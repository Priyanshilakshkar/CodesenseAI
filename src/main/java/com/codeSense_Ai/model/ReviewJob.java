package com.codeSense_Ai.model;

import lombok.Builder;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
public class ReviewJob {
    @Builder.Default
    private final String jobId = UUID.randomUUID().toString();

    private final String webhookDeliveryId;

    private final Long repositoryId;

    private final String owner;

    private final String repoName;

    private final Long installationId;

    private final int prNumber;

    private final String headSha;

    private final String prAuthor;

    private final String prTitle;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    private final Instant processAfter;

    @Builder.Default
    private final int attemptNumber = 1;

    public boolean isFirstAttempt()   { return attemptNumber == 1; }
    public boolean isDelayed()        { return processAfter != null
            && processAfter.isAfter(Instant.now()); }
    public boolean hasExceededRetries(int maxRetries) {
        return attemptNumber > maxRetries;
    }

    /** Creates a retry copy of this job with incremented attempt number */
    public ReviewJob asRetry(Instant retryAfter) {
        return ReviewJob.builder()
                .jobId(this.jobId)              // same job ID — for tracking
                .webhookDeliveryId(this.webhookDeliveryId)
                .repositoryId(this.repositoryId)
                .owner(this.owner)
                .repoName(this.repoName)
                .installationId(this.installationId)
                .prNumber(this.prNumber)
                .headSha(this.headSha)
                .prAuthor(this.prAuthor)
                .prTitle(this.prTitle)
                .createdAt(this.createdAt)      // original creation time preserved
                .attemptNumber(this.attemptNumber + 1)
                .processAfter(retryAfter)
                .build();
    }

    @Override
    public String toString() {
        return String.format("ReviewJob{id=%s, repo=%s/%s, pr=#%d, attempt=%d}",
                jobId.substring(0, 8), owner, repoName, prNumber, attemptNumber);
    }
}
