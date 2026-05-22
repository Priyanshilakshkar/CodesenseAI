package com.codeSense_Ai.Enties;
import com.codeSense_Ai.Enums.ReviewCategory;
import com.codeSense_Ai.Enums.Severity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReviewComment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_id", nullable = false)
    private ReviewSession reviewSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pull_request_id", nullable = false)
    private PullRequests pullRequest;

    private Long githubCommentId;

    @Column(nullable = false, length = 500)
    private String filePath;

    @Column(name = "diff_position")
    private Integer diffPosition;

    private Integer lineNumber;

    private Integer originalLine;

    private Integer newLine;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReviewCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Severity severity;

    @Column(nullable = false)
    private Integer confidence = 80;

    @Column(nullable = false, length = 300)
    private String title;

    @Lob
    @Column(nullable = false)
    private String body;

    @Lob

    private String codeSnippet;

    @Column(name = "source", length = 30)  // "ai", "security-rules", "rule-engine"
    private String source;


    @Lob
    private String suggestion;

    private String ruleId;

    private Boolean isPosted = false;

    private Integer postAttempts = 0;

    private Integer chunkIndex;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

}
