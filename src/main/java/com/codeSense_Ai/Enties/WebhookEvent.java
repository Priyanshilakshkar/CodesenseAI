package com.codeSense_Ai.Enties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String githubDeliveryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "repository_id")
    private Repositories repository;

    @Column(nullable = false)
    private String eventType;

    private String action;

    @Lob
    @Column(nullable = false)
    private String payloadJson;

    private Boolean signatureValid = false;

    private Boolean processed = false;

    private Integer processingAttempts = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_id")
    private ReviewSession reviewSession;

    private LocalDateTime receivedAt;

    private LocalDateTime processedAt;

    @PrePersist
    public void onCreate() {
        this.receivedAt = LocalDateTime.now();
    }
}
