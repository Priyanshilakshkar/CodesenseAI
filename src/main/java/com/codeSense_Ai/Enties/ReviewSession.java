package com.codeSense_Ai.Enties;

import com.codeSense_Ai.Enums.ReviewSessionStatus;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequests pullRequest;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id", nullable = false)
    private Repositories repository;

    @Column(nullable = false, length = 40)
    private String triggerSha;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewSessionStatus status = ReviewSessionStatus.QUEUED;

    private Integer totalChunks = 0;

    private Integer processedChunks = 0;

    private Integer totalIssuesFound = 0;

    private Integer securityIssues = 0;

    private Integer performanceIssues = 0;

    private Integer bugIssues = 0;

    private Integer CommentsPosted;

    private Integer styleIssues = 0;

    private Integer aiTokensUsed = 0;

    private String aiModelUsed;

    private Long processingTimeMs;

    @Lob
    private String errorMessage;
    private String jobId;
    private Long githubReviewId;

    private Integer attemptNumber;

    private Integer filesReviewed;

    private Integer filesCached;

    private Integer filesSkipped;

    private Integer totalAdditions = 0;

    private Integer totalRemovals = 0;

    private Integer totalHunks = 0;

    private String skipReason;

    private Integer duplicatesRemoved;

    private Instant startedAt;

    private Instant completedAt;

    private Instant createdAt;

    @OneToMany(mappedBy = "reviewSession", fetch = FetchType.LAZY)
    private List<ReviewComment> comments;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }

}
