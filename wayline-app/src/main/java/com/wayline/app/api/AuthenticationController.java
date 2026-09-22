package com.wayline.app.api;

import com.wayline.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

/**
 * Authentication controller.
 * Handles login and token generation.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
public class AuthenticationController {

    private final JwtTokenProvider jwtTokenProvider;

    @Value("${wayline.auth.username:}")
    private String configuredUsername;

    @Value("${wayline.auth.password:}")
    private String configuredPassword;

    @Value("${wayline.auth.role:MERCHANT}")
    private String configuredRole;

    /**
     * Login endpoint.
     * Returns JWT token for authenticated user.
     *
     * @param request Login credentials
     * @return JWT token
     */
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());

        if (configuredUsername.isBlank() || configuredPassword.isBlank() ||
            !configuredUsername.equals(request.username()) ||
            !configuredPassword.equals(request.password())) {
            return ResponseEntity.status(401).build();
        }

        String token = jwtTokenProvider.generateToken(request.username(), configuredRole);
        log.info("Token generated for user: {}", request.username());

        return ResponseEntity.ok(new LoginResponse(token));
    }

    /**
     * Login request DTO.
     */
    public record LoginRequest(String username, String password) {}

    /**
     * Login response DTO.
     */
    public record LoginResponse(String token) {}
}
