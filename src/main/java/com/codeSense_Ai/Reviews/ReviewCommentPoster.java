package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.Dto.GitHubReviewComment;
import com.codeSense_Ai.model.DiffFile;
import com.codeSense_Ai.model.ReviewIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static reactor.netty.http.HttpConnectionLiveness.log;

@Component
@Slf4j
public class ReviewCommentPoster {

    public MappingResult buildComment(List<ReviewIssue> issues , Map<String , DiffFile> diffFilesByPath){
        List<GitHubReviewComment> validComments  = new ArrayList<>();
        List<ReviewIssue>         unmappedIssues = new ArrayList<>();

        for (ReviewIssue issue : issues) {
            // Get the DiffFile for this issue's file
            DiffFile diffFile = diffFilesByPath.get(issue.filePath());

            if (diffFile == null) {
                // File not in our parsed diffs — shouldn't happen, but guard it
                log.warn("No DiffFile found for path '{}' — skipping issue: {}",
                        issue.filePath(), issue.title());
                unmappedIssues.add(issue);
                continue;
            }

            // Map line number → diff position
            Integer diffPosition = diffFile.diffPositionForLine(issue.line());

            if (diffPosition == null) {
                // AI reported a line number not in the diff
                // This happens when the AI references context lines instead of changed lines
                log.debug("Line {} not in diff for '{}' — issue: {}",
                        issue.line(), issue.filePath(), issue.title());
                unmappedIssues.add(issue);
                continue;
            }

            // Build the GitHub review comment
            GitHubReviewComment comment = new GitHubReviewComment(
                    issue.filePath(),
                    diffPosition,
                    issue.formattedComment()  // markdown body from ReviewIssue
            );

            validComments.add(comment);
            log.debug("Mapped issue '{}' → file={} line={} diffPos={}",
                    issue.title(), issue.filePath(), issue.line(), diffPosition);
        }

        log.info("Comment mapping: {}/{} issues mapped to diff positions ({} unmapped)",
                validComments.size(), issues.size(), unmappedIssues.size());

        return new MappingResult(validComments, unmappedIssues);
    }

    public record MappingResult(
            // Issues successfully mapped → will be posted as inline comments
            List<GitHubReviewComment> comments,

            // Issues that couldn't be mapped → added to the review summary instead
            List<ReviewIssue> unmappedIssues
    ) {
        public int mappedCount()   { return comments.size(); }
        public int unmappedCount() { return unmappedIssues.size(); }
    }
}
