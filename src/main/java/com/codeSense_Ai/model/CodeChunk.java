package com.codeSense_Ai.model;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CodeChunk {


    // ── Identity ──────────────────────────────────────────────────────────────

    /**
     * Unique ID for this chunk — used to correlate AI responses
     * back to the chunk that produced them, especially when
     * chunks are sent to OpenAI in parallel.
     *
     * Generated at build time — never null.
     */
    @Builder.Default
    private final String chunkId = UUID.randomUUID().toString();

    /**
     * Which chunk this is within the file — 1-based.
     * "Chunk 2 of 5" helps the AI understand it's seeing
     * a partial file, not the whole thing.
     */
    @Builder.Default
    private final int chunkIndex = 1;

    /**
     * Total number of chunks this file was split into.
     * Sent to AI in the prompt: "You are reviewing chunk 2 of 5."
     */
    @Builder.Default
    private final int totalChunks = 1;

    // ── Location ──────────────────────────────────────────────────────────────

    /**
     * Full file path within the repository.
     * e.g. "src/main/java/com/codesense/service/UserService.java"
     *
     * Used by:
     * - PromptBuilder: tells the AI which file it's reviewing
     * - GitHubReviewPoster: needed for the GitHub API comment payload
     * - ChunkPrioritizer: path patterns determine security/core-logic score
     */
    private final String filePath;

    /**
     * File extension, lowercase — derived from filePath.
     * e.g. "java", "py", "ts", "go"
     *
     * Used by:
     * - PromptBuilder: tells AI which language to apply rules for
     * - ChunkingEngine: chose the chunking strategy based on this
     */
    private final String language;

    // ── Content ───────────────────────────────────────────────────────────────

    /**
     * The actual lines of code in this chunk — in order.
     *
     * Includes:
     * - ADDED lines   (prefixed + in raw diff)
     * - REMOVED lines (prefixed - in raw diff)
     * - CONTEXT lines (surrounding unchanged code, shown for reference)
     * - Overlap lines (repeated from previous chunk, marked as context)
     *
     * PromptBuilder iterates this list to build the code block
     * sent to OpenAI, preserving +/- markers so the AI knows
     * what changed.
     */
    @With  // generates withLines(newLines) for OverlapStrategy
    private final List<DiffLine> lines;

    /**
     * Estimated token count for this chunk's content.
     * Calculated by TokenEstimator during chunking.
     *
     * Guaranteed to be <= 6000 (MAX_TOKENS_PER_CHUNK).
     * Used by AIReviewEngine to pick the right model
     * and track token usage per ReviewSession.
     */
    private final int estimatedTokens;

    // ── Strategy metadata ─────────────────────────────────────────────────────

    /**
     * Which chunking strategy produced this chunk.
     * Stored for debugging and analytics — helps identify
     * if the fallback strategy is being used too often.
     */
    @Builder.Default
    private final ChunkStrategy strategy = ChunkStrategy.GENERIC;

    /**
     * True if this chunk contains overlap lines prepended
     * from the previous chunk by OverlapStrategy.
     * Logged in ReviewSession for debugging context issues.
     */
    @Builder.Default
    private final boolean hasOverlap = false;

    /**
     * Priority score assigned by ChunkPrioritizer.
     * Higher = reviewed sooner by the AI engine.
     * 0 = not yet scored (before prioritization step).
     */
    @Builder.Default
    private final int priorityScore = 0;

    // ── Computed properties ───────────────────────────────────────────────────

    /**
     * Only the lines that represent actual changes —
     * ADDED and REMOVED, not CONTEXT or overlap.
     *
     * Used by AIReviewEngine when building prompts:
     * the AI is told "these are the changed lines" vs
     * "these are context lines for reference."
     */
    public List<DiffLine> changedLines() {
        return lines.stream()
                .filter(DiffLine::isChanged)
                .toList();
    }

    /**
     * Only context lines — unchanged surrounding code
     * included for the AI's reference.
     */
    public List<DiffLine> contextLines() {
        return lines.stream()
                .filter(DiffLine::isContext)
                .toList();
    }

    /**
     * True if this chunk has no actual changes —
     * only context/overlap lines. This shouldn't happen
     * normally, but guards against sending empty reviews to OpenAI.
     */
    public boolean hasNoChanges() {
        return changedLines().isEmpty();
    }

    /**
     * The line number range this chunk covers in the new file.
     * e.g. "Lines 15-67"
     *
     * Built from newLineNumber of first and last non-null lines.
     * Used in the AI prompt header so the model knows
     * where in the file this chunk sits.
     */
    public String lineRange() {
        Integer first = lines.stream()
                .map(DiffLine::newLineNumber)
                .filter(n -> n != null)
                .findFirst()
                .orElse(null);

        Integer last = null;
        List<DiffLine> reversed = lines.reversed();
        for (DiffLine line : reversed) {
            if (line.newLineNumber() != null) {
                last = line.newLineNumber();
                break;
            }
        }

        if (first == null || last == null) return "unknown range";
        return "Lines " + first + "-" + last;
    }


    public int firstDiffPosition() {
        return lines.stream()
                .mapToInt(DiffLine::diffPosition)
                .min()
                .orElse(0);
    }

    public int lastDiffPosition() {
        return lines.stream()
                .mapToInt(DiffLine::diffPosition)
                .max()
                .orElse(0);
    }

    /**
     * Just the filename without directory path.
     * Used in log messages and AI prompt summaries.
     * e.g. "UserService.java" from the full path above.
     */
    public String filename() {
        int slash = filePath.lastIndexOf('/');
        return slash == -1 ? filePath : filePath.substring(slash + 1);
    }

    /**
     * Human-readable summary — used in log messages.
     * e.g. "UserService.java chunk 2/5 [Lines 45-112] ~840 tokens SEMANTIC"
     */
    public String summary() {
        return String.format("%s chunk %d/%d [%s] ~%d tokens %s%s",
                filename(), chunkIndex, totalChunks,
                lineRange(), estimatedTokens, strategy,
                hasOverlap ? " +overlap" : ""
        );
    }

    // ── Enums ─────────────────────────────────────────────────────────────────

    public enum ChunkStrategy {
        SEMANTIC,  // SemanticChunker — kept whole functions together
        GENERIC,   // GenericChunkingStrategy — split at hunk boundaries
        LINE_SPLIT // last resort — oversized hunk split line by line
    }

}
