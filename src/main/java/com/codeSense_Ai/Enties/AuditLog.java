package com.codeSense_Ai.Enties;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    private Long ActorId;

    @Column(nullable = false)
    private String action;

    private String entityType;

    private Long entityId;

    private String details;

    @Lob
    private String oldValueJson;

    @Lob
    private String newValueJson;

    private String ipAddress;

    private String userAgent;

    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
    }
}
