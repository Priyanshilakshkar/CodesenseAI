package com.codeSense_Ai.Analytics;

import com.codeSense_Ai.Enties.DailyMetrics;
import com.codeSense_Ai.Enties.ReviewSession;
import com.codeSense_Ai.Repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AnalyticsService {
    private final DailyMetricsRepository metricsRepository;
    private final ReviewSessionRepository sessionRepository;
    private final ReviewCommentRepository commentRepository;
    private final AiRequestLogRepository aiLogRepository;
    private final RepositoriesRepository repoRepository;

    // ── Overview stats (top of dashboard) ────────────────────────────────────

    /**
     * Key numbers shown in the 4 stat cards at the top of the dashboard.
     * Reads from daily_metrics — extremely fast (30 rows max).
     */
    public OverviewStats getOverviewStats(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        List<DailyMetrics> metrics = metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC));

        int    totalReviews    = metrics.stream().mapToInt(DailyMetrics::getTotalReviews).sum();
        int    totalIssues     = metrics.stream().mapToInt(DailyMetrics::getTotalIssuesFound).sum();
        long   totalTokens     = metrics.stream().mapToLong(DailyMetrics::getTotalTokensUsed).sum();
        BigDecimal totalCost   = metrics.stream()
                .map(DailyMetrics::getAiCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        double avgCacheHitRate = metrics.stream()
                .mapToDouble(DailyMetrics::getCacheHitRate)
                .average().orElse(0.0);

        return new OverviewStats(
                totalReviews,
                totalIssues,
                totalTokens,
                totalCost,
                avgCacheHitRate,
                days
        );
    }

    /**
     * Reviews per day for the last N days — powers the line chart.
     * Returns a map of date → count, sorted chronologically.
     */
    public Map<LocalDate, Integer> getReviewsPerDay(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        List<DailyMetrics> metrics = metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC));

        // Fill in zero values for days with no reviews
        Map<LocalDate, Integer> result = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            result.put(LocalDate.now(ZoneOffset.UTC).minusDays(i), 0);
        }

        metrics.forEach(m -> result.put(m.getMetricDate(), m.getTotalReviews()));
        return result;
    }

    /**
     * Issue counts by category — powers the pie/donut chart.
     */
    public Map<String, Integer> getIssuesByCategory(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        List<DailyMetrics> metrics = metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC));

        Map<String, Integer> breakdown = new LinkedHashMap<>();
        breakdown.put("Security",        metrics.stream().mapToInt(DailyMetrics::getSecurityIssues).sum());
        breakdown.put("Bug",             metrics.stream().mapToInt(DailyMetrics::getBugIssues).sum());
        breakdown.put("Performance",     metrics.stream().mapToInt(DailyMetrics::getPerformanceIssues).sum());
        breakdown.put("Maintainability", metrics.stream().mapToInt(DailyMetrics::getMaintainabilityIssues).sum());
        breakdown.put("Style",           metrics.stream().mapToInt(DailyMetrics::getStyleIssues).sum());

        // Remove zero-count categories
        breakdown.entrySet().removeIf(e -> e.getValue() == 0);
        return breakdown;
    }

    /**
     * Token usage per day — powers the area chart.
     */
    public Map<LocalDate, Long> getTokenUsagePerDay(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        List<DailyMetrics> metrics = metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC));

        Map<LocalDate, Long> result = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            result.put(LocalDate.now(ZoneOffset.UTC).minusDays(i), 0L);
        }
        metrics.forEach(m -> result.put(m.getMetricDate(), m.getTotalTokensUsed()));
        return result;
    }

    /**
     * AI cost per day — powers the cost trend chart.
     */
    public Map<LocalDate, BigDecimal> getCostPerDay(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        List<DailyMetrics> metrics = metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC));

        Map<LocalDate, BigDecimal> result = new LinkedHashMap<>();
        for (int i = days; i >= 0; i--) {
            result.put(LocalDate.now(ZoneOffset.UTC).minusDays(i), BigDecimal.ZERO);
        }
        metrics.forEach(m ->
                result.put(m.getMetricDate(),
                        m.getAiCostUsd() != null ? m.getAiCostUsd() : BigDecimal.ZERO)
        );
        return result;
    }

    // ── Repository leaderboard ────────────────────────────────────────────────

    /**
     * Top repositories by issue count — powers the bar chart.
     */
    public List<RepoStats> getTopReposByIssues(int limit) {
        List<Object[]> raw = sessionRepository.getTopReposByIssueCount(
                PageRequest.of(0, limit)
        );
        return raw.stream()
                .map(row -> new RepoStats(
                        (String) row[0],  // owner
                        (String) row[1],  // name
                        toInt(row[2]),    // totalReviews
                        toInt(row[3])     // totalIssues
                ))
                .toList();
    }

    // ── Recent reviews ────────────────────────────────────────────────────────

    /**
     * Most recent ReviewSessions — shown in the reviews list page.
     */
    public List<ReviewSession> getRecentReviews(int limit) {
        return sessionRepository.findTop50ByOrderByCreatedAtDesc()
                .stream().limit(limit).toList();
    }

    /**
     * One specific ReviewSession with all its comments.
     * Used by the review detail page.
     */
    public Optional<ReviewSession> getReviewDetail(Long sessionId) {
        return sessionRepository.findById(sessionId);
    }

    // ── Cost summary ──────────────────────────────────────────────────────────

    /**
     * Total cost for the current calendar month.
     * Shown prominently on the dashboard as a cost awareness indicator.
     */
    public BigDecimal getCostThisMonth() {
        LocalDate firstOfMonth = LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
        List<DailyMetrics> thisMonth = metricsRepository
                .findAllByMetricDateBetween(firstOfMonth, LocalDate.now(ZoneOffset.UTC));

        return thisMonth.stream()
                .map(DailyMetrics::getAiCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Estimated monthly cost based on current daily average.
     * Shown as a projection: "at current rate: $X/month"
     */
    public BigDecimal getProjectedMonthlyCost() {
        BigDecimal last7DaysCost = getCostForLastNDays(7);
        BigDecimal dailyAverage  = last7DaysCost.divide(
                BigDecimal.valueOf(7), 6, java.math.RoundingMode.HALF_UP
        );
        return dailyAverage.multiply(BigDecimal.valueOf(30))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private BigDecimal getCostForLastNDays(int days) {
        LocalDate from = LocalDate.now(ZoneOffset.UTC).minusDays(days);
        return metricsRepository
                .findAllByMetricDateBetween(from, LocalDate.now(ZoneOffset.UTC))
                .stream()
                .map(DailyMetrics::getAiCostUsd)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private int toInt(Object o) { return o == null ? 0 : ((Number) o).intValue(); }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record OverviewStats(
            int        totalReviews,
            int        totalIssues,
            long       totalTokens,
            BigDecimal totalCost,
            double     avgCacheHitRate,
            int        periodDays
    ) {
        public double issuesPerReview() {
            return totalReviews > 0 ? (double) totalIssues / totalReviews : 0;
        }
        public String cacheHitRatePercent() {
            return String.format("%.1f%%", avgCacheHitRate * 100);
        }
    }

    public record RepoStats(
            String owner,
            String name,
            int    totalReviews,
            int    totalIssues
    ) {
        public String fullName() { return owner + "/" + name; }
        public double issuesPerReview() {
            return totalReviews > 0 ? (double) totalIssues / totalReviews : 0;
        }
    }
}
