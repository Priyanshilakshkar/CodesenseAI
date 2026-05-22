package com.codeSense_Ai.Dto;

import java.util.List;

public record DiffFetchResult(
        // Files that have a patch and passed all filters — ready for AI review
        List<GithubFile> reviewableFiles,

        // Files skipped because FileHashCache had a fresh result for them
        List<GithubFile> cachedFiles,

        // Files skipped because they're binary, deleted, lock files, etc.
        List<GithubFile> skippedFiles,

        // Total files GitHub reported (reviewable + cached + skipped)
        int totalFilesInPr,

        // The installation ID used — stored in ReviewSession for debugging
        Long installationId
) {
    public int reviewableCount() {
        return reviewableFiles.size();
    }

    /** True if every changed file was either cached or skipped — no AI needed */
    public boolean isFullyCached() {
        return reviewableFiles.isEmpty();
    }
}
