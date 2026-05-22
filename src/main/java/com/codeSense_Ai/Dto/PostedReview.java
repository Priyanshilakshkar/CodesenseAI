package com.codeSense_Ai.Dto;

public record PostedReview(
        long   githubReviewId,    // GitHub's ID for the created review
        int    commentsPosted,    // how many inline comments were posted
        int    commentsDropped,   // how many were dropped (no diff position / limit)
        int    duplicatesRemoved, // how many near-duplicates were filtered
        String summaryBody
) {
}
