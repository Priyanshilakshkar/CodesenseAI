package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

public record GitHubReviewResponse(

        Long id,


        @JsonProperty("node_id")
        String nodeId,


        @JsonProperty("user")
        ReviewUser user,


        String body,


        String state,

        @JsonProperty("html_url")
        String htmlUrl,


        @JsonProperty("pull_request_url")
        String pullRequestUrl,

        @JsonProperty("submitted_at")
        Instant submittedAt,

        @JsonProperty("commit_id")
        String commitId,


        @JsonProperty("author_association")
        String authorAssociation
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReviewUser(

            // GitHub username of the bot account
            // e.g. "codesense-ai[bot]"
            String login,

            // GitHub's numeric user ID for the bot
            Long id,

            // GitHub's node ID (GraphQL)
            @JsonProperty("node_id")
            String nodeId,

            // Direct link to the bot's avatar image
            @JsonProperty("avatar_url")
            String avatarUrl,

            // User type — for GitHub App bots this is "Bot"
            // Other values: "User", "Organization"
            String type

    ) {}

    // ── Validation helpers ─────────────────────────────────────────────────────

    /**
     * True if GitHub accepted our review and gave it a valid ID.
     * False if something went wrong and GitHub returned a null ID.
     * GitHubReviewPoster checks this before returning.
     */
    public boolean isValid() {
        return id != null && id > 0;
    }

    /**
     * True if the review was posted as a comment (not approve/reject).
     * We always post as COMMENT — this verifies GitHub honored our request.
     */
    public boolean isComment() {
        return "COMMENTED".equalsIgnoreCase(state);
    }

    /**
     * True if the review is still in pending/draft state.
     * This would indicate our submission failed silently.
     * Should never be true — we always submit, never save as draft.
     */
    public boolean isPending() {
        return "PENDING".equalsIgnoreCase(state);
    }

    /**
     * The GitHub review URL — safe to log and include in error messages.
     * Falls back to a placeholder if GitHub didn't return a URL.
     */
    public String reviewUrl() {
        return htmlUrl != null ? htmlUrl : "(URL not available — id=" + id + ")";
    }

    /**
     * Short summary for log lines — avoids logging the full body.
     * Example: "GitHubReviewResponse{id=99887766, state=COMMENTED, url=https://...}"
     */
    @Override
    public String toString() {
        return String.format(
                "GitHubReviewResponse{id=%d, state=%s, commitId=%s, url=%s}",
                id, state,
                commitId != null ? commitId.substring(0, Math.min(8, commitId.length())) : "null",
                htmlUrl
        );
    }
}
