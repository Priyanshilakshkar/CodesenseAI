package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.model.CodeChunk;
import com.codeSense_Ai.model.ReviewIssue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
public class ReviewAggregator {

    public AggregatedReview aggregate(Map<CodeChunk , List<ReviewIssue>> chunkResults) {
        List<ReviewIssue> allIssues = chunkResults.values().stream()
                .flatMap(List::stream)
                .collect(Collectors.toList());

        log.info("Aggregating {} issues from {} chunks across {} files",
                allIssues.size(),
                chunkResults.size(),
                chunkResults.keySet().stream()
                        .map(CodeChunk::getFilePath)
                        .distinct().count()
        );

        Map<String, List<ReviewIssue>> byFile = allIssues.stream()
                .collect(Collectors.groupingBy(
                        ReviewIssue::filePath,
                        LinkedHashMap::new,
                        Collectors.toList()
                ));

        byFile.forEach((Files, issues) ->
                issues.sort(Comparator.comparingInt(ReviewIssue::severity).reversed())
        );

        Map<ReviewIssue.Category, Long> countByCategory = allIssues.stream()
                .collect(Collectors.groupingBy(
                        ReviewIssue::category,
                        Collectors.counting()
                ));
        long criticalCount = allIssues.stream().filter(
                ReviewIssue::isCritical
        ).count();

        log.info("Aggregation complete: {} files, {} issues ({} critical)",
                byFile.size(), allIssues.size(), criticalCount);

        return new AggregatedReview(byFile, countByCategory, allIssues.size(), criticalCount);

    }
    public record AggregatedReview(

            // Issues grouped by file — preserves per-file ordering
            Map<String, List<ReviewIssue>> issuesByFile,

            // Count per category — used in the PR review summary
            Map<ReviewIssue.Category, Long> countByCategory,

            // Total before deduplication
            int totalIssues,

            // Critical issues (severity >= 80) — highlighted in summary
            long criticalCount

    ) {
        /** Flat list of all issues across all files */
        public List<ReviewIssue> allIssues() {
            return issuesByFile.values().stream()
                    .flatMap(List::stream)
                    .toList();
        }

        /** All files that have at least one issue */
        public Set<String> affectedFiles() {
            return issuesByFile.keySet();
        }
    }
}
