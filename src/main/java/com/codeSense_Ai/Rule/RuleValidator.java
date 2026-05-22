package com.codeSense_Ai.Rule;

import com.codeSense_Ai.model.RepoReviewConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

@Component
@Slf4j
public class RuleValidator {
    private static final Set<String> VALID_MODELS = Set.of(
            "gpt-4o", "gpt-4o-mini"
    );

    private static final Set<String> VALID_SEVERITIES = Set.of(
            "INFO", "WARNING", "ERROR", "CRITICAL"
    );

    /**
     * Validates a parsed RepoReviewConfig.
     * Returns a ValidationResult — never throws.
     * Caller (RuleLoader) decides whether to reject or warn.
     */
    public ValidationResult validate(RepoReviewConfig config) {
        List<String> errors   = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // ── Numeric range checks ──────────────────────────────────
        if (config.getMaxComments() < 1 || config.getMaxComments() > 200) {
            errors.add("maxComments must be between 1 and 200, got: "
                    + config.getMaxComments());
        }

        if (config.getConfidenceThreshold() < 0
                || config.getConfidenceThreshold() > 100) {
            errors.add("confidenceThreshold must be 0-100, got: "
                    + config.getConfidenceThreshold());
        }

        if (config.getMinimumSeverity() < 0
                || config.getMinimumSeverity() > 100) {
            errors.add("minimumSeverity must be 0-100, got: "
                    + config.getMinimumSeverity());
        }

        // ── Model validation ──────────────────────────────────────
        if (!VALID_MODELS.contains(config.getModel())) {
            errors.add("Unknown model '" + config.getModel()
                    + "'. Valid values: " + VALID_MODELS);
        }

        // ── Glob pattern validation ───────────────────────────────
        for (String pattern : config.getIgnoreFiles()) {
            if (pattern == null || pattern.isBlank()) {
                warnings.add("Empty pattern in ignoreFiles — skipped");
                continue;
            }
            if (pattern.length() > 500) {
                errors.add("ignoreFiles pattern too long (max 500 chars): "
                        + pattern.substring(0, 50) + "...");
            }
        }

        // ── AlwaysFlag pattern validation ─────────────────────────
        for (String pattern : config.getAlwaysFlag()) {
            try {
                Pattern.compile(pattern); // validate as regex
            } catch (PatternSyntaxException e) {
                errors.add("Invalid regex in alwaysFlag: '"
                        + pattern + "' — " + e.getMessage());
            }
        }

        // ── Language override validation ───────────────────────────
        config.getLanguageOverrides().forEach((lang, override) -> {
            if (lang == null || lang.isBlank()) {
                errors.add("Empty language key in languageOverrides");
                return;
            }
            if (override.getModel() != null
                    && !VALID_MODELS.contains(override.getModel())) {
                errors.add("Unknown model in languageOverrides." + lang
                        + ": " + override.getModel());
            }
            if (override.getConfidenceThreshold() != null
                    && (override.getConfidenceThreshold() < 0
                    || override.getConfidenceThreshold() > 100)) {
                errors.add("languageOverrides." + lang
                        + ".confidenceThreshold must be 0-100");
            }
        });

        // ── Soft warnings ─────────────────────────────────────────
        if (config.getMaxComments() > 100) {
            warnings.add("maxComments=" + config.getMaxComments()
                    + " is high — PRs with many comments can be noisy");
        }

        if (config.getConfidenceThreshold() < 50) {
            warnings.add("confidenceThreshold=" + config.getConfidenceThreshold()
                    + " is low — expect more false positives");
        }

        if (!config.getOnlyFiles().isEmpty() && !config.getIgnoreFiles().isEmpty()) {
            warnings.add("Both onlyFiles and ignoreFiles are set — "
                    + "onlyFiles takes precedence");
        }

        if (!errors.isEmpty()) {
            log.warn("Config validation failed with {} errors: {}", errors.size(), errors);
        }
        if (!warnings.isEmpty()) {
            log.info("Config validation warnings: {}", warnings);
        }

        return new ValidationResult(errors, warnings);
    }

    // ── Result type ───────────────────────────────────────────────────────────

    public record ValidationResult(
            List<String> errors,
            List<String> warnings
    ) {
        public boolean isValid()    { return errors.isEmpty(); }
        public boolean hasWarnings(){ return !warnings.isEmpty(); }

        public String errorSummary() {
            return String.join("; ", errors);
        }
    }
}
