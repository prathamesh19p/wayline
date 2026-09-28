package com.wayline.app.auth;

import com.wayline.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final RefreshTokenRepository repository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${wayline.security.jwt.refresh-expiration:30d}")
    private java.time.Duration refreshLifetime;

    @Transactional
    public TokenPair issue(String username, String role) {
        String rawRefreshToken = newRefreshToken();
        saveToken(rawRefreshToken, username, role, UUID.randomUUID().toString());
        return pair(username, role, rawRefreshToken);
    }

    @Transactional
    public Optional<TokenPair> rotate(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = repository.findByHashForUpdate(hash(rawRefreshToken));
        if (found.isEmpty()) {
            return Optional.empty();
        }

        RefreshToken current = found.get();
        if (Boolean.TRUE.equals(current.getRevoked())) {
            revokeFamily(current.getFamilyId());
            return Optional.empty();
        }
        if (!current.getExpiresAt().isAfter(Instant.now())) {
            current.setRevoked(true);
            current.setRevokedAt(Instant.now());
            repository.save(current);
            return Optional.empty();
        }

        current.setRevoked(true);
        current.setRevokedAt(Instant.now());
        repository.save(current);

        String replacement = newRefreshToken();
        saveToken(replacement, current.getUsername(), current.getRole(), current.getFamilyId());
        return Optional.of(pair(current.getUsername(), current.getRole(), replacement));
    }

    private void saveToken(String rawToken, String username, String role, String familyId) {
        repository.save(RefreshToken.builder()
            .tokenHash(hash(rawToken))
            .username(username)
            .role(role)
            .familyId(familyId)
            .expiresAt(Instant.now().plus(refreshLifetime))
            .build());
    }

    private void revokeFamily(String familyId) {
        Instant revokedAt = Instant.now();
        repository.findByFamilyId(familyId).forEach(token -> {
            token.setRevoked(true);
            token.setRevokedAt(revokedAt);
        });
        repository.flush();
    }

    private TokenPair pair(String username, String role, String refreshToken) {
        return new TokenPair(jwtTokenProvider.generateToken(username, role), refreshToken,
            jwtTokenProvider.getTokenLifetimeSeconds());
    }

    private String newRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash refresh token", exception);
        }
    }

    public record TokenPair(String accessToken, String refreshToken, long expiresInSeconds) {}
}
