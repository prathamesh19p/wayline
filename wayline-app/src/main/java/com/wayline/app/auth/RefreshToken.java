package com.wayline.app.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens", indexes = {
    @Index(name = "idx_refresh_tokens_family", columnList = "family_id"),
    @Index(name = "idx_refresh_tokens_expiry", columnList = "expires_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(nullable = false, length = 100)
    private String username;

    @Column(nullable = false, length = 40)
    private String role;

    @Column(nullable = false, length = 36)
    private String familyId;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    @Default
    private Boolean revoked = false;

    private Instant revokedAt;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    @Version
    private Long version;
}
