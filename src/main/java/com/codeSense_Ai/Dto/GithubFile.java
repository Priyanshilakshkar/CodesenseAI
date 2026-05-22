package com.codeSense_Ai.Dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GithubFile(
        // Full path of the file — e.g. "src/main/java/com/codesense/UserService.java"
        String filename,

        // What happened to this file:
        // "added"    → new file created in this PR
        // "removed"  → file deleted in this PR
        // "modified" → existing file changed
        // "renamed"  → file moved/renamed (check previousFilename)
        // "copied"   → file was copied from another path
        // "changed"  → file mode changed (permissions), no content change
        // "unchanged"→ file included for context but not changed
        String status,

        // Number of lines added (shown as green + lines in GitHub UI)
        int additions,

        // Number of lines removed (shown as red - lines in GitHub UI)
        int deletions,

        // Total lines changed = additions + deletions
        int changes,

        // The raw unified diff text for this file — this is what DiffParserEngine reads.
        // Example:
        //   @@ -15,6 +15,7 @@ public class UserService {
        //    public User findById(Long id) {
        //   -    return repo.findById(id);
        //   +    return repo.findById(id)
        //   +        .orElseThrow(UserNotFoundException::new);
        //    }
        //
        // NULL for binary files — always null-check before parsing!
        String patch,

        // SHA-256 blob hash of the file content.
        // This is what FileHashCache uses as the cache key.
        // Same file content = same SHA, even across different PRs.
        @JsonProperty("sha")
        String blobSha,

        // Direct URL to view the file on GitHub
        @JsonProperty("blob_url")
        String blobUrl,

        // Direct URL to the raw file content
        @JsonProperty("raw_url")
        String rawUrl,

        // Direct URL to the file's contents via GitHub API
        @JsonProperty("contents_url")
        String contentsUrl,

        // Only populated when status = "renamed" or "copied"
        // Contains the old path before the rename
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
