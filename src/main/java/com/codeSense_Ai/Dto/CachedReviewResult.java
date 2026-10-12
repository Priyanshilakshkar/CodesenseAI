package com.codeSense_Ai.Dto;

import com.codeSense_Ai.model.ReviewIssue;

import java.util.List;

public record CachedReviewResult(
        List<ReviewIssue> issues,

        // Which model produced this — stored so callers can log it
        String modelUsed,

        // Token count — added to ReviewSession totals even for cache hits
        int tokensUsed
) {
    public boolean hasNoIssues() {
        return issues == null || issues.isEmpty();
    }
}
