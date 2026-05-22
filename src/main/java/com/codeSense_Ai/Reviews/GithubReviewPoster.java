package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.Auth.GithubAppAuthenticator;
import com.codeSense_Ai.Dto.GitHubReview;
import com.codeSense_Ai.Dto.GitHubReviewComment;
import com.codeSense_Ai.Dto.GitHubReviewResponse;
import com.codeSense_Ai.Enties.Repositories;
import com.codeSense_Ai.Exceptions.GitHubReviewException;
import com.codeSense_Ai.Service.RateLimitTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

@Service
@Component
@Slf4j
@RequiredArgsConstructor
public class GithubReviewPoster {
    private final RestClient restClient;
    private final GithubAppAuthenticator authenticator;
    private final RateLimitTracker rateLimitTracker;


    public long postReview(Repositories repository,
                           int              prNumber,
                           String           summaryBody,
                           List<GitHubReviewComment> comments) {
        String owner          = repository.getOwner();
        String repoName       = repository.getName();
        Long   installationId = repository.getInstallation_id();

        log.info("Posting review to {}/{} PR #{}: {} inline comments",
                owner, repoName, prNumber, comments.size());

        // Get a valid installation token
        String token = authenticator.generateIntallationToken(installationId);

        // Build the review payload
        // state = "COMMENT" → informational only, never blocks merging
        GitHubReview review = GitHubReview.asComment(summaryBody, comments);

        try {
            GitHubReviewResponse response = restClient.post()
                    .uri("/repos/{owner}/{repo}/pulls/{pr}/reviews",
                            owner, repoName, prNumber)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .header(HttpHeaders.CONTENT_TYPE,
                            MediaType.APPLICATION_JSON_VALUE)
                    .header("Accept", "application/vnd.github+json")
                    .header("X-GitHub-Api-Version", "2022-11-28")
                    .body(review)
                    .exchange((request, resp) -> {
                        // Update rate limit tracker from response headers
                        rateLimitTracker.updateFromReponses(owner, repoName, resp.getHeaders());
                        return resp.bodyTo(GitHubReviewResponse.class);
                    });

            if (response == null || response.id() == null) {
                throw new GitHubReviewException("GitHub returned null review response");
            }

            log.info("Review posted successfully: reviewId={} for {}/{} PR #{}",
                    response.id(), owner, repoName, prNumber);

            return response.id();

        } catch (HttpClientErrorException.UnprocessableEntity e) {
            // 422 — usually means a diff position is invalid
            // This happens if our position mapping has a bug
            log.error("GitHub rejected review (422) for {}/{} PR #{}: {}",
                    owner, repoName, prNumber, e.getResponseBodyAsString());

            // Retry with no inline comments — just post the summary
            return postSummaryOnly(owner, repoName, prNumber, token, summaryBody);

        } catch (HttpClientErrorException.Unauthorized e) {
            // 401 — token expired, evict and retry once
            log.warn("GitHub 401 posting review — evicting token and retrying");
            authenticator.evictToken(installationId);
            String freshToken = authenticator.generateIntallationToken(installationId);

            return retryPost(owner, repoName, prNumber, freshToken, review);

        } catch (Exception e) {
            log.error("Failed to post review to {}/{} PR #{}: {}",
                    owner, repoName, prNumber, e.getMessage());
            throw new GitHubReviewException(
                    "Failed to post review: " + e.getMessage()
            );
        }
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Fallback: post only the summary with no inline comments.
     * Used when GitHub rejects the full review due to invalid positions.
     */
    private long postSummaryOnly(String owner, String repoName,
                                 int prNumber, String token, String body) {
        log.warn("Falling back to summary-only review for {}/{} PR #{}",
                owner, repoName, prNumber);

        GitHubReview summaryOnly = GitHubReview.asComment(
                body + "\n\n> ⚠️ *Inline comments could not be posted due to " +
                        "a diff position mapping error. Please check CodeSense logs.*",
                List.of() // empty comments
        );

        GitHubReviewResponse response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{pr}/reviews",
                        owner, repoName, prNumber)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .body(summaryOnly)
                .retrieve()
                .body(GitHubReviewResponse.class);

        return response != null && response.id() != null ? response.id() : -1L;
    }

    private long retryPost(String owner, String repoName,
                           int prNumber, String token, GitHubReview review) {
        GitHubReviewResponse response = restClient.post()
                .uri("/repos/{owner}/{repo}/pulls/{pr}/reviews",
                        owner, repoName, prNumber)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .body(review)
                .retrieve()
                .body(GitHubReviewResponse.class);

        return response != null && response.id() != null ? response.id() : -1L;
    }

}
