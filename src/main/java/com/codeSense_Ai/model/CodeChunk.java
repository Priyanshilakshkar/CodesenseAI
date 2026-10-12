package com.codeSense_Ai.model;

import lombok.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Getter
@Setter
@Builder
@AllArgsConstructor
public class CodeChunk {


    // ── Identity ──────────────────────────────────────────────────────────────


    @Builder.Default
    private final String chunkId = UUID.randomUUID().toString();


    @Builder.Default
    private final int chunkIndex = 1;

    private String extension;
    @Builder.Default
    private final int totalChunks = 1;

    // ── Location ──────────────────────────────────────────────────────────────


    private final String filePath;


    private final String language;

    // ── Content ───────────────────────────────────────────────────────────────


    @With  // generates withLines(newLines) for OverlapStrategy
    private final List<DiffLine> lines;


    private final int estimatedTokens;

    // ── Strategy metadata ─────────────────────────────────────────────────────

    @Builder.Default
    private final ChunkStrategy strategy = ChunkStrategy.GENERIC;


    @Builder.Default
    private final boolean hasOverlap = false;


    @Builder.Default
    private final int priorityScore = 0;

    // ── Computed properties ───────────────────────────────────────────────────


    public List<DiffLine> changedLines() {
        return lines.stream()
                .filter(DiffLine::isChanged)
                .toList();
    }


    public List<DiffLine> contextLines() {
        return lines.stream()
                .filter(DiffLine::isContext)
                .toList();
    }


    public boolean hasNoChanges() {
        return changedLines().isEmpty();
    }


    public String lineRange() {

        Integer first = lines.stream()
                .map(DiffLine::getNewLineNum)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);

        Integer last = null;

        for (int i = lines.size() - 1; i >= 0; i--) {
            DiffLine line = lines.get(i);

            if (line.getNewLineNum() != null) {
                last = line.getNewLineNum();
                break;
            }
        }

        if (first == null || last == null) {
            return "unknown range";
        }

        return "Lines " + first + "-" + last;
    }


    public int firstDiffPosition() {
        return lines.stream()
                .mapToInt(DiffLine:: getDiffPosition)
                .min()
                .orElse(0);
    }

    public int lastDiffPosition() {
        return lines.stream()
                .mapToInt(DiffLine::getDiffPosition)
                .max()
                .orElse(0);
    }


    public String filename() {
        int slash = filePath.lastIndexOf('/');
        return slash == -1 ? filePath : filePath.substring(slash + 1);
    }


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
