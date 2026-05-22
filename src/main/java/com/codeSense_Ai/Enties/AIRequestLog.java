package com.codeSense_Ai.Enties;
import com.codeSense_Ai.Enums.AIRequestStatus;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIRequestLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_id", nullable = false)
    private ReviewSession reviewSession;

    @Column(nullable = false)
    private String model;

    private String filePath;

    private Integer chunkIndex;

    private Integer promptTokens = 0;

    private Integer completionTokens = 0;

    private Integer totalTokens = 0;

    private BigDecimal estimatedCostUsd;

    private Integer latencyMs;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AIRequestStatus status;

    private String errorCode;

    private String errorMessage;

    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt =Instant.now();
    }
}
