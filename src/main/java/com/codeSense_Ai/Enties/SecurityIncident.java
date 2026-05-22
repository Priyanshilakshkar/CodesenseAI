package com.codeSense_Ai.Enties;
import com.codeSense_Ai.Enums.SecurityIncidentType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SecurityIncident {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_session_id", nullable = false)
    private ReviewSession reviewSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "review_comment_id")
    private ReviewComment reviewComment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityIncidentType incidentType;

    private String cweId;

    private BigDecimal cvssScore;

    private String filePath;

    private Integer lineNumber;

    @Lob
    @Column(nullable = false)
    private String description;

    @Lob
    private String remediation;

    private Boolean isAcknowledged = false;

    private LocalDateTime acknowledgedAt;

    private LocalDateTime createdAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
