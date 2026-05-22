package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.Dto.OpenAiResponse;
import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.RepoReviewConfig;
import com.codeSense_Ai.model.ReviewIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class AiResponseEngine {
    private final SystemPromtTemplate systemPromptTemplate;
    private final PromptBuilder        promptBuilder;
    private final ModelRouter          modelRouter;
    private final AiResponseParser     responseParser;
    private final ConfidenceFilter     confidenceFilter;

    /**

     * This is called once per chunk by ReviewOrchestrationService.
     *
     * @param chunk   The code chunk to review
     * @param config  Per-repo rules from .codesense.yml
     * @return        Filtered list of ReviewIssues — ready for GitHubReviewPoster
     */
    public List<ReviewIssue> review(CodeChunk chunk, RepoReviewConfig config) {
        // Guard: skip chunks with no actual changes
        if (chunk.hasNoChanges()) {
            log.debug("Skipping chunk {} — no changed lines", chunk.summary());
            return List.of();
        }

        log.info("AI reviewing: {}", chunk.summary());

        // Step 1: build the system prompt
        String systemPrompt = systemPromptTemplate.build(chunk.getLanguage());

        // Step 2: build the user prompt with actual code
        String userPrompt = promptBuilder.build(chunk, config);

        // Step 3: send to OpenAI via ModelRouter (with fallback)
        OpenAiResponse response = modelRouter.call(systemPrompt, userPrompt);

        // Step 4: parse the JSON response into ReviewIssue objects
        List<ReviewIssue> parsed = responseParser.parse(
                response, chunk.getChunkId(), chunk.getFilePath()
        );

        // Step 5: filter out low-confidence findings
        List<ReviewIssue> filtered = confidenceFilter.filter(parsed);

        log.info("AI review complete: {} → {} issues (model={}, tokens={})",
                chunk.filename(), filtered.size(),
                response.modelUsed(), response.totalTokens());

        return filtered;
    }
}
