package com.codeSense_Ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ReviewIssue(
        int line,

        // How serious this issue is — 0 (style nit) to 100 (critical security bug)
        // Issues below confidenceThreshold (default 70) are discarded
        int severity,

        // Confidence that this is a real issue — 0 to 100
        // Separate from severity: a CRITICAL issue can have low confidence
        int confidence,

        // One of: SECURITY, PERFORMANCE, BUG, STYLE, MAINTAINABILITY
        Category category,

        // Short title — shown as the comment heading on GitHub
        // e.g. "Potential SQL injection vulnerability"
        String title,

        // Detailed explanation — the full comment body posted to GitHub
        // Should explain WHY this is an issue, not just what it is
        String body,

        // Concrete fix suggestion — what the developer should do instead
        // e.g. "Use parameterized queries: repo.findById(:id)"
        String suggestion,

        // The chunk ID this issue came from — for deduplication across chunks
        @JsonProperty("chunk_id")
        String chunkId,

        // The file path — copied from the chunk for easy access
        @JsonProperty("file_path")
        String filePath
) {

    public enum Category {
        SECURITY,        // SQL injection, XSS, auth bypass, hardcoded secrets
        PERFORMANCE,     // N+1 queries, memory leaks, blocking calls
        BUG,             // Null pointers, race conditions, logic errors
        STYLE,           // Naming, formatting (low severity only)
        MAINTAINABILITY  // Complex code, missing docs, dead code
    }

    // ── Helpers ──────────────────────────────────────────────

    public boolean isCritical()  { return severity >= 80; }
    public boolean isHigh()      { return severity >= 60 && severity < 80; }
    public boolean isMedium()    { return severity >= 40 && severity < 60; }
    public boolean isLow()       { return severity < 40; }
    public boolean isSecurity()  { return category == Category.SECURITY; }

    /**
     * The formatted comment body posted to GitHub.
     * Combines title, body, and suggestion into one markdown string.
     */
    public String formattedComment() {
        StringBuilder sb = new StringBuilder();

        // Severity emoji prefix
        String emoji = switch (category) {
            case SECURITY -> "🔴";
            case PERFORMANCE -> "🟡";
            case BUG -> "🟠";
            case STYLE -> "🔵";
            case MAINTAINABILITY -> "⚪";
        };

        sb.append(emoji).append(" **").append(title).append("**\n\n");
        sb.append(body).append("\n");

        if (suggestion != null && !suggestion.isBlank()) {
            sb.append("\n**Suggestion:** ").append(suggestion);
        }

        sb.append("\n\n")
                .append("*Severity: ").append(severity).append("/100 · ")
                .append(category).append("*");

        return sb.toString();

    }
}
