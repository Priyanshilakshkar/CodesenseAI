package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.Config.OpenAiProperties;
import com.codeSense_Ai.Dto.OpenAiResponse;
import com.codeSense_Ai.Exceptions.OpenAIException;
import com.codeSense_Ai.Exceptions.OpenAIRateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ModelRouter {
    private final OpenAiProperties properties;
    private final OpenAiClient openAIClient;

    /**
     * Tries the primary model first (gpt-4o).
     * On rate limit or server error, falls back to gpt-4o-mini.
     * Tracks which model was actually used for logging.
     */
    public OpenAiResponse call(String systemPrompt, String userPrompt) {
        String primary = properties.getPrimaryModel();  // gpt-4o
        String fallback = properties.getFallbackModel(); // gpt-4o-mini

        // ── Try primary model ────────────────────────────────────
        try {
            return openAIClient.call(systemPrompt, userPrompt, primary);

        } catch (OpenAIRateLimitException e) {
            // 429 — primary is rate limited, try fallback immediately
            log.warn("Primary model {} rate limited — falling back to {}",
                    primary, fallback);

        } catch (OpenAIException e) {
            // 500/503 — primary model error, try fallback
            log.warn("Primary model {} failed: {} — falling back to {}",
                    primary, e.getMessage(), fallback);
        }

        // ── Try fallback model ───────────────────────────────────
        try {
            log.info("Using fallback model: {}", fallback);
            return openAIClient.call(systemPrompt, userPrompt, fallback);

        } catch (Exception e) {
            // Both models failed — log and rethrow
            log.error("Both models failed. Primary={} Fallback={} Error={}",
                    primary, fallback, e.getMessage());
            throw new OpenAIException(
                    "All models failed — cannot complete AI review"
            );
        }

    }

    }
