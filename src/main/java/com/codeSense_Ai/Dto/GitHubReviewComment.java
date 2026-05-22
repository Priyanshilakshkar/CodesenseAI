package com.codeSense_Ai.Dto;

public record GitHubReviewComment(
        String path,
        Integer position,
        String body
) {
}
