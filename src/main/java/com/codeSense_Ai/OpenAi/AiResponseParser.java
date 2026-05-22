package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.Dto.OpenAiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.codeSense_Ai.model.ReviewIssue;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiResponseParser {

    private final ObjectMapper objectMapper;

    public List<ReviewIssue> parse(OpenAiResponse response , String chunkId ,String filePath){
        String raw = response.content();

        if (raw == null || raw.isBlank()) {
            log.warn("OpenAI returned empty content for chunk {}", chunkId);
            return List.of();
        }

        // Clean the response — model sometimes adds markdown fences
        // even in JSON mode (rare but happens with older model versions)
        String cleaned = stripMarkdownFences(raw);

        try{
            JsonNode root = objectMapper.readTree(cleaned);

            // Validate top-level structure
            if (!root.has("issues")) {
                log.warn("OpenAI response missing 'issues' field: {}", abbreviated(cleaned));
                return List.of();
            }
            JsonNode issuesNode = root.get("issues");
            if (!issuesNode.isArray()) {
                log.warn("OpenAI 'issues' field is not an array");
                return List.of();
            }
            List<ReviewIssue> issues = new ArrayList<>();
            for (JsonNode issueNode : issuesNode) {

                parseIssue(issueNode, chunkId, filePath)
                        .ifPresent(issue -> issues.add(issue));
            }

            log.debug("Parsed {} issues from OpenAI response for chunk {}",
                    issues.size(), chunkId);
            return issues;

        }catch (Exception e) {
            log.error("Failed to parse OpenAI response for chunk {}: {} | raw={}",
                    chunkId, e.getMessage(), abbreviated(cleaned));
            return List.of(); // never propagate parsing failures
        }

    }
    private java.util.Optional<com.codeSense_Ai.model.ReviewIssue> parseIssue(JsonNode node,
                                                                              String chunkId,
                                                                              String filePath) {
        try {
            // Extract and validate required fields
            int line       = getIntOrDefault(node, "line", 0);
            int severity   = clamp(getIntOrDefault(node, "severity", 0), 0, 100);
            int confidence = clamp(getIntOrDefault(node, "confidence", 0), 0, 100);
            String title   = getStringOrDefault(node, "title", "Untitled issue");
            String body    = getStringOrDefault(node, "body", "");
            String suggestion = getStringOrDefault(node, "suggestion", "");

            // Parse category safely
            com.codeSense_Ai.model.ReviewIssue.Category category = parseCategory(
                    getStringOrDefault(node, "category", "BUG")
            );

            // Validate: line must be a positive integer
            if (line <= 0) {
                log.debug("Skipping issue with invalid line number: {}", line);
                return java.util.Optional.empty();
            }

            // Validate: title and body must be present
            if (title.isBlank() || body.isBlank()) {
                log.debug("Skipping issue with empty title or body");
                return java.util.Optional.empty();
            }

            return java.util.Optional.of(new com.codeSense_Ai.model.ReviewIssue(
                    line, severity, confidence, category,
                    title, body, suggestion, chunkId, filePath
            ));

        } catch (Exception e) {
            log.warn("Skipping malformed issue node: {}", e.getMessage());
            return java.util.Optional.empty();
        }
    }

    private com.codeSense_Ai.model.ReviewIssue.Category parseCategory(String raw) {
        try {
            return com.codeSense_Ai.model.ReviewIssue.Category.valueOf(raw.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return com.codeSense_Ai.model.ReviewIssue.Category.BUG; // safe default
        }
    }

    private String stripMarkdownFences(String text) {
        String trimmed = text.trim();
        // Strip ```json ... ``` or ``` ... ```
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("```(?:json)?\\s*", "");
            if (trimmed.endsWith("```")) {
                trimmed = trimmed.substring(0, trimmed.lastIndexOf("```")).trim();
            }
        }
        return trimmed;
    }

    private int getIntOrDefault(JsonNode node, String field, int def) {
        return node.has(field) && node.get(field).isNumber()
                ? node.get(field).asInt() : def;
    }

    private String getStringOrDefault(JsonNode node, String field, String def) {
        return node.has(field) && node.get(field).isTextual()
                ? node.get(field).asText() : def;
    }

    private int clamp(int value, int min, int max) {
        return Math.min(max, Math.max(min, value));
    }

    private String abbreviated(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "..." : s;
    }
}
