package com.codeSense_Ai.Analytics;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DashboardDataBuilder {
    private final AnalyticsService analyticsService;
    private final ObjectMapper objectMapper;

    private static final int DEFAULT_PERIOD_DAYS = 30;

    /**
     * Builds the complete model for the main dashboard page.
     * Called by DashboardController for GET /dashboard
     */
    public Map<String, Object> buildDashboardModel(int days) {
        Map<String, Object> model = new LinkedHashMap<>();

        // ── Period selector ───────────────────────────────────────
        model.put("selectedDays", days);
        model.put("periodOptions", List.of(7, 14, 30, 90));

        // ── Top stat cards ────────────────────────────────────────
        AnalyticsService.OverviewStats overview =
                analyticsService.getOverviewStats(days);
        model.put("overview", overview);

        // ── Cost cards ────────────────────────────────────────────
        model.put("costThisMonth",       analyticsService.getCostThisMonth());
        model.put("projectedMonthlyCost",analyticsService.getProjectedMonthlyCost());

        // ── Chart data (JSON for Chart.js) ────────────────────────
        model.put("reviewsChartJson",
                toJson(buildReviewsChartData(days)));

        model.put("issuesCategoryJson",
                toJson(buildCategoryChartData(days)));

        model.put("tokenUsageJson",
                toJson(buildTokenChartData(days)));

        model.put("costTrendJson",
                toJson(buildCostChartData(days)));

        // ── Repository leaderboard ────────────────────────────────
        model.put("topRepos", analyticsService.getTopReposByIssues(10));

        // ── Recent reviews table ──────────────────────────────────
        model.put("recentReviews", analyticsService.getRecentReviews(10));

        return model;
    }

    /**
     * Builds the model for the reviews list page.
     * Called by DashboardController for GET /dashboard/reviews
     */
    public Map<String, Object> buildReviewsListModel() {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("reviews", analyticsService.getRecentReviews(50));
        return model;
    }

    /**
     * Builds the model for the review detail page.
     * Called by DashboardController for GET /dashboard/reviews/{id}
     */
    public Map<String, Object> buildReviewDetailModel(Long sessionId) {
        Map<String, Object> model = new LinkedHashMap<>();

        analyticsService.getReviewDetail(sessionId).ifPresent(session -> {
            model.put("session", session);
            model.put("pr", session.getPullRequest());
            model.put("repo", session.getPullRequest().getRepository());
            // Comments are lazy-loaded — Thymeleaf iterates them in template
        });

        return model;
    }

    // ── Chart data builders ───────────────────────────────────────────────────

    /**
     * Line chart: reviews per day.
     * Chart.js format: { labels: ["Jan 1", ...], datasets: [{ data: [3, 5, ...] }] }
     */
    private Map<String, Object> buildReviewsChartData(int days) {
        Map<LocalDate, Integer> data = analyticsService.getReviewsPerDay(days);

        List<String> labels = data.keySet().stream()
                .map(d -> d.getMonthValue() + "/" + d.getDayOfMonth())
                .toList();

        List<Integer> values = new ArrayList<>(data.values());

        return Map.of(
                "labels", labels,
                "datasets", List.of(Map.of(
                        "label",           "Reviews per day",
                        "data",            values,
                        "borderColor",     "#534AB7",
                        "backgroundColor", "rgba(83,74,183,0.1)",
                        "fill",            true,
                        "tension",         0.4
                ))
        );
    }

    /**
     * Pie/donut chart: issues by category.
     */
    private Map<String, Object> buildCategoryChartData(int days) {
        Map<String, Integer> data = analyticsService.getIssuesByCategory(days);

        // Category colors matching the emoji colors used in PR comments
        Map<String, String> categoryColors = Map.of(
                "Security",        "#EF4444",  // red
                "Bug",             "#F97316",  // orange
                "Performance",     "#EAB308",  // yellow
                "Maintainability", "#6B7280",  // gray
                "Style",           "#3B82F6"   // blue
        );

        List<String> labels = new ArrayList<>(data.keySet());
        List<Integer> values = labels.stream()
                .map(data::get).toList();
        List<String> colors = labels.stream()
                .map(l -> categoryColors.getOrDefault(l, "#6B7280"))
                .toList();

        return Map.of(
                "labels", labels,
                "datasets", List.of(Map.of(
                        "data",            values,
                        "backgroundColor", colors,
                        "borderWidth",     2
                ))
        );
    }

    /**
     * Area chart: token usage per day.
     */
    private Map<String, Object> buildTokenChartData(int days) {
        Map<LocalDate, Long> data = analyticsService.getTokenUsagePerDay(days);

        List<String>  labels = data.keySet().stream()
                .map(d -> d.getMonthValue() + "/" + d.getDayOfMonth()).toList();
        List<Long> values = new ArrayList<>(data.values());

        return Map.of(
                "labels", labels,
                "datasets", List.of(Map.of(
                        "label",           "Tokens used",
                        "data",            values,
                        "borderColor",     "#0EA5E9",
                        "backgroundColor", "rgba(14,165,233,0.1)",
                        "fill",            true,
                        "tension",         0.3
                ))
        );
    }

    /**
     * Line chart: AI cost per day in USD.
     */
    private Map<String, Object> buildCostChartData(int days) {
        Map<LocalDate, BigDecimal> data = analyticsService.getCostPerDay(days);

        List<String>     labels = data.keySet().stream()
                .map(d -> d.getMonthValue() + "/" + d.getDayOfMonth()).toList();
        List<BigDecimal> values = new ArrayList<>(data.values());

        return Map.of(
                "labels", labels,
                "datasets", List.of(Map.of(
                        "label",           "Cost (USD)",
                        "data",            values,
                        "borderColor",     "#10B981",
                        "backgroundColor", "rgba(16,185,129,0.1)",
                        "fill",            true,
                        "tension",         0.3
                ))
        );
    }

    private String toJson(Object data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.error("Failed to serialize chart data: {}", e.getMessage());
            return "{}";
        }
    }
}
