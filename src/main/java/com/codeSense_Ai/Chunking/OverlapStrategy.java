package com.codeSense_Ai.Chunking;

import com.codeSense_Ai.Enums.DiffLineType;
import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.DiffLine;

import java.util.ArrayList;
import java.util.List;

public class OverlapStrategy {
    private static final int OVERLAP_LINES = 15;

    /**
     * Applies context overlap to a list of chunks.
     * Each chunk (except the first) gets the last 15 lines
     * of the previous chunk prepended as context.
     *
     * Called by ChunkingEngine after chunks are created.
     */
    public List<CodeChunk> applyOverlap(List<CodeChunk> chunks) {
        if (chunks.size() <= 1) return chunks; // nothing to overlap

        List<CodeChunk> result = new ArrayList<>();
        result.add(chunks.get(0)); // first chunk is unchanged

        for (int i = 1; i < chunks.size(); i++) {
            CodeChunk previous = chunks.get(i - 1);
            CodeChunk current = chunks.get(i);

            // Take the last N lines of the previous chunk
            List<DiffLine> previousLines = previous.getLines();
            int overlapStart = Math.max(0, previousLines.size() - OVERLAP_LINES);
            List<DiffLine> overlapLines = previousLines.subList(overlapStart, previousLines.size());

            // Mark overlap lines as context so the AI knows they're
            // repeated for reference — not new changes to review
            List<DiffLine> markedOverlap = overlapLines.stream()
                    .map(line -> new DiffLine(
                            DiffLineType.CONTEXT, // treat as context regardless of original type
                            "// [context from previous chunk] " + line.content(),
                            line.getOldLineNum(),
                            line.getNewLineNum(),
                            line.getDiffPosition()
                    ))
                    .toList();

            // Prepend overlap to current chunk's lines
            List<DiffLine> overlappedLines = new ArrayList<>();
            overlappedLines.addAll(markedOverlap);
            overlappedLines.addAll(current.getLines());

            result.add(current.withLines(overlappedLines));
        }

        return result;

    }
}
