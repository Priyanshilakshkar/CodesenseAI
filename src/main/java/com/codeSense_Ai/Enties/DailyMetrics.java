package com.codeSense_Ai.Enties;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "daily_metrics",
        indexes = {
                @Index(name = "idx_metric_date", columnList = "metric_date", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyMetrics {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The date this row represents — one row per calendar day
    @Column(name = "metric_date", nullable = false, unique = true)
    private LocalDate metricDate;

    // ── Review counts ─────────────────────────────────────────────
    @Column(name = "total_reviews")
    private int totalReviews;          // all ReviewSessions that day

    @Column(name = "completed_reviews")
    private int completedReviews;      // status = COMPLETED

    @Column(name = "failed_reviews")
    private int failedReviews;         // status = FAILED or RATE_LIMITED

    // ── Issue counts ──────────────────────────────────────────────
    @Column(name = "total_issues_found")
    private int totalIssuesFound;      // sum of comments_posted

    @Column(name = "security_issues")
    private int securityIssues;

    @Column(name = "performance_issues")
    private int performanceIssues;

    @Column(name = "bug_issues")
    private int bugIssues;

    @Column(name = "style_issues")
    private int styleIssues;

    @Column(name = "maintainability_issues")
    private int maintainabilityIssues;

    // ── AI usage ──────────────────────────────────────────────────
    @Column(name = "total_tokens_used")
    private long totalTokensUsed;

    @Column(name = "prompt_tokens")
    private long promptTokens;

    @Column(name = "completion_tokens")
    private long completionTokens;

    // ── Cost tracking ─────────────────────────────────────────────
    // Calculated at rollup time using current OpenAI pricing
    @Column(name = "ai_cost_usd", precision = 10, scale = 6)
    private BigDecimal aiCostUsd;

    // ── File stats ────────────────────────────────────────────────
    @Column(name = "files_reviewed")
    private int filesReviewed;

    @Column(name = "files_cached")
    private int filesCached;           // cache hits — money saved

    @Column(name = "cache_hit_rate")
    private double cacheHitRate;       // filesCached / (filesReviewed + filesCached)

    // ── Latency ───────────────────────────────────────────────────
    @Column(name = "avg_review_latency_ms")
    private long avgReviewLatencyMs;

    @Column(name = "p95_review_latency_ms")
    private long p95ReviewLatencyMs;

    // ── Repos ─────────────────────────────────────────────────────
    @Column(name = "active_repos")
    private int activeRepos;           // repos with at least one review that day

    @Column(name = "updated_at")
    private java.time.Instant updatedAt;

    @PrePersist @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.Instant.now();
    }
}
