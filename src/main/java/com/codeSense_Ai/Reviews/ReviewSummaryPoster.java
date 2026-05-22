package com.codeSense_Ai.Reviews;

import com.codeSense_Ai.model.ReviewIssue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class ReviewSummaryPoster {
    public String buildSummary(ReviewAggregator.AggregatedReview aggregated,
                              CommentLimitEnforcer.EnforcedResult enforced,
                              ReviewCommentPoster.MappingResult mapped,
                              String            modelUsed,
                              int               tokensUsed) {
        StringBuilder sb = new StringBuilder();

        // ── Header ───────────────────────────────────────────────
        sb.append("## 🤖 CodeSense AI Review\n\n");

        // ── Overall verdict ───────────────────────────────────────
        int totalPosted = mapped.mappedCount();
        long critical   = aggregated.criticalCount();

        if (totalPosted == 0) {
            sb.append("✅ **No issues found.** ");
            sb.append("The changed code looks good!\n\n");
        } else if (critical > 0) {
            sb.append("🔴 **").append(critical)
                    .append(" critical issue").append(critical > 1 ? "s" : "")
                    .append(" found** — please review before merging.\n\n");
        } else {
            sb.append("🟡 **").append(totalPosted)
                    .append(" issue").append(totalPosted > 1 ? "s" : "")
                    .append(" found** — review inline comments below.\n\n");
        }

        // ── Stats table ───────────────────────────────────────────
        sb.append("| | Count |\n");
        sb.append("|---|---|\n");
        sb.append("| Files reviewed | ")
                .append(aggregated.affectedFiles().size()).append(" |\n");
        sb.append("| Issues found | ")
                .append(aggregated.totalIssues()).append(" |\n");
        sb.append("| Inline comments posted | ")
                .append(totalPosted).append(" |\n");

        if (mapped.unmappedCount() > 0) {
            sb.append("| Could not map to diff | ")
                    .append(mapped.unmappedCount()).append(" |\n");
        }
        if (enforced.hadOverflow()) {
            sb.append("| Dropped (over limit) | ")
                    .append(enforced.droppedCount()).append(" |\n");
        }

        sb.append("\n");

        // ── Category breakdown ────────────────────────────────────
        Map<ReviewIssue.Category, Long> breakdown = aggregated.countByCategory();
        if (!breakdown.isEmpty()) {
            sb.append("### Findings by Category\n\n");
            appendCategoryRow(sb, breakdown, ReviewIssue.Category.SECURITY,
                    "🔴 Security");
            appendCategoryRow(sb, breakdown, ReviewIssue.Category.BUG,
                    "🟠 Bug");
            appendCategoryRow(sb, breakdown, ReviewIssue.Category.PERFORMANCE,
                    "🟡 Performance");
            appendCategoryRow(sb, breakdown, ReviewIssue.Category.MAINTAINABILITY,
                    "⚪ Maintainability");
            appendCategoryRow(sb, breakdown, ReviewIssue.Category.STYLE,
                    "🔵 Style");
            sb.append("\n");
        }

        // ── Unmapped issues section ───────────────────────────────
        // Issues the AI found but couldn't be posted as inline comments
        // (line number wasn't in the diff) — listed here instead
        List<ReviewIssue> unmapped = mapped.unmappedIssues();
        if (!unmapped.isEmpty()) {
            sb.append("### Additional Findings\n\n");
            sb.append("*These issues could not be mapped to specific diff lines:*\n\n");
            unmapped.stream()
                    .limit(10) // cap at 10 in the summary
                    .forEach(issue -> {
                        sb.append("- **").append(issue.title()).append("**");
                        sb.append(" (`").append(issue.filePath()).append("`");
                        sb.append(" line ~").append(issue.line()).append(")\n");
                        sb.append("  ").append(issue.body()).append("\n");
                        if (issue.suggestion() != null && !issue.suggestion().isBlank()) {
                            sb.append("  *Suggestion:* ").append(issue.suggestion()).append("\n");
                        }
                        sb.append("\n");
                    });
        }

        // ── Overflow notice ───────────────────────────────────────
        if (enforced.hadOverflow()) {
            sb.append("> ⚠️ **").append(enforced.droppedCount())
                    .append(" additional issues were found but not posted** ")
                    .append("(comment limit reached). Increase `maxComments` in ")
                    .append("`.codesense.yml` to see all findings.\n\n");
        }

        // ── Footer ────────────────────────────────────────────────
        sb.append("---\n");
        sb.append("*Generated by [CodeSense AI](https://github.com/your-org/codesense) ");
        sb.append("· Model: `").append(modelUsed).append("`");
        sb.append(" · Tokens used: ").append(String.format("%,d", tokensUsed));
        sb.append("*\n");

        return sb.toString();
    }

    // ── Private helper ────────────────────────────────────────────────────────

    private void appendCategoryRow(StringBuilder sb,
                                   Map<ReviewIssue.Category, Long> breakdown,
                                   ReviewIssue.Category category,
                                   String label) {
        long count = breakdown.getOrDefault(category, 0L);
        if (count > 0) {
            sb.append("- ").append(label).append(": **").append(count).append("**\n");
        }
    }
}
