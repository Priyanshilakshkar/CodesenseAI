package com.codeSense_Ai.Service;

import com.codeSense_Ai.Chunking.*;
import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.DiffFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkingEngine {

    private final SemanticChunker semanticChunker;    // Task 03
    private final GenericChunkingStrategy genericStrategy; // Task 04
    private final OverlapStrategy overlapStrategy;    // Task 05
    private final ChunkPriotizer prioritizer;        // Task 06
    private final TokenEstimator tokenEstimator;     // Task 01
    // FunctionBoundaryDetector is injected into SemanticChunker  (Task 02)

    public List<CodeChunk> chunk(DiffFile diffFile) {
        // Step 1: Choose strategy based on language
        List<CodeChunk> rawChunks;
        String ext = diffFile.getExtension();

        if (List.of("java","py","js","ts").contains(ext)) {
            rawChunks = semanticChunker.chunk(diffFile);   // smart, language-aware
        } else {
            rawChunks = genericStrategy.chunk(diffFile);   // hunk-based fallback
        }

        // Step 2: Apply context overlap between chunks
        List<CodeChunk> overlappedChunks = overlapStrategy.applyOverlap(rawChunks);

        // Step 3: Sort by priority — security files first
        return prioritizer.prioritize(overlappedChunks);
    }
}
