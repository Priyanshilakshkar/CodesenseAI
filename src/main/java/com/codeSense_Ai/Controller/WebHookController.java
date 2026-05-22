package com.codeSense_Ai.Controller;

import com.codeSense_Ai.Dto.WebhookPayload;
import com.codeSense_Ai.Exceptions.RetryableException;
import com.codeSense_Ai.Github.webhook.WebhookPayloadProtector;
import com.codeSense_Ai.Github.webhook.WebhookSignatureValidator;
import com.codeSense_Ai.Queue.ReviewJobPublisher;
import com.codeSense_Ai.Repository.RepositoriesRepository;
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

    @PostMapping("/github")
    public ResponseEntity<Void> handleGitHubWebhook(
            @RequestHeader("X-Hub-Signature-256") String signature,
            @RequestHeader("X-GitHub-Event")       String eventType,
            @RequestHeader("X-GitHub-Delivery")    String deliveryId,
            @RequestBody String rawPayload) {

        WebhookPayload payload = parsePayload(rawPayload);
        String owner    = payload.repository().owner().login();
        String repoName = payload.repository().name();

        String secret = eventService.getWebhookSecret(repoName);

        // ── Validate signature ─────────────────────────────────────
        if (!signatureValidator.validate(rawPayload, signature , secret)) {
            log.warn("Invalid webhook signature for delivery {}", deliveryId);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        // ── Replay protection ──────────────────────────────────────
        if (replayProtector.payloadProtector(deliveryId)) {
            log.info("Duplicate delivery {} — ignoring", deliveryId);
            return ResponseEntity.accepted().build(); // still 202, not an error
        }
        eventService.markAsProcessed(deliveryId);

        // ── Only process PR events ─────────────────────────────────
        if (!"pull_request".equals(eventType)) {
            log.debug("Ignoring non-PR event: {}", eventType);
            return ResponseEntity.accepted().build();
        }

        // ── Parse payload ──────────────────────────────────────────


        // Only process when a PR is opened or synchronized (new commits pushed)
        String action = payload.action();
        if (!"opened".equals(action) && !"synchronize".equals(action)) {
            log.debug("Ignoring PR action: {}", action);
            return ResponseEntity.accepted().build();
        }

        // ── Persist raw event ──────────────────────────────────────
        eventService.saveWebhook(deliveryId, eventType, rawPayload);

        // ── Find registered repository ─────────────────────────────


        repoRepository.findByOwnerAndName(owner, repoName).ifPresentOrElse(
                repo -> {
                    // ── Publish review job ─────────────────────────────
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
