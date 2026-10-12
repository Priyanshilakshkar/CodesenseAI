package com.codeSense_Ai.Service;

import com.codeSense_Ai.Auth.GithubAppAuthenticator;
import com.codeSense_Ai.Diff.UnifiedDiffParser;
import com.codeSense_Ai.Dto.DiffFetchResult;
import com.codeSense_Ai.Dto.GithubFile;
import com.codeSense_Ai.Dto.PostedReview;
import com.codeSense_Ai.Enties.AIRequestLog;
import com.codeSense_Ai.Enties.PullRequests;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Enties.ReviewSession;
import com.codeSense_Ai.Enums.AIRequestStatus;
import com.codeSense_Ai.Enums.ReviewSessionStatus;
import com.codeSense_Ai.Enums.States;
import com.codeSense_Ai.Exceptions.RateLimitExceededException;
import com.codeSense_Ai.Exceptions.RateLimitRetryException;
import com.codeSense_Ai.Exceptions.RetryableException;
import com.codeSense_Ai.OpenAi.AiResponseEngine;
import com.codeSense_Ai.Repository.AiRequestLogRepository;
import com.codeSense_Ai.Repository.PullRequestRepository;
import com.codeSense_Ai.Repository.RepositoriesRepository;
import com.codeSense_Ai.Repository.ReviewSessionRepository;
import com.codeSense_Ai.Reviews.ReviewPostingService;
import com.codeSense_Ai.Rule.RuleLoader;
import com.codeSense_Ai.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReviewOrchestrationService {

    private final GithubAppAuthenticator githubAppAuthenticator;
    private final GitHubFetchDiffService diffFetchService;

    // ── Week 4 ────────────────────────────────────────────────────
    private final UnifiedDiffParser diffParserEngine;

    // ── Week 5 ────────────────────────────────────────────────────
    private final ChunkingEngine chunkingEngine;

    // ── Week 6 ────────────────────────────────────────────────────
    private final AiResponseEngine aiReviewEngine;

    // ── Week 7 ────────────────────────────────────────────────────
    private final ReviewPostingService reviewPostingService;

    // ── Week 9 ────────────────────────────────────────────────────
    private final RuleLoader ruleLoader;

    // ── Persistence ───────────────────────────────────────────────
    private final RepositoriesRepository repoRepository;
    private final PullRequestRepository prRepository;
    private final ReviewSessionRepository sessionRepository;
    private final AiRequestLogRepository aiRequestLogRepository;

    private static final int MAX_TOKENS_PER_PR = 50_000;

    /**
     * Processes one ReviewJob end-to-end.
     *
     * Stages:
     *   1. Load repository + PR entities
     *   2. Create ReviewSession (status = PROCESSING)
     *   3. Load repo config (.codesense.yml or DB or defaults)
     *   4. Fetch + filter PR diff files from GitHub
     *   5. Parse each file's diff into DiffFile objects
     *   6. Chunk each DiffFile into CodeChunks
     *   7. Send each CodeChunk to AIReviewEngine
     *   8. Post review to GitHub via ReviewPostingService
     *   9. Update ReviewSession (status = COMPLETED)
     *
     * On any error: ReviewSession marked FAILED, exception rethrown
     * so ReviewJobConsumer can decide whether to retry.
     */
    public void processReview(ReviewJob job) {
        log.info("Processing review: repo={}/{} pr=#{}",
                job.getOwner(), job.getRepoName(), job.getPrNumber());

        // ── Stage 1: Load entities ────────────────────────────────
        Repositories repository = loadRepository(job);
        PullRequests pullRequest = loadOrCreatePullRequest(job, repository);

        // ── Stage 2: Create ReviewSession ─────────────────────────
        ReviewSession session = createSession(job, pullRequest);

        try {
            // ── Stage 3: Load repo config ─────────────────────────

            String token = githubAppAuthenticator.generateIntallationToken(
                    job.getInstallationId()
            );


            RepoReviewConfig config = ruleLoader.load(repository, token);
            log.info("Loaded config for {}/{}: maxComments={} model={}",
                    job.getOwner(), job.getRepoName(),
                    config.getMaxComments(), config.getModel());

            // ── Stage 4: Fetch diff from GitHub ───────────────────
            DiffFetchResult diffResult = fetchDiff(job, repository, session);

            if (diffResult.isFullyCached()) {
                log.info("All files cached — skipping AI review for PR #{}",
                        job.getPrNumber());
                markCompleted(session, 0, 0, "all-cached", config.getModel());
                return;
            }

            // ── Stage 5: Parse diffs ──────────────────────────────
            List<DiffFile> diffFiles = parseDiffs(diffResult, session);

            // ── Stage 6 + 7: Chunk + AI review ───────────────────
            Map<CodeChunk, List<ReviewIssue>> chunkResults =
                    chunkAndReview(diffFiles, config, session);

            // ── Stage 8: Post review to GitHub ────────────────────
            PostedReview posted = reviewPostingService.post(
                    chunkResults,
                    diffFiles,
                    repository,
                    job.getPrNumber(),
                    config,
                    session.getAiModelUsed(),
                    session.getAiTokensUsed()
            );

            // ── Stage 9: Mark completed ───────────────────────────
            markCompleted(session, posted, diffResult);
            log.info("Review completed: pr=#{} reviewId={} comments={}",
                    job.getPrNumber(), posted.githubReviewId(), posted.commentsPosted());

        } catch (RateLimitExceededException e) {
            // GitHub rate limited — re-queue after reset
            markFailed(session, "RATE_LIMITED", e.getMessage());
            throw new RateLimitRetryException(e.getMessage(),
                    e.getRetryAfterSeconds());

        } catch (RetryableException e) {
            markFailed(session, "TRANSIENT_ERROR", e.getMessage());
            throw e;

        } catch (Exception e) {
            log.error("Review failed for PR #{}: {}", job.getPrNumber(), e.getMessage(), e);
            markFailed(session, "FAILED", e.getMessage());
            throw new RetryableException("Review processing failed: " + e.getMessage(), e);
        }
    }

    // ── Stage implementations ─────────────────────────────────────────────────

    private Repositories loadRepository(ReviewJob job) {
        return repoRepository.findByGithubRepoId(job.getRepositoryId())
                .orElseThrow(() -> new IllegalStateException(
                        "Repository not found: id=" + job.getRepositoryId()
                ));
    }

    private PullRequests loadOrCreatePullRequest(ReviewJob job, Repositories repository) {
        return prRepository
                .findByRepositoryIdAndPrNumber(
                        repository.getId(), job.getPrNumber())
                .orElseGet(() -> {
                    // First time we've seen this PR — create the entity
                    PullRequests pr = new PullRequests();
                    pr.setRepository(repository);
                    pr.setPrNumber(job.getPrNumber());
                    pr.setTitle(job.getPrTitle());
                    pr.setAuthorUserName(job.getPrAuthor());
                    pr.setHeadSha(job.getHeadSha());
                    pr.setStates(States.OPEN);
                    pr.setCreatedAt(Instant.now());
                    pr.setBaseBranch(job.getBaseBranch());
                    pr.setBaseSha(job.getBaseSha());
                    pr.setHeadBranch(job.getHeadBranch());
                    pr.setGithubPrId(job.getGithubPrId());
                    return prRepository.save(pr);
                });
    }

    private ReviewSession createSession(ReviewJob job, PullRequests pullRequest) {
        ReviewSession session = new ReviewSession();
        session.setPullRequest(pullRequest);
        session.setStatus(ReviewSessionStatus.PROCESSING);
        session.setJobId(job.getJobId());
        session.setAttemptNumber(job.getAttemptNumber());
        session.setCreatedAt(Instant.now());
        session.setRepository(pullRequest.getRepository());
        session.setTriggerSha(job.getHeadSha());
        ReviewSession saved = sessionRepository.save(session);
        log.info("Created ReviewSession id={} for PR #{}",
                saved.getId(), job.getPrNumber());
        return saved;
    }

    private DiffFetchResult fetchDiff(ReviewJob job,
                                      Repositories repository,
                                      ReviewSession session) {
        try {
            DiffFetchResult result = diffFetchService.fetchDiff(
                    repository, job.getPrNumber()
            );
            // Update session with file counts
            session.setFilesReviewed(result.reviewableFiles().size());
            session.setFilesCached(result.cachedFiles().size());
            session.setFilesSkipped(result.skippedFiles().size());
            sessionRepository.save(session);
            return result;
        } catch (RateLimitExceededException e) {
            throw e; // propagate — consumer handles retry
        } catch (Exception e) {
            throw new RetryableException("Failed to fetch diff: " + e.getMessage(), e);
        }
    }

    private List<DiffFile> parseDiffs(DiffFetchResult diffResult,
                                      ReviewSession session) {
        List<DiffFile> diffFiles = new ArrayList<>();
        int totalAdditions = 0;
        int totalRemovals  = 0;
        int totalHunks     = 0;

        for (GithubFile file : diffResult.reviewableFiles()) {
            try {
                DiffFile diffFile = diffParserEngine.diffParser(
                        file.patch(),
                        file.filename()
                );
                log.info(
                        "Parsed DiffFile: filename={}, oldPath={}, newPath={}, extension={}",
                        file.filename(),
                        diffFile.getOldPath(),
                        diffFile.getNewPath(),
                        diffFile.getExtension()
                );

                diffFiles.add(diffFile);
                totalAdditions += diffFile.totalAdditions();
                totalRemovals  += diffFile.totalRemovals();
                totalHunks     += diffFile.getHunksList().size();

            } catch (Exception e) {
                // Skip unparseable files — log and continue
                log.warn("Failed to parse diff for '{}': {}",
                        file.filename(), e.getMessage());
            }
        }

        // Update session with diff stats
        session.setTotalAdditions(totalAdditions);
        session.setTotalRemovals(totalRemovals);
        session.setTotalHunks(totalHunks);
        sessionRepository.save(session);

        log.info("Parsed {} diff files: +{} -{} in {} hunks",
                diffFiles.size(), totalAdditions, totalRemovals, totalHunks);
        return diffFiles;
    }

    private Map<CodeChunk, List<ReviewIssue>> chunkAndReview(
            List<DiffFile> diffFiles,
            RepoReviewConfig config,
            ReviewSession session) {

        Map<CodeChunk, List<ReviewIssue>> results = new LinkedHashMap<>();
        int totalTokensUsed = 0;
        String lastModelUsed = config.getModel();

        for (DiffFile diffFile : diffFiles) {
            // ── Chunk the file ────────────────────────────────────
            List<CodeChunk> chunks = chunkingEngine.chunk(diffFile);


            for (CodeChunk chunk : chunks) {
                // ── Check token budget ────────────────────────────
                if (totalTokensUsed + chunk.getEstimatedTokens() > MAX_TOKENS_PER_PR) {
                    log.warn("Token budget reached ({}/{}) — skipping remaining chunks",
                            totalTokensUsed, MAX_TOKENS_PER_PR);
                    break;
                }

                // ── Send to AI ────────────────────────────────────
                try {
                    List<ReviewIssue> issues =
                            aiReviewEngine.review(chunk, config);

                    results.put(chunk, issues);

                    // Track token usage
                    int chunkTokens = chunk.getEstimatedTokens();
                    totalTokensUsed += chunkTokens;
                    lastModelUsed    = config.getModel();

                    // Persist AI request log for cost analytics
                    persistAiRequestLog(session, chunk, chunkTokens, lastModelUsed);

                } catch (Exception e) {
                    // AI failure on one chunk — skip it, continue with others
                    log.error("AI review failed for chunk {}: {}",
                            chunk.summary(), e.getMessage());
                    results.put(chunk, List.of()); // empty = no findings
                }
            }
        }

        // Update session with token totals
        session.setAiTokensUsed(totalTokensUsed);
        session.setAiModelUsed(lastModelUsed);
        sessionRepository.save(session);

        log.info("AI review complete: {} chunks processed, {} tokens used, model={}",
                results.size(), totalTokensUsed, lastModelUsed);

        return results;
    }

    // ── Session lifecycle ─────────────────────────────────────────────────────

    private void markCompleted(ReviewSession session,
                               PostedReview posted,
                               DiffFetchResult diffResult) {
        session.setStatus(ReviewSessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setGithubReviewId(posted.githubReviewId());
        session.setCommentsPosted(posted.commentsPosted());
        session.setDuplicatesRemoved(posted.duplicatesRemoved());
        sessionRepository.save(session);
    }

    private void markCompleted(ReviewSession session,
                               int commentsPosted,
                               int tokensUsed,
                               String reason,
                               String model) {
        session.setStatus(ReviewSessionStatus.COMPLETED);
        session.setCompletedAt(Instant.now());
        session.setCommentsPosted(commentsPosted);
        session.setAiTokensUsed(tokensUsed);
        session.setAiModelUsed(model);
        session.setSkipReason(reason);
        sessionRepository.save(session);
    }

    private void markFailed(ReviewSession session, String status, String errorMessage) {
        session.setStatus(ReviewSessionStatus.FAILED);
        session.setCompletedAt(Instant.now());
        session.setErrorMessage(
                errorMessage != null && errorMessage.length() > 500
                        ? errorMessage.substring(0, 500)
                        : errorMessage
        );
        sessionRepository.save(session);
    }

    private void persistAiRequestLog(ReviewSession session,
                                     CodeChunk chunk,
                                     int tokensUsed,
                                     String model) {
        AIRequestLog log = new AIRequestLog();
        log.setReviewSession(session);
        log.setFilePath(chunk.getFilePath());
        log.setChunkIndex(chunk.getChunkIndex());
        log.setTotalTokens(tokensUsed);
        log.setModel(model);
        log.setCreatedAt(Instant.now());
        log.setStatus(AIRequestStatus.SUCCESS);
        aiRequestLogRepository.save(log);
    }
}
