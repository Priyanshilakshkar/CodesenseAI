package com.codeSense_Ai.Controller;

import com.codeSense_Ai.Dto.GithubAppProperties;
import com.codeSense_Ai.Dto.WebhookPayload;
import com.codeSense_Ai.Exceptions.RetryableException;
import com.codeSense_Ai.Github.webhook.WebhookPayloadProtector;
import com.codeSense_Ai.Github.webhook.WebhookSignatureValidator;
import com.codeSense_Ai.Queue.ReviewJobPublisher;
import com.codeSense_Ai.Repository.RepositoriesRepository;
import com.codeSense_Ai.Service.InstallationRepositorySyncService;
import com.codeSense_Ai.Service.WebhookEventService;
import com.codeSense_Ai.model.ReviewJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

@RestController
@Slf4j
@RequiredArgsConstructor
public class WebHookController {
    private final WebhookSignatureValidator signatureValidator;
    private final WebhookPayloadProtector replayProtector;
    private final WebhookEventService eventService;
    private final ReviewJobPublisher jobPublisher;
    private final RepositoriesRepository repoRepository;
    private final ObjectMapper mapper;
    private final InstallationRepositorySyncService installationRepositorySyncService;
    private final GithubAppProperties githubAppProperties;

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event")       String eventType,
            @RequestHeader("X-GitHub-Delivery")    String deliveryId,
            @RequestBody String rawPayload) {





        String secret = githubAppProperties.getWebhookSecret();


        log.info("Api has reached");

        // ── Validate signature ─────────────────────────────────────
        if (!signatureValidator.validate(rawPayload, signature , secret)) {
            log.warn("Invalid webhook signature for delivery {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        WebhookPayload payload = parsePayload(rawPayload);

        log.info("Webhook signature validation SUCCESS!");
        log.info("GitHub event type: {}", eventType);
        log.info("GitHub delivery ID: {}", deliveryId);
        log.info("Webhook payload parsed successfully");


        if (replayProtector.payloadProtector(deliveryId)) {
            log.info("Duplicate delivery {} — ignoring", deliveryId);
            return ResponseEntity.accepted().build(); // still 202, not an error
        }




        if ("installation_repositories".equals(eventType)) {
            if (payload.installation() == null
                    || payload.installation().id() == null) {
                return ResponseEntity.badRequest().build();
            }

            Long installationId = payload.installation().id();

            installationRepositorySyncService
                    .syncInstallationRepositoryChanges(
                            installationId,
                            payload.repositoriesAdded(),
                            payload.repositoriesRemoved()
                    );

            installationRepositorySyncService
                    .syncInstallationRepositories(installationId);

            return ResponseEntity.accepted().build();
        }



        if ("installation".equals(eventType)) {
            if (payload.installation() == null
                    || payload.installation().id() == null) {
                return ResponseEntity.badRequest().build();
            }

            Long installationId = payload.installation().id();

            log.info(
                    "Installation event received: action={}, installationId={}",
                    payload.action(),
                    installationId
            );

            if ("created".equals(payload.action())
                    || "unsuspend".equals(payload.action())) {

                log.info("Starting repository sync for installation {}",
                        installationId);

                installationRepositorySyncService
                        .syncInstallationRepositories(installationId);

                log.info("Repository sync completed for installation {}",
                        installationId);
            }else if ("deleted".equals(payload.action())) {

                installationRepositorySyncService
                        .deactivateInstallationRepositories(installationId);
            }

            return ResponseEntity.accepted().build();
        }


        log.info("Webhook signature validation SUCCESS!");

        // ── Replay protection ──────────────────────────────────────


        // ── Only process PR events ─────────────────────────────────
        if (!"pull_request".equals(eventType)) {
            log.debug("Ignoring non-PR event: {}", eventType);
            return ResponseEntity.accepted().build();
        }
        if (payload.repository() == null
                || payload.repository().owner() == null
                || payload.repository().owner().login() == null
                || payload.repository().name() == null) {
            log.warn("Webhook payload missing repository information");
            return ResponseEntity.badRequest().build();
        }

        final String owner = payload.repository().owner().login();
        final String repoName = payload.repository().name();

        // ── Parse payload ──────────────────────────────────────────


        // Only process when a PR is opened or synchronized (new commits pushed)
        String action = payload.action();
        if (!"opened".equals(action) && !"synchronize".equals(action)) {
            log.debug("Ignoring PR action: {}", action);
            return ResponseEntity.accepted().build();
        }

        // ── Persist raw event ──────────────────────────────────────
        eventService.saveWebhook(deliveryId, eventType, rawPayload);

        eventService.markAsProcessed(deliveryId);

        // ── Find registered repository ─────────────────────────────
        Long installationId = payload.installation().id();
        log.info("Looking up repository: owner='{}', name='{}', installationId={}",
                owner, repoName, installationId);

        log.info("Repositories in DB: {}",
                repoRepository.findAll().stream()
                        .map(r -> r.getOwner() + "/" + r.getName()
                                + " (installation=" + r.getInstallation_id()
                                + ", active=" + r.isActive() + ")")
                        .toList());
        repoRepository.findByOwnerAndName(owner, repoName).ifPresentOrElse(
                repo -> {
                    // ── Publish review job ─────────────────────────────

                    if (installationId == null
                            || !installationId.equals(repo.getInstallation_id())) {
                        log.warn("Installation mismatch for {}/{}", owner, repoName);
                        return;
                    }
                    ReviewJob job = ReviewJob.builder()
                            .webhookDeliveryId(deliveryId)
                            .repositoryId(repo.getGithubRepoId())
                            .owner(owner)
                            .repoName(repoName)
                            .installationId(repo.getInstallation_id())
                            .prNumber(payload.pullRequest().number())
                            .headSha(payload.pullRequest().head().sha())
                            .prAuthor(payload.pullRequest().user().login())
                            .prTitle(payload.pullRequest().title())
                            .baseSha(payload.pullRequest().base().sha())
                            .baseBranch(payload.pullRequest().base().ref())
                            .headBranch(payload.pullRequest().head().ref())
                            .githubPrId(payload.pullRequest().id())

                            .build();

                    jobPublisher.publish(job);
                    log.info("Published review job for {}/{} PR #{}",
                            owner, repoName, payload.pullRequest().number());
                },
                () -> log.info("Repo {}/{} not registered — ignoring webhook", owner, repoName)
        );

        // ── Return 202 immediately ─────────────────────────────────
        // Must be < 2s total or GitHub considers the delivery failed
        return ResponseEntity.accepted().build();
    }

    private WebhookPayload parsePayload(String raw) {
        try {
            return mapper.readValue(raw, WebhookPayload.class);
        } catch (Exception e) {
            throw new RetryableException("Failed to parse webhook payload", e);
        }
    }
}
