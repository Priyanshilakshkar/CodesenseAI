package com.codeSense_Ai.Enties;

import com.codeSense_Ai.Enums.Roles;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.hibernate.validator.constraints.UniqueElements;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Users {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "github_id", nullable = false, unique = true)
    private Long githubId;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(length = 255)
    private String email;

    @Column(name = "display_name")
    private String displayName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /*
        Store encrypted token here
     */
    @Lob
    @Column(name = "github_access_token")
    private byte[] githubAccessToken;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Roles role = Roles.REVIEWER;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
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
