package com.codeSense_Ai.Chunking;

import com.codeSense_Ai.Enums.DiffLineType;
import com.codeSense_Ai.model.CodeChunk;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
public class ChunkPriotizer {
    private static final List<String> SECURITY_PATH_PATTERNS = List.of(
            "auth", "security", "crypto", "password", "token",
            "permission", "role", "oauth", "jwt", "secret", "encrypt"
    );

    // Files containing these path segments are core business logic
    private static final List<String> CORE_LOGIC_PATTERNS = List.of(
            "service", "controller", "handler", "processor",
            "manager", "repository", "domain", "usecase"
    );

    // Files to deprioritise — important but lower review value
    private static final List<String> LOW_PRIORITY_PATTERNS = List.of(
            "test", "spec", "mock", "stub", "fixture",
            "config", "configuration", "properties", "dto",
            "README", "CHANGELOG", "docs"
    );

    /**
     * Sorts chunks by review priority — highest priority first.
     * The AI will review them in this order.
     */
    public List<CodeChunk> prioritize(List<CodeChunk> chunks) {
        return chunks.stream()
                .sorted(Comparator.comparingInt(this::calculatePriority).reversed())
                .toList();
    }

    /**
     * Calculates a priority score for a chunk.
     * Higher score = reviewed sooner.
     */
    private int calculatePriority(CodeChunk chunk) {
        String path = chunk.getFilePath().toLowerCase();
        int score = 0;

        // ── Tier 1: Security-sensitive files (highest priority) ──
        boolean isSecurity = SECURITY_PATH_PATTERNS.stream()
                .anyMatch(path::contains);
        if (isSecurity) score += 1000;

        // ── Tier 2: Core business logic ──────────────────────────
        boolean isCoreLogic = CORE_LOGIC_PATTERNS.stream()
                .anyMatch(path::contains);
        if (isCoreLogic) score += 500;

        // ── Tier 3: Most lines changed ───────────────────────────
        // More changes = more potential issues
        long changedLineCount = chunk.getLines().stream()
                .filter(line -> line.getType() != DiffLineType.CONTEXT)
                .count();
        score += (int) Math.min(changedLineCount * 10, 300); // cap at 300

        // ── Tier 4: Deprioritise low-value files ─────────────────
        boolean isLowPriority = LOW_PRIORITY_PATTERNS.stream()
                .anyMatch(path::contains);
        if (isLowPriority) score -= 200;

        // ── Tier 5: Larger chunks reviewed before tiny ones ──────
        // A 5000-token chunk has more review value than a 50-token chunk
        score += chunk.getEstimatedTokens() / 100;

        log.debug("Priority score for {}: {} (security={}, core={}, changes={}, low={})",
                chunk.getFilePath(), score, isSecurity, isCoreLogic, changedLineCount, isLowPriority);

        return score;
    }
}
