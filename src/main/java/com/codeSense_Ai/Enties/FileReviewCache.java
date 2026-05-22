package com.codeSense_Ai.Enties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;


@Entity
@Getter
@Setter
@Table(
        name = "file_review_cache",
        indexes = {
                @Index(name = "idx_file_hash", columnList = "file_hash", unique = true),
                @Index(name = "idx_created_at", columnList = "created_at") // for cleanup job
        }
)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileReviewCache {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The blobSha from GitHub — SHA-256 of the file's content
    // Unique constraint ensures one cached result per unique file content
    @Column(name = "file_hash", nullable = false, unique = true, length = 64)
    private String fileHash;

    // The filename at the time of review — stored for debugging/logging only
    // NOT part of the cache key — same content in differently-named files shares one entry
    @Column(name = "filename", nullable = false)
    private String filename;

    // The AI review result serialized as JSON
    // We store JSON (not separate columns) so the schema doesn't change if ReviewResult evolves
    @Column(name = "review_result_json", nullable = false, columnDefinition = "TEXT")
    private String reviewResultJson;

    // Which AI model produced this result — important for invalidation
    // If you upgrade from gpt-4o-mini to gpt-4o, old cache entries are still valid
    // but you may want to force re-review with the better model
    @Column(name = "model_used", nullable = false, length = 50)
    private String modelUsed;

    // How many tokens this review consumed — for cost analytics
    @Column(name = "tokens_used")
    private Integer tokensUsed;


    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_hit_at")
    private Instant lastHitAt; // updated each time this cache entry is used

    @Column(name = "hit_count")
    private int hitCount; // how many times this entry has saved an OpenAI call

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        lastHitAt = Instant.now();
    }


}
