package com.codeSense_Ai.Enties;

import com.codeSense_Ai.Enums.States;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_repo_pr_number",
                        columnNames = {"repository_id", "pr_number"}
                )
        },
        indexes = {
                @Index(name = "idx_state" , columnList = "states"),
                @Index(name = "idx_head_sha" , columnList = "headSha"),
                @Index(name = "idx_author" , columnList = "author"),
                @Index(name = "idx_github_created_at" , columnList = "githubCreatedAt")
        }

)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequests {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
        Repository Relationship
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "repository_id",
            nullable = false
    )
    private Repositories repository;

    @Column(nullable = false)
    private Long githubPrId;

    @Column(name = "pr_number", nullable = false)
    private Integer prNumber;

    @Column(nullable = false, length = 500)
    private String title;

    @Lob
    private String description;

    private Long authorGithubId;

    @Column(length = 100 , name = "author")
    private String authorUserName;

    @Column(nullable = false, length = 200)
    private String baseBranch;

    @Column(nullable = false, length = 200)
    private String headBranch;

    @Column(nullable = false, length = 40)
    private String baseSha;

    @Column(nullable = false, length = 40)
    private String headSha;

    @Column(length = 500)
    private String diffUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private States states = States.OPEN;

    @Column(nullable = false)
    private Integer filesChanged = 0;

    @Column(nullable = false)
    private Integer additions = 0;

    @Column(nullable = false)
    private Integer deletions = 0;

    private LocalDateTime githubCreatedAt;

    private LocalDateTime githubUpdatedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void onCreate() {

        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @PreUpdate
    public void onUpdate() {

        this.updatedAt = Instant.now();
    }


}
