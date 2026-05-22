package com.codeSense_Ai.Chunking;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

@Component
public class TokenEstimator {
    private final int CHARS_PER_TOKEN = 4;

    public int estimate(String text){
        if(text == null || text.isEmpty()) return 0;
        return (int) Math.ceil((double) text.length() / CHARS_PER_TOKEN);
    }

    public int estimateLines(List<String> lines){
        int totalChars = lines.stream()
                .mapToInt(line -> line.length() +1)
                .sum();
        return (int) Math.ceil(totalChars / CHARS_PER_TOKEN);

    }

    public boolean wouldExceedLimit(int currentTokens, String textToAdd, int limit) {
        return (currentTokens + estimate(textToAdd)) > limit;
    }
}
