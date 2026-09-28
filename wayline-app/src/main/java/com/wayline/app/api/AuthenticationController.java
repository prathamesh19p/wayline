package com.wayline.app.api;

import com.wayline.common.security.JwtTokenProvider;
import com.wayline.app.auth.RefreshTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/v1/auth")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Exchange credentials for a bearer token.")
public class AuthenticationController {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;

    @Value("${wayline.auth.username:}")
    private String configuredUsername;

    @Value("${wayline.auth.password:}")
    private String configuredPassword;

    @Value("${wayline.auth.role:MERCHANT}")
    private String configuredRole;

    @Operation(
        summary = "Log in and receive a JWT",
        description = """
            Returns a signed JWT to send as `Authorization: Bearer <token>` on every other \
            endpoint. The token carries the merchant identity and role used for authorization.

            Credentials come from `WAYLINE_AUTH_USERNAME` and `WAYLINE_AUTH_PASSWORD`. This is a \
            single-tenant stand-in for a real identity provider; it is deliberately simple so the \
            project stays runnable without external infrastructure.""")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Authenticated"),
        @ApiResponse(responseCode = "401", description = "Unknown user or wrong password")
    })
    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody LoginRequest request) {
        log.info("Login attempt for user: {}", request.username());

        if (configuredUsername.isBlank() || configuredPassword.isBlank() ||
            !configuredUsername.equals(request.username()) ||
            !configuredPassword.equals(request.password())) {
            return ResponseEntity.status(401).build();
        }

        RefreshTokenService.TokenPair tokenPair = refreshTokenService.issue(request.username(), configuredRole);
        log.info("Token generated for user: {}", request.username());

        return ResponseEntity.ok(new LoginResponse(
            tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.expiresInSeconds()));
    }

    @Operation(summary = "Rotate a refresh token", description = "Refresh tokens are single-use and rotated on every successful exchange.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tokens rotated"),
        @ApiResponse(responseCode = "401", description = "Refresh token expired, revoked, or invalid")
    })
    @SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<LoginResponse> refresh(@RequestBody RefreshRequest request) {
        return refreshTokenService.rotate(request.refreshToken())
            .map(tokenPair -> ResponseEntity.ok(new LoginResponse(
                tokenPair.accessToken(), tokenPair.refreshToken(), tokenPair.expiresInSeconds())))
            .orElseGet(() -> ResponseEntity.status(401).build());
    }

    @Schema(description = "Credentials to exchange for a token.")
    public record LoginRequest(
        @Schema(example = "test-merchant") String username,
        @Schema(example = "test-password") String password) {}

    @Schema(description = "A signed JWT.")
    public record RefreshRequest(String refreshToken) {}

    public record LoginResponse(
        @Schema(description = "Send as: Authorization: Bearer <token>") String token,
        String refreshToken,
        long expiresIn) {}
}
