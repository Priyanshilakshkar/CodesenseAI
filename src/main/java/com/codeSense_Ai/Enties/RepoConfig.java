package com.codeSense_Ai.Enties;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "repo_config")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepoConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Repository this config belongs to
     */
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "repository_id",
            nullable = false,
            unique = true
    )
    private Repositories repository;

    // ── Review Settings ───────────────────────────────────────

    private Integer maxComments;

    private String model;

    private Integer confidenceThreshold;

    private Integer minimumSeverity;

    // ── JSON Stored Fields ────────────────────────────────────
    // Stored as JSON strings in DB

    @Column(columnDefinition = "TEXT")
    private String ignoreFilesJson;

    @Column(columnDefinition = "TEXT")
    private String onlyFilesJson;

    @Column(columnDefinition = "TEXT")
    private String alwaysFlagJson;

    @Column(columnDefinition = "TEXT")
    private String requirePatternsJson;

    @Column(columnDefinition = "TEXT")
    private String ignoreCategoriesJson;

    // ── Metadata ──────────────────────────────────────────────

    private Boolean enabled;

    private String updatedBy;

    private Instant createdAt;

    private Instant updatedAt;
}
