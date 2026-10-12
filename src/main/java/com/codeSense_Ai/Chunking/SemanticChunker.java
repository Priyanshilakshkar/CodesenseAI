package com.codeSense_Ai.Chunking;

import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.DiffLine;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SemanticChunker {

    private final TokenEstimator tokenEstimator;
    private final GenericChunkingStrategy genericChunkingStrategy;
    private final FunctionBoundaryDetector BoundaryDetector;

    private static final int MAX_TOKENS_PER_CHUNK = 6000;

    public List<CodeChunk> chunk(DiffFile diffFile) {
        List<CodeChunk> result = new ArrayList<>();
        List<DiffLine> allLines = diffFile.allLines();
        String language = diffFile.getExtension();

        List<DiffLine> currentChunk = new ArrayList<>();
        int currentTokens = 0;

        int i = 0;
        while (i < allLines.size()) {
            DiffLine line = allLines.get(i);

            // Is this the start of a funcion?
            if (BoundaryDetector.isFunctionStart(line.content(), language)) {

                // Find where this function ends
                List<String> lineContents = allLines.stream()
                        .map(DiffLine::content).toList();
                int functionEnd = BoundaryDetector.FindFunctionEnd(lineContents, i, language);

                // Extract all lines belonging to this function
                List<DiffLine> functionLines = allLines.subList(i, functionEnd + 1);
                int functionTokens = tokenEstimator.estimateLines(
                        functionLines.stream().map(DiffLine::content).toList()
                );

                // Does the whole function fit in the current chunk?
                if (currentTokens + functionTokens <= MAX_TOKENS_PER_CHUNK) {
                    // Yes — add it to current chunk
                    currentChunk.addAll(functionLines);
                    currentTokens += functionTokens;
                } else {
                    // Current chunk has content — finalise it first
                    if (!currentChunk.isEmpty()) {
                        result.add(buildChunk(currentChunk, diffFile));
                        currentChunk = new ArrayList<>();
                        currentTokens = 0;
                    }

                    // Does the function alone fit in one chunk?
                    if (functionTokens <= MAX_TOKENS_PER_CHUNK) {
                        // Yes — start a fresh chunk with just this function
                        currentChunk.addAll(functionLines);
                        currentTokens = functionTokens;
                    } else {
                        // Function is too big even alone — fall back to hunk-based
                        List<CodeChunk> fallbackChunks = genericChunkingStrategy
                                .chunk(diffFile, functionLines);
                        result.addAll(fallbackChunks);
                    }
                }

                i = functionEnd + 1; // skip past this function
                continue;
            }

            // Not a function start — add line to current chunk
            int lineTokens = tokenEstimator.estimate(line.content());
            if (currentTokens + lineTokens > MAX_TOKENS_PER_CHUNK) {
                // Current chunk is full — finalise it
                result.add(buildChunk(currentChunk, diffFile));
                currentChunk = new ArrayList<>();
                currentTokens = 0;
            }

            currentChunk.add(line);
            currentTokens += lineTokens;
            i++;
        }

        // Don't forget the last chunk
        if (!currentChunk.isEmpty()) {
            result.add(buildChunk(currentChunk, diffFile));
        }

        return result;
    }

    private CodeChunk buildChunk(List<DiffLine> lines, DiffFile diffFile) {
        return CodeChunk.builder()
                .filePath(diffFile.getNewPath())
                .extension(diffFile.getExtension())
                .language(diffFile.getExtension())
                .lines(new ArrayList<>(lines))
                .estimatedTokens(tokenEstimator.estimateLines(
                        lines.stream().map(DiffLine::content).toList()))
                .build();
    }

}
