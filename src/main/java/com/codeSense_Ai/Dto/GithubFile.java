package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubFile(
        // Full path of the file — e.g. "src/main/java/com/codesense/UserService.java"
        String filename,


        String status,

        int additions,

        int deletions,

        // Total lines changed = additions + deletions
        int changes,


        String patch,


        String blobSha,


        @JsonProperty("blob_url")
        String blobUrl,


        @JsonProperty("raw_url")
        String rawUrl,

        // Direct URL to the file's contents via GitHub API
        @JsonProperty("contents_url")
        String contentsUrl,


        @JsonProperty("previous_filename")
        String previousFilename
) {
    public boolean isDeleted() {
        return "removed".equals(status);
    }

    /** True if this is a brand new file added in this PR */
    public boolean isAdded() {
        return "added".equals(status);
    }

    /** True if this file has no reviewable diff (binary, mode-change, or unchanged) */
    public boolean hasPatch() {
        return patch != null && !patch.isBlank();
    }

    /** True if this is a binary file (images, compiled artifacts, etc.) */
    public boolean isBinary() {
        return patch == null && !"removed".equals(status);
    }

    /** True if the file was renamed (possibly with changes too) */
    public boolean isRenamed() {
        return "renamed".equals(status);
    }

    /**
     * The effective filename to display/log.
     * For renamed files, shows "old/path → new/path".
     */
    public String displayName() {
        if (isRenamed() && previousFilename != null) {
            return previousFilename + " → " + filename;
        }
        return filename;
    }

    /**
     * File extension, lowercase — used by ChunkingEngine to pick
     * the right chunking strategy (Java vs Python vs unknown).
     * Returns empty string if no extension found.
     */
    public String extension() {
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex == -1 || dotIndex == filename.length() - 1) {
            return "";
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    /**
     * True if this file should be skipped entirely before even
     * looking at its content — lock files, generated code, etc.
     * The RuleEngine does deeper filtering; this is just the obvious cases.
     */
    public boolean isLikelyNotReviewable() {
        String lower = filename.toLowerCase();
        return lower.endsWith(".lock")          // package-lock.json, Gemfile.lock
                || lower.endsWith(".min.js")        // minified JS
                || lower.endsWith(".min.css")       // minified CSS
                || lower.contains("/generated/")    // generated code directories
                || lower.contains(".generated.")    // e.g. UserMapper.generated.java
                || lower.endsWith(".png")
                || lower.endsWith(".jpg")
                || lower.endsWith(".jpeg")
                || lower.endsWith(".gif")
                || lower.endsWith(".svg")
                || lower.endsWith(".ico")
                || lower.endsWith(".pdf")
                || lower.endsWith(".jar")
                || lower.endsWith(".class");
    }
}
