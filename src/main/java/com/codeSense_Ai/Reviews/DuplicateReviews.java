package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.model.ReviewIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class DuplicateReviews {
    private static final int LINE_PROXIMITY_THRESHOLD = 3;

    // Title similarity above this = duplicate (Jaccard similarity)
    private static final double TITLE_SIMILARITY_THRESHOLD = 0.7;

    public List<ReviewIssue> Duplicates(ReviewAggregator.AggregatedReview aggregated){
        List<ReviewIssue> deduplicated = new ArrayList<>();
        int duplicatedIssue = 0;

        for(Map.Entry<String , List<ReviewIssue>> entry : aggregated.issuesByFile().entrySet()){
            String filepath = entry.getKey();
            List<ReviewIssue> issues = entry.getValue();

            List<ReviewIssue> unique = dedulicatedFiles(issues);
            int removed  = issues.size() - unique.size();
            duplicatedIssue += removed;

            if (removed > 0) {
                log.debug("Removed {} duplicates from {}", removed, filepath);
            }

            deduplicated.addAll(unique);
        }

        deduplicated.sort(Comparator.comparingInt(ReviewIssue::severity).reversed());

        log.info("Deduplication complete: {} → {} issues ({} duplicates removed)",
                aggregated.totalIssues(), deduplicated.size(), duplicatedIssue);

        return deduplicated;
    }
    private List<ReviewIssue> dedulicatedFiles(List<ReviewIssue> files){

        List<ReviewIssue> unique = new ArrayList<>();

        for(ReviewIssue candidates : files){
            boolean isDuplicate = unique.stream()
                    .anyMatch(existing -> areDuplicates(existing , candidates));

            if(!isDuplicate){
                unique.add(candidates);
            }
        }

        return unique;

    }
    private boolean areDuplicates(ReviewIssue a, ReviewIssue b){
        if(a.category() != b.category()) return false;

        int lineDiff = Math.abs(a.line() - b.line());
        if(lineDiff> LINE_PROXIMITY_THRESHOLD) return false;

        double similarity = jaccardSimilarity(
                tokenize(a.title()) , tokenize(b.title())
        );
        if(similarity > TITLE_SIMILARITY_THRESHOLD){
            return false;
        }

        log.debug("Duplicate detected: '{}' (line {}) ≈ '{}' (line {}) similarity={:.2f}",
                a.title(), a.line(), b.title(), b.line(), similarity);

        return true;
    }
    private double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) return 1.0;
        if (a.isEmpty() || b.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);

        Set<String> union = new HashSet<>(a);
        union.addAll(b);

        return (double) intersection.size() / union.size();
    }

    /** Split title into lowercase word tokens for comparison */
    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) return Set.of();
        return Arrays.stream(text.toLowerCase()
                        .replaceAll("[^a-z0-9\\s]", " ") // remove punctuation
                        .split("\\s+"))
                .filter(word -> word.length() > 2) // skip short words
                .collect(Collectors.toSet());
    }
}
