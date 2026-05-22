package com.codeSense_Ai.Chunking;

import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.DiffHunks;
import com.codeSense_Ai.model.DiffLine;
import jakarta.validation.constraints.Email;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;


@Component
@RequiredArgsConstructor
public class GenericChunkingStrategy {

    private final TokenEstimator tokenEstimator;
    private static final int MAX_TOKENS_PER_CHUNK = 6000;

    public List<CodeChunk> chunk(DiffFile diffFile) {
        return chunk(diffFile, null); // null = use all hunks
    }

    public List<CodeChunk> chunk(DiffFile diffFile , List<DiffLine> lines){
        List<DiffHunks> hunks = diffFile.getHunksList();
        List<CodeChunk> result = new ArrayList<>();

        List<DiffLine> currentLines = new ArrayList<>();
        int currentToken  = 0;

        for(DiffHunks hunks1 : hunks){
            List<String> hunkContents = hunks1.getLines().stream().map(DiffLine::getContent).toList();

            int  hunkTokens= tokenEstimator.estimateLines(hunkContents);

            if(currentToken + hunkTokens <= MAX_TOKENS_PER_CHUNK){
                currentLines.addAll(hunks1.getLines());
                currentToken+= hunkTokens;
            }
            else {
                if(!currentLines.isEmpty()){
                    result.add(buildChunk(currentLines , diffFile));
                    currentLines = new ArrayList<>();
                    currentToken = 0;
                }
                if(hunkTokens > MAX_TOKENS_PER_CHUNK){
                    List<CodeChunk> chunks = SplitOversizedChunk(hunks1 , diffFile);
                    result.addAll(chunks);

                }
                else{
                    currentLines.addAll(hunks1.getLines());
                    currentToken = hunkTokens;
                }
            }



        }

        return result;


    }
    public CodeChunk buildChunk(List<DiffLine> currentLines , DiffFile file){
        return CodeChunk.builder()
                .filePath(file.getNewPath())
                .extension(file.getExtension())
                .lines(new ArrayList<>(currentLines))
                .estimatedTokens(tokenEstimator.estimateLines(
                        currentLines.stream().map(DiffLine::getContent).toList()
                ))
                .build();
    }

    public List<CodeChunk> SplitOversizedChunk(DiffHunks hunk , DiffFile file){
        List<CodeChunk> result = new ArrayList<>();
        List<DiffLine> currentLines = new ArrayList<>();

        int currentToken = 0;

        for(DiffLine line : hunk.getLines()){
            int hunkTokens = tokenEstimator.estimate(line.getContent());

            if(currentToken + hunkTokens > MAX_TOKENS_PER_CHUNK && !currentLines.isEmpty()){
                result.add(buildChunk(currentLines , file));
                currentLines = new ArrayList<>();
                currentToken = 0;
            }

            currentLines.add(line);
            currentToken+=hunkTokens;
        }

        return result;
    }

}
