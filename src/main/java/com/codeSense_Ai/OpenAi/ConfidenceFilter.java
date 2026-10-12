package com.codeSense_Ai.OpenAi;

import com.codeSense_Ai.model.ReviewIssue;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

import static reactor.netty.http.HttpConnectionLiveness.log;
@Component
public class ConfidenceFilter {
    @Value("${codesense.openai.confidence-threshold:70}")
    private int threshold;

    /**
     * Removes issues the AI isn't confident about.
     * Reduces false positives — developers ignore bots that
     * cry wolf on every minor thing.
     */
    public List<ReviewIssue> filter(List<ReviewIssue> issues) {
        List<ReviewIssue> confident = issues.stream()
                .filter(issue -> issue.confidence() >= threshold)
                .toList();

        int dropped = issues.size() - confident.size();
        if (dropped > 0) {
            log.debug("Confidence filter: kept {}/{} issues (threshold={})",
                    confident.size(), issues.size(), threshold);
        }

        return confident;

    }

}