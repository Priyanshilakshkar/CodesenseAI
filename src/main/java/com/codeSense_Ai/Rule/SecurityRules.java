package com.codeSense_Ai.Rule;

import com.codeSense_Ai.model.DiffLine;
import com.codeSense_Ai.model.ReviewIssue;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class SecurityRules {
    /**
     * A security rule definition — pattern + severity + message.
     */
    private record SecurityRule(
            Pattern pattern,
            int severity,
            String title,
            String body,
            String suggestion
    ) {}

    // All built-in security rules
    private static final List<SecurityRule> RULES = List.of(

            // ── Hardcoded secrets ─────────────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "(password|passwd|pwd|secret|api_key|apikey|token|auth)\\s*=\\s*[\"'][^\"']{4,}[\"']",
                            Pattern.CASE_INSENSITIVE
                    ),
                    95,
                    "Hardcoded secret detected",
                    "A credential appears to be hardcoded in source code. "
                            + "Hardcoded secrets are extracted by attackers who gain "
                            + "read access to the repository or compiled artifact.",
                    "Move this to environment variables or a secrets manager "
                            + "(AWS Parameter Store, HashiCorp Vault). "
                            + "Never commit secrets to source control."
            ),

            // ── SQL injection ─────────────────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "(\"SELECT|\"INSERT|\"UPDATE|\"DELETE|\"DROP)[^\"]*\"\\s*\\+",
                            Pattern.CASE_INSENSITIVE
                    ),
                    90,
                    "Potential SQL injection vulnerability",
                    "String concatenation used to build a SQL query. "
                            + "If any concatenated value contains user input, "
                            + "this is exploitable via SQL injection.",
                    "Use parameterized queries or a query builder. "
                            + "Example: 'WHERE id = :id' with named parameters."
            ),

            // ── Dangerous deserialization ─────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "ObjectInputStream|readObject\\(|fromXML\\(|parseObject\\(",
                            Pattern.CASE_INSENSITIVE
                    ),
                    80,
                    "Potentially unsafe deserialization",
                    "Deserializing untrusted data can lead to remote code execution. "
                            + "Java deserialization is a well-known attack vector (CVE-2015-4852 etc.).",
                    "Avoid deserializing data from untrusted sources. "
                            + "Use safer formats like JSON with whitelisted types. "
                            + "If required, use SerialKiller or other deserialization filters."
            ),

            // ── XXE (XML External Entity) ─────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "DocumentBuilderFactory|SAXParserFactory|XMLInputFactory",
                            Pattern.CASE_INSENSITIVE
                    ),
                    75,
                    "Possible XML External Entity (XXE) vulnerability",
                    "XML parsers are vulnerable to XXE attacks by default "
                            + "unless external entity processing is explicitly disabled.",
                    "Disable external entities: "
                            + "factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) "
                            + "and factory.setFeature("
                            + "\"http://apache.org/xml/features/disallow-doctype-decl\", true)"
            ),

            // ── Path traversal ────────────────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "new File\\([^)]*\\+|Paths\\.get\\([^)]*\\+",
                            Pattern.CASE_INSENSITIVE
                    ),
                    75,
                    "Potential path traversal vulnerability",
                    "Building file paths with string concatenation can allow "
                            + "path traversal attacks (e.g. '../../etc/passwd') "
                            + "if any component comes from user input.",
                    "Validate and canonicalize paths before use: "
                            + "path.toRealPath().startsWith(allowedDir)"
            ),

            // ── System command execution ──────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "Runtime\\.getRuntime\\(\\)\\.exec|ProcessBuilder",
                            Pattern.CASE_INSENSITIVE
                    ),
                    70,
                    "System command execution detected",
                    "Executing system commands with user-controlled input "
                            + "can lead to command injection attacks.",
                    "Avoid passing user input to system commands. "
                            + "If necessary, use a whitelist of allowed commands "
                            + "and sanitize all inputs."
            ),

            // ── Weak cryptography ─────────────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "MD5|SHA-1|DES|RC4|getInstance\\(\"MD5|getInstance\\(\"SHA-1",
                            Pattern.CASE_INSENSITIVE
                    ),
                    65,
                    "Weak cryptographic algorithm",
                    "MD5 and SHA-1 are cryptographically broken and should not "
                            + "be used for security purposes.",
                    "Use SHA-256 or SHA-3 for hashing. "
                            + "For password hashing use BCrypt, Argon2, or PBKDF2."
            ),

            // ── Insecure random ───────────────────────────────────────
            new SecurityRule(
                    Pattern.compile(
                            "new Random\\(|Math\\.random\\(",
                            Pattern.CASE_INSENSITIVE
                    ),
                    60,
                    "Insecure random number generator",
                    "java.util.Random and Math.random() are not cryptographically "
                            + "secure and must not be used for tokens, passwords, or "
                            + "session IDs.",
                    "Use java.security.SecureRandom for all security-sensitive "
                            + "random number generation."
            )
    );

    /**
     * Scans added lines for security anti-patterns.
     * Returns ReviewIssue for each match found.
     * Only scans ADDED lines — already-existing code is not flagged.
     */
    public List<ReviewIssue> scan(List<DiffLine> addedLines, String filePath) {
        List<ReviewIssue> findings = new ArrayList<>();

        for (DiffLine line : addedLines) {
            if (line.getNewLineNum() == null) continue;
            String content = line.content();

            for (SecurityRule rule : RULES) {
                if (rule.pattern().matcher(content).find()) {
                    findings.add(new ReviewIssue(
                            line.getNewLineNum(),
                            rule.severity(),
                            100, // confidence = 100 for exact pattern matches
                            ReviewIssue.Category.SECURITY,
                            rule.title(),
                            rule.body(),
                            rule.suggestion(),
                            "security-rules",
                            filePath
                    ));
                    break; // one finding per line per rule pass
                }
            }
        }

        return findings;
    }
}
