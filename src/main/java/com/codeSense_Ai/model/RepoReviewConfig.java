package com.codeSense_Ai.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder(toBuilder = true)
public class RepoReviewConfig {
    @Builder.Default
    private final int maxComments = 80;

    /**
     * Which OpenAI model to use for this repo.
     * Default: gpt-4o (from application.yml primary model).
     * Some repos may prefer gpt-4o-mini for cost savings.
     * Overrides ModelRouter's default choice.
     */
    @Builder.Default
    private final String model = "gpt-4o";

    /**
     * Minimum AI confidence score to include a finding.
     * Range: 0–100. Default: 70 (from application.yml).
     * Higher = fewer but more accurate comments.
     * Lower  = more comments but more false positives.
     */
    @Builder.Default
    private final int confidenceThreshold = 70;

    /**
     * Minimum severity score to include a finding.
     * Range: 0–100. Default: 0 (include everything above confidence threshold).
     * Set to 50 to skip style/info findings entirely.
     */
    @Builder.Default
    private final int minimumSeverity = 0;

    // ── File filtering ────────────────────────────────────────────────────────

    /**
     * Glob patterns for files to skip entirely — never sent to AI.
     * Applied before any other processing.
     *
     * Examples:
     *   "**\/*.generated.java"  → all generated Java files
     *   "src/test/**"           → all test files
     *   "*.md"                  → all markdown files
     */
    @Builder.Default
    private final List<String> ignoreFiles = List.of();

    /**
     * Only review files matching these patterns.
     * If empty, all files are reviewed (subject to ignoreFiles).
     * If set, only matching files are reviewed.
     *
     * Example: ["src/main/**"] to only review production code.
     */
    @Builder.Default
    private final List<String> onlyFiles = List.of();

    // ── Custom rules ──────────────────────────────────────────────────────────

    /**
     * Patterns that should ALWAYS be flagged, regardless of AI confidence.
     * The RuleEngine adds these to the prompt AND post-processes results
     * to ensure they appear even if the AI missed them.
     *
     * Examples:
     *   "System.out.println"  → flag debug print statements
     *   "TODO|FIXME|HACK"     → flag unfinished work
     *   "Thread.sleep"        → flag blocking calls
     */
    @Builder.Default
    private final List<String> alwaysFlag = List.of();

    /**
     * Patterns the AI should require in the reviewed code.
     * Added as instructions to the AI prompt.
     * Does not auto-fail if missing — just adds context for AI judgment.
     *
     * Examples:
     *   "parameterized queries only"
     *   "all public methods must have Javadoc"
     */
    @Builder.Default
    private final List<String> requirePatterns = List.of();

    /**
     * Issue categories to suppress entirely — never posted to GitHub.
     * Applied by ConfidenceFilter after AI response is parsed.
     *
     * Example: [STYLE] to disable all style comments for a repo
     * whose team has agreed to handle style via formatter only.
     */
    @Builder.Default
    @JsonProperty("ignoreCategories")
    private final List<ReviewIssue.Category> ignoreCategories = List.of();

    // ── Language overrides ────────────────────────────────────────────────────

    /**
     * Per-language config overrides.
     * Allows different thresholds for different file types in the same repo.
     *
     * Example — stricter for Java, looser for scripts:
     *   languageOverrides:
     *     java:
     *       confidenceThreshold: 85
     *     sh:
     *       confidenceThreshold: 50
     */
    @Builder.Default
    private final java.util.Map<String, LanguageOverride> languageOverrides = java.util.Map.of();

    // ── Metadata ──────────────────────────────────────────────────────────────

    /**
     * Human-readable name for this config version.
     * Stored in ReviewSession for debugging — "which config was active?"
     */
    @Builder.Default
    private final String configVersion = "default";

    /**
     * True if this config was loaded from .codesense.yml in the repo.
     * False if it was loaded from DB or is the system default.
     * Logged in ReviewSession — useful for debugging config issues.
     */
    @Builder.Default
    private final boolean loadedFromRepo = false;

    // ── Nested types ──────────────────────────────────────────────────────────

    /**
     * Per-language configuration override.
     * Only the fields set here override the top-level config.
     * Unset fields fall back to the parent RepoReviewConfig value.
     */
    @Getter
    @Builder
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LanguageOverride {
        private final Integer confidenceThreshold; // null = use parent value
        private final Integer minimumSeverity;     // null = use parent value
        private final Integer maxComments;         // null = use parent value
        private final String  model;               // null = use parent value
    }

    // ── Computed helpers ──────────────────────────────────────────────────────

    /**
     * True if this config has any custom rules beyond defaults.
     * PromptBuilder uses this to decide whether to add a
     * "Repository-Specific Rules" section to the AI prompt.
     */
    public boolean hasCustomRules() {
        return !alwaysFlag.isEmpty() || !requirePatterns.isEmpty();
    }

    /**
     * True if the given file path should be skipped entirely.
     * Called by GitHubDiffFetchService during file classification.
     *
     * Uses glob matching: "**\/*.generated.java" matches
     * "src/main/java/Foo.generated.java" at any depth.
     */
    public boolean shouldIgnoreFile(String filePath) {
        // onlyFiles: if set, file must match at least one pattern
        if (!onlyFiles.isEmpty()) {
            boolean matchesOnly = onlyFiles.stream()
                    .anyMatch(pattern -> matchesGlob(filePath, pattern));
            if (!matchesOnly) return true;
        }

        // ignoreFiles: if file matches any pattern, skip it
        return ignoreFiles.stream()
                .anyMatch(pattern -> matchesGlob(filePath, pattern));
    }

    /**
     * True if this issue category should be suppressed.
     * Called by ConfidenceFilter after AI response is parsed.
     */
    public boolean shouldIgnoreCategory(ReviewIssue.Category category) {
        return ignoreCategories.contains(category);
    }

    /**
     * Effective confidence threshold for a given language.
     * Checks languageOverrides first, falls back to top-level threshold.
     */
    public int effectiveConfidenceThreshold(String language) {
        if (language != null && languageOverrides.containsKey(language)) {
            Integer override = languageOverrides.get(language).getConfidenceThreshold();
            if (override != null) return override;
        }
        return confidenceThreshold;
    }

    /**
     * Effective model for a given language.
     * Checks languageOverrides first, falls back to top-level model.
     */
    public String effectiveModel(String language) {
        if (language != null && languageOverrides.containsKey(language)) {
            String override = languageOverrides.get(language).getModel();
            if (override != null) return override;
        }
        return model;
    }

    // ── Factory methods ───────────────────────────────────────────────────────

    /**
     * System defaults — used when no .codesense.yml exists
     * and no DB config has been set for the repo.
     */
    public static RepoReviewConfig defaults() {
        return RepoReviewConfig.builder()
                .configVersion("system-default")
                .loadedFromRepo(false)
                .build();
    }

    /**
     * Merge two configs — repo config takes priority over defaults.
     * Used by RuleLoader: system defaults + repo overrides = effective config.
     */
    public static RepoReviewConfig merge(RepoReviewConfig base, RepoReviewConfig override) {
        return RepoReviewConfig.builder()
                .maxComments(override.maxComments != 80
                        ? override.maxComments : base.maxComments)
                .model(!override.model.equals("gpt-4o")
                        ? override.model : base.model)
                .confidenceThreshold(override.confidenceThreshold != 70
                        ? override.confidenceThreshold : base.confidenceThreshold)
                .minimumSeverity(override.minimumSeverity != 0
                        ? override.minimumSeverity : base.minimumSeverity)
                .ignoreFiles(override.ignoreFiles.isEmpty()
                        ? base.ignoreFiles : override.ignoreFiles)
                .onlyFiles(override.onlyFiles.isEmpty()
                        ? base.onlyFiles : override.onlyFiles)
                .alwaysFlag(override.alwaysFlag.isEmpty()
                        ? base.alwaysFlag : override.alwaysFlag)
                .requirePatterns(override.requirePatterns.isEmpty()
                        ? base.requirePatterns : override.requirePatterns)
                .ignoreCategories(override.ignoreCategories.isEmpty()
                        ? base.ignoreCategories : override.ignoreCategories)
                .languageOverrides(override.languageOverrides.isEmpty()
                        ? base.languageOverrides : override.languageOverrides)
                .configVersion(override.configVersion)
                .loadedFromRepo(override.loadedFromRepo)
                .build();
    }

    // ── Private: glob matching ────────────────────────────────────────────────

    /**
     * Simple glob matcher supporting * and **.
     *
     *  *   matches any sequence of characters except /
     *  **  matches any sequence of characters including /
     *  ?   matches any single character
     *
     * Examples:
     *   "**\/*.generated.java" matches "src/main/Foo.generated.java"
     *   "src/test/**"          matches "src/test/java/FooTest.java"
     *   "*.md"                 matches "README.md" but not "docs/README.md"
     */
    private boolean matchesGlob(String path, String pattern) {
        // Convert glob to regex
        String regex = pattern
                .replace(".", "\\.")       // escape dots
                .replace("**", "§§§")     // temp placeholder for **
                .replace("*", "[^/]*")    // * = anything except /
                .replace("§§§", ".*")     // ** = anything including /
                .replace("?", "[^/]");    // ? = single char except /

        return path.matches(regex);
    }
}
