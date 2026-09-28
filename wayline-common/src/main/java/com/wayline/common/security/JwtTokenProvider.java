package com.wayline.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates the HS256 bearer tokens used for API authentication.
 */
@Component
@Slf4j
public class JwtTokenProvider {

    private static final int MIN_SECRET_LENGTH = 32;
    private static final String ROLE_CLAIM = "role";

    private final SecretKey signingKey;
    private final Duration tokenLifetime;

    public JwtTokenProvider(
        @Value("${wayline.security.jwt.secret}") String secret,
        @Value("${wayline.security.jwt.expiration}") Duration tokenLifetime
    ) {
        if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "wayline.security.jwt.secret must be at least " + MIN_SECRET_LENGTH
                    + " characters. Set the JWT_SECRET environment variable.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.tokenLifetime = tokenLifetime;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
            .subject(username)
            .claim(ROLE_CLAIM, role)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(tokenLifetime)))
            .signWith(signingKey)
            .compact();
    }

    public long getTokenLifetimeSeconds() {
        return tokenLifetime.toSeconds();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    public String getRoleFromToken(String token) {
        return parseClaims(token).get(ROLE_CLAIM, String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException exception) {
            // Expired or forged tokens are ordinary traffic, not an application error.
            log.debug("Rejected JWT: {}", exception.getMessage());
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }
}
