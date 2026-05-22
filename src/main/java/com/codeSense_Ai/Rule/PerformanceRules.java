package com.codeSense_Ai.Rule;

import com.codeSense_Ai.model.DiffLine;
import com.codeSense_Ai.model.ReviewIssue;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class PerformanceRules {
    private record PerformanceRule(
            Pattern pattern,
            int severity,
            String title,
            String body,
            String suggestion
    ) {}

    private static final List<PerformanceRule> RULES = List.of(

            // ── String concatenation in loop ──────────────────────────
            new PerformanceRule(
                    Pattern.compile("\\+=\\s*[\"']|\\+\\s*[\"']\\s*\\+",
                            Pattern.CASE_INSENSITIVE),
                    55,
                    "String concatenation may be inefficient",
                    "String concatenation with + creates a new String object each time. "
                            + "In a loop this causes excessive object allocation.",
                    "Use StringBuilder for string building in loops: "
                            + "sb.append(value) instead of str += value"
            ),

            // ── Thread.sleep in request path ──────────────────────────
            new PerformanceRule(
                    Pattern.compile("Thread\\.sleep\\(", Pattern.CASE_INSENSITIVE),
                    70,
                    "Blocking Thread.sleep() detected",
                    "Thread.sleep() blocks a thread entirely, wasting resources. "
                            + "In a web server context this reduces throughput.",
                    "Use scheduled tasks (@Scheduled) or reactive delays "
                            + "instead of Thread.sleep() in request-handling code."
            ),

            // ── Catching Exception broadly ────────────────────────────
            new PerformanceRule(
                    Pattern.compile("catch\\s*\\(Exception\\s+|catch\\s*\\(Throwable\\s+",
                            Pattern.CASE_INSENSITIVE),
                    45,
                    "Overly broad exception catch",
                    "Catching Exception or Throwable masks unexpected errors "
                            + "and makes debugging significantly harder.",
                    "Catch specific exception types. If a broad catch is "
                            + "intentional, add a comment explaining why."
            ),

            // ── System.out.println ────────────────────────────────────
            new PerformanceRule(
                    Pattern.compile("System\\.out\\.print|System\\.err\\.print",
                            Pattern.CASE_INSENSITIVE),
                    35,
                    "Debug print statement detected",
                    "System.out.println bypasses the logging framework, "
                            + "cannot be disabled per-environment, and impacts performance.",
                    "Replace with a Logger: log.info(), log.debug(), log.error()"
            ),

            // ── Creating SimpleDateFormat without static ──────────────
            new PerformanceRule(
                    Pattern.compile("new SimpleDateFormat\\(",
                            Pattern.CASE_INSENSITIVE),
                    50,
                    "SimpleDateFormat is not thread-safe",
                    "SimpleDateFormat instances are not thread-safe. "
                            + "Creating them inside methods causes excessive allocation; "
                            + "sharing them causes race conditions.",
                    "Use java.time.DateTimeFormatter (immutable and thread-safe) "
                            + "or ThreadLocal<SimpleDateFormat> if legacy code requires it."
            )
    );

    public List<ReviewIssue> scan(List<DiffLine> addedLines, String filePath) {
        List<ReviewIssue> findings = new ArrayList<>();

        for (DiffLine line : addedLines) {
            if (line.getNewLineNum() == null) continue;
            String content = line.content();

            for (PerformanceRule rule : RULES) {
                if (rule.pattern().matcher(content).find()) {
                    findings.add(new ReviewIssue(
                            line.getNewLineNum(),
                            rule.severity(),
                            100,
                            ReviewIssue.Category.PERFORMANCE,
                            rule.title(),
                            rule.body(),
                            rule.suggestion(),
                            "performance-rules",
                            filePath
                    ));
                    break;
                }
            }
        }

        return findings;
    }
}
