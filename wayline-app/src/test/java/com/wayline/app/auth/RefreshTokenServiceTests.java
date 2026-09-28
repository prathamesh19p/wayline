package com.wayline.app.auth;

import com.wayline.common.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTests {
    @Mock private RefreshTokenRepository repository;
    @Mock private JwtTokenProvider jwtTokenProvider;

    private RefreshTokenService service;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, jwtTokenProvider);
        ReflectionTestUtils.setField(service, "refreshLifetime", Duration.ofDays(30));
    }

    @Test
    void rotatesRefreshTokenAndRevokesThePreviousToken() {
        when(jwtTokenProvider.generateToken(anyString(), anyString())).thenReturn("access-token");
        when(jwtTokenProvider.getTokenLifetimeSeconds()).thenReturn(3600L);
        RefreshToken existing = token(false);
        when(repository.findByHashForUpdate(anyString())).thenReturn(Optional.of(existing));
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<RefreshTokenService.TokenPair> rotated = service.rotate("old-refresh-token");

        assertTrue(rotated.isPresent());
        assertNotEquals("old-refresh-token", rotated.orElseThrow().refreshToken());
        assertTrue(existing.getRevoked());
        assertTrue(existing.getRevokedAt().isAfter(Instant.EPOCH));
        verify(repository).save(existing);
    }

    @Test
    void reuseOfRevokedTokenRevokesItsTokenFamily() {
        RefreshToken reused = token(true);
        RefreshToken replacement = token(false);
        replacement.setId(2L);
        when(repository.findByHashForUpdate(anyString())).thenReturn(Optional.of(reused));
        when(repository.findByFamilyId("family-1")).thenReturn(List.of(reused, replacement));

        assertFalse(service.rotate("replayed-token").isPresent());
        assertTrue(reused.getRevoked());
        assertTrue(replacement.getRevoked());
        verify(repository).flush();
        verify(repository, never()).save(any(RefreshToken.class));
    }

    private RefreshToken token(boolean revoked) {
        return RefreshToken.builder()
            .id(1L)
            .tokenHash("a".repeat(64))
            .username("merchant")
            .role("MERCHANT")
            .familyId("family-1")
            .expiresAt(Instant.now().plusSeconds(600))
            .revoked(revoked)
            .build();
    }
}
