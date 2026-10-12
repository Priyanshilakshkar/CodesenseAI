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

        if (diffFile == null) {
            throw new IllegalArgumentException("DiffFile cannot be null");
        }

        List<CodeChunk> rawChunks;

        String ext = diffFile.getExtension();

        if (ext != null) {
            ext = ext.toLowerCase();
        }

        log.info(
                "Chunking file: oldPath={}, newPath={}, extension={}, hunks={}",
                diffFile.getOldPath(),
                diffFile.getNewPath(),
                ext,
                diffFile.getHunksList() != null
                        ? diffFile.getHunksList().size()
                        : 0
        );

        if (List.of("java", "py", "js", "ts").contains(ext)) {

            log.info("Using SemanticChunker for extension '{}'", ext);

            rawChunks = semanticChunker.chunk(diffFile);

        } else {

            log.info("Using GenericChunkingStrategy for extension '{}'", ext);

            rawChunks = genericStrategy.chunk(diffFile);
        }

        List<CodeChunk> overlappedChunks =
                overlapStrategy.applyOverlap(rawChunks);

        return prioritizer.prioritize(overlappedChunks);
    }
}
