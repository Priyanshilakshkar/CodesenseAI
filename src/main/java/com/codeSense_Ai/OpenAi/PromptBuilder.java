package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.DiffLine;
import com.codeSense_Ai.model.RepoReviewConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PromptBuilder {

    public String build(CodeChunk chunk , RepoReviewConfig config){
        StringBuilder prompt = new StringBuilder();

        prompt.append("## File Under Review\n");
        prompt.append("**Path:**` ").append(chunk.getFilePath()).append("`\n");
        prompt.append("**language:** ").append(chunk.getLanguage()).append("`\n");
        prompt.append("**Scope:** Chunk").append(chunk.getChunkIndex())
                .append(" of ").append(chunk.getTotalChunks())
                .append(" - ").append(chunk.lineRange()).append("\n");
        prompt.append("**Changes** ")
                .append(chunk.changedLines().size()).append("lines changed\n");

        if (chunk.isHasOverlap()) {
            prompt.append("**Note:** Lines marked `[context]` are from the ");
        }

        if(config != null && config.hasCustomRules()){
            prompt.append("\n ##Repository Specific Rules##");
            prompt.append("Apply these rules in addition to Standard Review\n");
            config.getAlwaysFlag().forEach(rule->
                    prompt.append(" -Flag: ").append(rule).append("\n")
                    );
            if(config.getRequirePatterns()!=null){
                config.getRequirePatterns().forEach(pattern ->
                        prompt.append(" -Require: ").append(pattern).append("\n")
                        );
            }
        }

        prompt.append("\n ##Code Diff## \n");
        prompt.append("Legend- `+` = ADDED (review these) ,").append(" `-` = removed  , ` ` CONTEXT  unchanged lines ");
        prompt.append("```Diff\n");
        prompt.append(buildDiffBlock(chunk.getLines()));
        prompt.append("\n```\n");

        prompt.append("\n## Line Number Reference\n");
        prompt.append("Use these line numbers in your response:\n");
        chunk.getLines().stream()
                .filter(line -> line.isAdded() && line.getNewLineNum() != null)
                .limit(20) // only show first 20 to keep prompt short
                .forEach(line ->
                        prompt.append("- Line ").append(line.getNewLineNum())
                                .append(": `").append(abbreviated(line.content())).append("`\n")
                );

        prompt.append("\nReview the ADDED lines (+) in the diff above. ")
                .append("Respond with JSON only.");

        log.debug("Built prompt for {} chunk {}/{}: ~{} chars",
                chunk.filename(), chunk.getChunkIndex(), chunk.getTotalChunks(),
                prompt.length());

        return prompt.toString();





}

     public String buildDiffBlock(List<DiffLine> lines){
         StringBuilder block = new StringBuilder();
         for (DiffLine line : lines) {
             String prefix = switch (line.getType()) {
                 case ADDED   -> "+";
                 case REMOVED -> "-";
                 case CONTEXT -> " ";
             };

             // Add line number annotation for ADDED lines so AI can reference them
             String lineNum = line.getNewLineNum() != null
                     ? String.format("%4d", line.getNewLineNum())
                     : "    ";

             block.append(prefix)
                     .append(lineNum).append(" ")
                     .append(line.content()).append("\n");
         }
         return block.toString();
    }

    private String abbreviated(String content) {
        String trimmed = content.trim();
        return trimmed.length() > 60
                ? trimmed.substring(0, 57) + "..."
                : trimmed;
    }
}
