package com.codeSense_Ai.Dto;

public record OpenAiResponse(
        String content,           // the raw JSON string from the model
        int promptTokens,         // tokens used for input
        int completionTokens,     // tokens used for output
        String modelUsed
) {
    public int totalTokens() {
        return promptTokens + completionTokens;
    }
}
