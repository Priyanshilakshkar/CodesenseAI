package com.codeSense_Ai.Rule;

import com.codeSense_Ai.Dto.GithubFile;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.DiffLine;
import com.codeSense_Ai.model.RepoReviewConfig;
import com.codeSense_Ai.model.ReviewIssue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
@RequiredArgsConstructor
public class RuleEngine {
    private final SecurityRules  securityRules;
    private final PerformanceRules performanceRules;

    // ── Pre-processing (runs BEFORE AI) ──────────────────────────────────────

    /**
     * Filters files that should be ignored according to config.
     * Called by GitHubDiffFetchService during file classification.
     *
     * @param files   All reviewable files from GitHub
     * @param config  Repo config with ignoreFiles/onlyFiles patterns
     * @return        Files that should actually be reviewed
     */
    public List<GithubFile> filterFiles(List<GithubFile> files,
                                        RepoReviewConfig config) {
        List<GithubFile> filtered = files.stream()
                .filter(file -> !config.shouldIgnoreFile(file.filename()))
                .toList();

        int skipped = files.size() - filtered.size();
        if (skipped > 0) {
            log.info("RuleEngine filtered {} files via ignoreFiles/onlyFiles config",
                    skipped);
        }

        return filtered;
    }

    /**
     * Builds additional prompt instructions from config rules.
     * Called by PromptBuilder to inject repo-specific requirements
     * into the system prompt sent to the AI.
     *
     * @return  Markdown string to append to the AI system prompt
     */
    public String buildPromptAdditions(RepoReviewConfig config) {
        if (!config.hasCustomRules()) return "";

        StringBuilder additions = new StringBuilder();
        additions.append("\n## Repository-Specific Rules\n");
        additions.append("Apply these rules in addition to standard review:\n\n");

        // AlwaysFlag patterns — instruct AI to look for these
        if (!config.getAlwaysFlag().isEmpty()) {
            additions.append("**Always flag these patterns:**\n");
            config.getAlwaysFlag().forEach(pattern ->
                    additions.append("- `").append(pattern).append("`\n")
            );
            additions.append("\n");
        }

        // RequirePatterns — coding standards to enforce
        if (!config.getRequirePatterns().isEmpty()) {
            additions.append("**Team coding standards to enforce:**\n");
            config.getRequirePatterns().forEach(req ->
                    additions.append("- ").append(req).append("\n")
            );
        }

        return additions.toString();
    }

    // ── Post-processing (runs AFTER AI) ──────────────────────────────────────

    /**
     * Applies all post-processing rules to AI findings.
     * Run after AIResponseParser and before ConfidenceFilter.
     *
     * Steps:
     *   1. Run built-in security/performance rules (pattern matching)
     *   2. Filter by ignoreCategories
     *   3. Filter by minimumSeverity
     *   4. Boost severity for alwaysFlag pattern matches
     */
    public List<ReviewIssue> postProcess(List<ReviewIssue> aiIssues,
                                         List<DiffFile>    diffFiles,
                                         RepoReviewConfig  config) {
        List<ReviewIssue> result = new ArrayList<>(aiIssues);

        // Step 1: Add built-in rule findings (independent of AI)
        List<ReviewIssue> ruleFindings = runBuiltInRules(diffFiles, config);
        result.addAll(ruleFindings);
        if (!ruleFindings.isEmpty()) {
            log.info("Built-in rules added {} findings", ruleFindings.size());
        }

        // Step 2: Filter suppressed categories
        result = result.stream()
                .filter(issue -> !config.shouldIgnoreCategory(issue.category()))
                .toList();

        // Step 3: Filter by minimum severity
        result = result.stream()
                .filter(issue -> issue.severity() >= config.getMinimumSeverity())
                .toList();

        // Step 4: Boost severity for alwaysFlag pattern matches
        result = boostAlwaysFlagMatches(result, diffFiles, config);

        log.debug("RuleEngine postProcess: {} AI issues + {} rule issues → {} final",
                aiIssues.size(), ruleFindings.size(), result.size());

        return result;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private List<ReviewIssue> runBuiltInRules(List<DiffFile> diffFiles,
                                              RepoReviewConfig config) {
        List<ReviewIssue> findings = new ArrayList<>();

        for (DiffFile diffFile : diffFiles) {
            // Only scan ADDED lines — removed lines are already gone
            List<DiffLine> addedLines = diffFile.getAllLines().stream()
                    .filter(DiffLine::isAdded)
                    .toList();

            // Run security rules
            findings.addAll(securityRules.scan(addedLines, diffFile.getNewPath()));

            // Run performance rules
            findings.addAll(performanceRules.scan(addedLines, diffFile.getNewPath()));

            // Run alwaysFlag patterns from repo config
            findings.addAll(scanAlwaysFlag(addedLines, diffFile.getNewPath(), config));
        }

        return findings;
    }

    private List<ReviewIssue> scanAlwaysFlag(List<DiffLine> addedLines,
                                             String filePath,
                                             RepoReviewConfig config) {
        List<ReviewIssue> findings = new ArrayList<>();

        for (String pattern : config.getAlwaysFlag()) {
            try {
                Pattern compiled = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

                for (DiffLine line : addedLines) {
                    Matcher matcher = compiled.matcher(line.content());
                    if (matcher.find() && line.getNewLineNum() != null) {
                        findings.add(new ReviewIssue(
                                line.getNewLineNum(),
                                40,    // severity: LOW — teams should tune this
                                100,   // confidence: 100 — pattern matched exactly
                                ReviewIssue.Category.MAINTAINABILITY,
                                "Pattern flagged: " + pattern,
                                "This line matches a pattern your team has configured "
                                        + "to always flag: `" + pattern + "`",
                                "Review whether this is intentional.",
                                "rule-engine",
                                filePath
                        ));
                    }
                }
            } catch (Exception e) {
                log.debug("Error applying alwaysFlag pattern '{}': {}",
                        pattern, e.getMessage());
            }
        }

        return findings;
    }

    /**
     * Boosts severity of AI findings that match alwaysFlag patterns.
     * If the AI found a "SQL injection" issue and the team has flagged
     * SQL patterns, boost that finding's severity by 10 points.
     */
    private List<ReviewIssue> boostAlwaysFlagMatches(List<ReviewIssue> issues,
                                                     List<DiffFile> diffFiles,
                                                     RepoReviewConfig config) {
        if (config.getAlwaysFlag().isEmpty()) return issues;

        // Build a line content lookup for pattern matching
        java.util.Map<String, java.util.Map<Integer, String>> lineContents =
                new java.util.HashMap<>();
        for (DiffFile f : diffFiles) {
            java.util.Map<Integer, String> byLine = new java.util.HashMap<>();
            f.getAllLines().stream()
                    .filter(l -> l.getNewLineNum() != null)
                    .forEach(l -> byLine.put(l.getNewLineNum(), l.content()));
            lineContents.put(f.getNewPath(), byLine);
        }

        return issues.stream().map(issue -> {
            String lineContent = lineContents
                    .getOrDefault(issue.filePath(), java.util.Map.of())
                    .getOrDefault(issue.line(), "");

            boolean matchesAlwaysFlag = config.getAlwaysFlag().stream()
                    .anyMatch(pattern -> {
                        try {
                            return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
                                    .matcher(lineContent).find();
                        } catch (Exception e) {
                            return false;
                        }
                    });

            if (matchesAlwaysFlag) {
                // Boost severity by 10, cap at 100
                int boostedSeverity = Math.min(100, issue.severity() + 10);
                return new ReviewIssue(
                        issue.line(), boostedSeverity, issue.confidence(),
                        issue.category(), issue.title(), issue.body(),
                        issue.suggestion(), issue.chunkId(), issue.filePath()
                );
            }
            return issue;
        }).toList();
    }
}
