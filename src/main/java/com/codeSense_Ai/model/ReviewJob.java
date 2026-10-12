package com.codeSense_Ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.jackson.Jacksonized;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Getter
@Builder
@Jacksonized
@NoArgsConstructor
@AllArgsConstructor
public class ReviewJob implements Serializable {
    @Builder.Default
    private  String jobId = UUID.randomUUID().toString();

    private  String webhookDeliveryId;

    private  Long repositoryId;

    private  String owner;

    private  String repoName;

    private  Long installationId;

    private  int prNumber;

    private Long githubPrId;

    private  String headSha;

    private  String prAuthor;

    private  String prTitle;

    private  String baseBranch;
    private  String headBranch;

    private  String baseSha;

    @Builder.Default
    private final Instant createdAt = Instant.now();

    private  Instant processAfter;

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
                .githubPrId(this.githubPrId)
                .baseBranch(this.baseBranch)
                .headBranch(this.headBranch)
                .baseSha(this.baseSha)
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
