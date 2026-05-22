package com.codeSense_Ai.Analytics;

import com.codeSense_Ai.Enties.DailyMetrics;
import com.codeSense_Ai.Repository.AiRequestLogRepository;
import com.codeSense_Ai.Repository.DailyMetricsRepository;
import com.codeSense_Ai.Repository.ReviewCommentRepository;
import com.codeSense_Ai.Repository.ReviewSessionRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class MetricsRollupScheduler {
    private final ReviewSessionRepository sessionRepository;
    private final ReviewCommentRepository commentRepository;
    private final AiRequestLogRepository aiLogRepository;
    private final DailyMetricsRepository metricsRepository;

    // GPT-4o pricing (per 1K tokens, as of 2025)
    // These are stored as constants — update when OpenAI changes pricing
    private static final double GPT4O_INPUT_COST_PER_1K  = 0.005;
    private static final double GPT4O_INPUT_COST_PER_1K_MINI = 0.00015;
    private static final double GPT4O_OUTPUT_COST_PER_1K = 0.015;

    /**
     * Main scheduled job — runs every hour at :00 minutes.
     * Rolls up yesterday and today (today is updated hourly as reviews come in).
     */
    @Scheduled(cron = "0 0 * * * *")  // every hour on the hour
    @Transactional
    public void rollupHourly() {
        log.info("Starting hourly metrics rollup");
        LocalDate today     = LocalDate.now(ZoneOffset.UTC);
        LocalDate yesterday = today.minusDays(1);

        rollupDay(today);
        rollupDay(yesterday); // re-roll yesterday in case any late data arrived

        log.info("Hourly metrics rollup complete");
    }

    /**
     * Startup backfill — rolls up the last 30 days on application start.
     * Ensures the dashboard has data even after a fresh deployment.
     * Runs 30 seconds after startup to let the app finish initializing.
     */
    @Scheduled(initialDelay = 30_000, fixedDelay = Long.MAX_VALUE)
    @Transactional
    public void backfillOnStartup() {
        log.info("Starting metrics backfill for last 30 days");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        for (int i = 0; i < 30; i++) {
            LocalDate day = today.minusDays(i);
            rollupDay(day);
        }

        log.info("Metrics backfill complete");
    }

    /**
     * Rolls up one specific day.
     * Queries all ReviewSessions for that day and upserts daily_metrics.
     */
    public void rollupDay(LocalDate date) {
        Instant dayStart = date.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant dayEnd   = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // ── Fetch raw session data ────────────────────────────────
        List<Object[]> sessionStats = sessionRepository
                .getStatsForDateRange(dayStart, dayEnd);

        if (sessionStats.isEmpty()) {
            log.debug("No sessions found for {}", date);
            return;
        }

        // ── Compute metrics ───────────────────────────────────────
        int totalReviews     = 0;
        int completedReviews = 0;
        int failedReviews    = 0;
        int filesReviewed    = 0;
        int filesCached      = 0;
        int totalComments    = 0;
        long totalLatencyMs  = 0;
        long totalTokens     = 0;
        long promptTokens    = 0;
        long completionTokens = 0;
        java.util.Set<Long> activeRepoIds = new java.util.HashSet<>();
        List<Long> latencies = new java.util.ArrayList<>();

        for (Object[] row : sessionStats) {
            // row: [status, filesReviewed, filesCached, commentsPosted,
            //       tokensUsed, latencyMs, repositoryId]
            String status      = (String)  row[0];
            int    files       = toInt(row[1]);
            int    cached      = toInt(row[2]);
            int    comments    = toInt(row[3]);
            long   tokens      = toLong(row[4]);
            long   latencyMs   = toLong(row[5]);
            Long   repoId      = (Long)    row[6];

            totalReviews++;
            if ("COMPLETED".equals(status)) completedReviews++;
            if ("FAILED".equals(status) || "RATE_LIMITED".equals(status)) failedReviews++;

            filesReviewed += files;
            filesCached   += cached;
            totalComments += comments;
            totalTokens   += tokens;
            totalLatencyMs += latencyMs;

            if (latencyMs > 0) latencies.add(latencyMs);
            if (repoId != null) activeRepoIds.add(repoId);
        }

        // ── Issue category breakdown ──────────────────────────────
        List<Object[]> categoryStats = commentRepository
                .getCategoryBreakdownForDateRange(dayStart, dayEnd);

        int securityIssues       = 0;
        int performanceIssues    = 0;
        int bugIssues            = 0;
        int styleIssues          = 0;
        int maintainabilityIssues = 0;

        for (Object[]  row : categoryStats) {
            String category = (String) row[0];
            int    count    = toInt(row[1]);
            switch (category) {
                case "SECURITY"        -> securityIssues       = count;
                case "PERFORMANCE"     -> performanceIssues    = count;
                case "BUG"             -> bugIssues            = count;
                case "STYLE"           -> styleIssues          = count;
                case "MAINTAINABILITY" -> maintainabilityIssues = count;
            }
        }

        // ── Token breakdown from AI request logs ──────────────────
        List<Object[]> tokenStats = aiLogRepository
                .getTokenBreakdownForDateRange(dayStart, dayEnd);

        for (Object[] row : tokenStats) {
            promptTokens     += toLong(row[0]);
            completionTokens += toLong(row[1]);
        }

        // ── Calculate cost ────────────────────────────────────────
        BigDecimal cost = calculateCost(promptTokens, completionTokens);

        // ── Calculate latency percentiles ─────────────────────────
        latencies.sort(Long::compareTo);
        long avgLatency = latencies.isEmpty() ? 0 :
                (long) latencies.stream().mapToLong(Long::longValue).average().orElse(0);
        long p95Latency = latencies.isEmpty() ? 0 :
                latencies.get((int) Math.floor(latencies.size() * 0.95));

        // ── Cache hit rate ────────────────────────────────────────
        int totalFiles = filesReviewed + filesCached;
        double cacheHitRate = totalFiles > 0
                ? (double) filesCached / totalFiles : 0.0;

        // ── Upsert into daily_metrics ─────────────────────────────
        DailyMetrics metrics = metricsRepository
                .findByMetricDate(date)
                .orElse(DailyMetrics.builder().metricDate(date).build());

        metrics.setTotalReviews(totalReviews);
        metrics.setCompletedReviews(completedReviews);
        metrics.setFailedReviews(failedReviews);
        metrics.setTotalIssuesFound(totalComments);
        metrics.setSecurityIssues(securityIssues);
        metrics.setPerformanceIssues(performanceIssues);
        metrics.setBugIssues(bugIssues);
        metrics.setStyleIssues(styleIssues);
        metrics.setMaintainabilityIssues(maintainabilityIssues);
        metrics.setTotalTokensUsed(totalTokens);
        metrics.setPromptTokens(promptTokens);
        metrics.setCompletionTokens(completionTokens);
        metrics.setAiCostUsd(cost);
        metrics.setFilesReviewed(filesReviewed);
        metrics.setFilesCached(filesCached);
        metrics.setCacheHitRate(cacheHitRate);
        metrics.setAvgReviewLatencyMs(avgLatency);
        metrics.setP95ReviewLatencyMs(p95Latency);
        metrics.setActiveRepos(activeRepoIds.size());

        metricsRepository.save(metrics);
        log.debug("Rolled up metrics for {}: {} reviews, {} issues, ${} cost",
                date, totalReviews, totalComments, cost);
    }

    // ── Cost calculation ──────────────────────────────────────────────────────

    private BigDecimal calculateCost(long promptTokens, long completionTokens) {
        // Simplified: assume all tokens are gpt-4o
        // In production, use model-specific rates from AiRequestLog.modelUsed
        double inputCost  = (promptTokens     / 1000.0) * GPT4O_INPUT_COST_PER_1K;
        double outputCost = (completionTokens / 1000.0) * GPT4O_OUTPUT_COST_PER_1K;
        return BigDecimal.valueOf(inputCost + outputCost)
                .setScale(6, java.math.RoundingMode.HALF_UP);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private int  toInt(Object o)  { return o == null ? 0 : ((Number) o).intValue(); }
    private long toLong(Object o) { return o == null ? 0 : ((Number) o).longValue(); }
}
