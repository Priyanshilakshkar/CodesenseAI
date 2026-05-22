package com.codeSense_Ai.Config;

import jakarta.validation.constraints.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Builder
@ConfigurationProperties(prefix = "codesense.openai")
@Component
@Validated
public class OpenAiProperties {
    /**
     * OpenAI API key — loaded from environment variable OPENAI_API_KEY.
     * NEVER hardcode this. Never log it.
     *
     * application.yml:
     *   api-key: ${OPENAI_API_KEY}
     */
    @NotBlank(message = "OpenAI API key must be set via OPENAI_API_KEY environment variable")
    private String apiKey;

    // ── Endpoint ──────────────────────────────────────────────────────────────

    /**
     * Base URL for the OpenAI API.
     * Default: https://api.openai.com/v1
     *
     * Overridable so you can point to:
     * - Azure OpenAI endpoint
     * - A local LLM proxy (e.g. LiteLLM, Ollama)
     * - A mock server in integration tests
     */
    @NotBlank(message = "OpenAI base URL must be set")
    private String baseUrl = "https://api.openai.com/v1";

    // ── Model selection ───────────────────────────────────────────────────────

    /**
     * Primary model — used for all reviews by default.
     * Default: gpt-4o (best quality, higher cost).
     *
     * ModelRouter tries this first. Falls back to fallbackModel
     * on 429 (rate limit) or 5xx (server error) responses.
     */
    @NotBlank(message = "Primary model must be set")
    private String primaryModel = "gpt-4o";

    /**
     * Fallback model — used when primaryModel fails.
     * Default: gpt-4o-mini (lower quality, much cheaper).
     *
     * Also used per-repo when RepoReviewConfig specifies
     * a cheaper model for specific file types (e.g. shell scripts).
     */
    @NotBlank(message = "Fallback model must be set")
    private String fallbackModel = "gpt-4o-mini";

    // ── Response limits ───────────────────────────────────────────────────────

    /**
     * Maximum tokens the model can generate in its response.
     * Default: 1000 — enough for ~10-15 detailed review issues.
     *
     * Keep this low — you're asking for JSON, not an essay.
     * Higher values increase cost without improving review quality.
     *
     * Range: 100–4096
     */
    @Min(value = 100, message = "max-tokens must be at least 100")
    @Max(value = 4096, message = "max-tokens must not exceed 4096")
    private int maxTokens = 1000;

    /**
     * Maximum tokens in the prompt (input) per chunk.
     * Chunks exceeding this are split by ChunkingEngine.
     * Default: 6000 — leaves headroom for system prompt + response.
     *
     * GPT-4o context window: 128,000 tokens total.
     * We keep chunks small for focused, accurate reviews.
     */
    @Min(value = 1000, message = "max-prompt-tokens must be at least 1000")
    @Max(value = 20000, message = "max-prompt-tokens must not exceed 20000")
    private int maxPromptTokens = 6000;

    // ── Quality control ───────────────────────────────────────────────────────

    /**
     * Minimum confidence score (0–100) to include a finding.
     * Default: 70 — filters out low-confidence false positives.
     *
     * Applied by ConfidenceFilter after AIResponseParser runs.
     * Can be overridden per-repo in .codesense.yml.
     *
     * Tuning guide:
     *   50  → more comments, more false positives
     *   70  → balanced (recommended default)
     *   85  → fewer comments, very high precision
     *  100  → only include issues the AI is 100% sure about (rarely useful)
     */
    @Min(value = 0,   message = "confidence-threshold must be between 0 and 100")
    @Max(value = 100, message = "confidence-threshold must be between 0 and 100")
    private int confidenceThreshold = 70;

    /**
     * Temperature for the model (0.0–1.0).
     * Default: 0 — deterministic, consistent reviews across identical chunks.
     *
     * Do NOT increase this. Code review needs consistency —
     * the same code should produce the same findings every time.
     * Higher temperature = creative but inconsistent = unreliable reviews.
     */
    @DecimalMin(value = "0.0", message = "temperature must be between 0.0 and 1.0")
    @DecimalMax(value = "1.0", message = "temperature must be between 0.0 and 1.0")
    private double temperature = 0.0;

    // ── Reliability ───────────────────────────────────────────────────────────

    /**
     * HTTP timeout in seconds for OpenAI API calls.
     * Default: 60 seconds — GPT-4o can be slow on long prompts.
     *
     * Set on the RestClient used by OpenAIClient.
     * If a call takes longer than this, it throws a timeout exception
     * which ModelRouter catches and retries with the fallback model.
     */
    @Min(value = 10,  message = "timeout-seconds must be at least 10")
    @Max(value = 300, message = "timeout-seconds must not exceed 300")
    private int timeoutSeconds = 60;

    /**
     * Maximum retry attempts on transient failures.
     * Default: 3 — retries on 429 and 5xx errors.
     *
     * Managed by Resilience4j @Retry in OpenAIClient.
     * Each retry uses exponential backoff + jitter.
     *
     * Note: ModelRouter handles model-level fallback (gpt-4o → gpt-4o-mini).
     * This handles request-level retry within the same model.
     */
    @Min(value = 0, message = "max-retries must be at least 0")
    @Max(value = 5, message = "max-retries must not exceed 5")
    private int maxRetries = 3;

    /**
     * Base delay in milliseconds between retries.
     * Default: 1000ms (1 second).
     * Actual delay = retryDelayMs * (2 ^ attemptNumber) ± jitter.
     *
     * Attempt 1: ~1000ms
     * Attempt 2: ~2000ms
     * Attempt 3: ~4000ms
     */
    @Min(value = 100, message = "retry-delay-ms must be at least 100ms")
    private int retryDelayMs = 1000;

    // ── Cost control ──────────────────────────────────────────────────────────

    /**
     * Maximum tokens to spend per PR review (across all chunks).
     * Default: 50,000 — prevents runaway costs on massive PRs.
     *
     * ReviewOrchestrationService tracks token usage per session.
     * Once this limit is hit, remaining chunks are skipped and
     * a note is added to the PR review summary.
     *
     * At GPT-4o pricing (~$0.005/1K input tokens):
     *   50,000 tokens ≈ $0.25 per PR review maximum
     */
    @Min(value = 1000, message = "max-tokens-per-pr must be at least 1000")
    private int maxTokensPerPr = 50_000;

    /**
     * Monthly token budget across all repos.
     * Default: 0 (disabled — no monthly limit enforced).
     * When set,  tracks cumulative usage.
     *
     * Useful for controlling costs in production:
     *   5,000,000 tokens ≈ $25/month at GPT-4o pricing
     */
    @Min(value = 0, message = "monthly-token-budget must be non-negative")
    private int monthlyTokenBudget = 0;

    // ── Computed helpers ──────────────────────────────────────────────────────

    /**
     * The full chat completions endpoint URL.
     * Convenience method used by OpenAIClient — avoids
     * string concatenation scattered throughout the codebase.
     *
     * Returns: "https://api.openai.com/v1/chat/completions"
     */
    public String getChatCompletionsUrl() {
        return baseUrl + "/chat/completions";
    }

    /**
     * True if a monthly token budget has been configured.
     * Used by ReviewOrchestrationService to decide whether
     * to check budget before sending each chunk.
     */
    public boolean hasMonthlyBudget() {
        return monthlyTokenBudget > 0;
    }

    /**
     * The Authorization header value for OpenAI API calls.
     * Encapsulates the "Bearer " prefix so callers
     * never need to remember to add it.
     */
    public String getAuthorizationHeader() {
        return "Bearer " + apiKey;
    }

    /**
     * Safe string for logging — never reveals the actual API key.
     * Shows first 7 chars + masked rest:  "sk-proj-abc****"
     *
     * Called by startup logging and health checks.
     */
    public String getMaskedApiKey() {
        if (apiKey == null || apiKey.length() < 10) {
            return "***";
        }
        return apiKey.substring(0, 7) + "****";
    }
}
