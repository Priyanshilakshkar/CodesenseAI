package com.codeSense_Ai.Dto;

import java.util.List;

public record GitHubReview(
        String body,
        String event,
        List<GitHubReviewComment> comments
) {
    public static GitHubReview asComment(String body,
                                         List<GitHubReviewComment> comments) {
        return new GitHubReview(body, "COMMENT", comments);
    }
}
