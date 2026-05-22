package com.codeSense_Ai.Enties;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(indexes = {
        @Index(name="idx_owner" , columnList = "owner"),
        @Index(name = "idx_is_active" , columnList = "isActive"),
        @Index(name = "ids_language" , columnList = "language")
})
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Repositories {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @Column(nullable = false , unique = true)
    private Long githubRepoId;

    @Column(nullable = false , length = 100)
    private String owner;

    @Column(nullable = false , length = 300)
    private String name;

    @Column(nullable = false , length = 300 , unique = true)
    private String fullName;

    private Long installation_id;

    @Lob
    @Column( nullable = false)
    private String webhookSecret;

    @Column(nullable = false )
    private boolean isActive = true;

    @Column(nullable = false , length = 100)
    private String defaultBranch = "main";

    @Column(length = 50)
    private String language;

    @Column(nullable = false)
    private int totalReviews = 0;

    private LocalDateTime last_review_at;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "createdBy")
    private Users user;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }


}
