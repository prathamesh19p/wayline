package com.wayline.app.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

public class ApiRateLimitFilter extends OncePerRequestFilter {
    private static final long WINDOW_SECONDS = 60;
    private static final DefaultRedisScript<Long> INCREMENT_WINDOW = new DefaultRedisScript<>(
        "local count = redis.call('INCR', KEYS[1]); "
            + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; "
            + "return count",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int apiRequestsPerMinute;
    private final int loginRequestsPerMinute;

    public ApiRateLimitFilter(
        StringRedisTemplate redisTemplate,
        int apiRequestsPerMinute,
        int loginRequestsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.apiRequestsPerMinute = apiRequestsPerMinute;
        this.loginRequestsPerMinute = loginRequestsPerMinute;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        boolean loginRequest = "/api/v1/auth/login".equals(request.getRequestURI());
        int limit = loginRequest ? loginRequestsPerMinute : apiRequestsPerMinute;
        String principal = loginRequest ? request.getRemoteAddr() : authenticatedPrincipal(request);

        try {
            Long count = redisTemplate.execute(
                INCREMENT_WINDOW,
                List.of("wayline:rate-limit:" + (loginRequest ? "login:" : "api:") + digest(principal)),
                Long.toString(WINDOW_SECONDS)
            );
            if (count == null) {
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Rate limiter unavailable");
                return;
            }
            if (count > limit) {
                response.setHeader("Retry-After", Long.toString(WINDOW_SECONDS));
                response.sendError(HttpStatus.TOO_MANY_REQUESTS.value(), "Rate limit exceeded");
                return;
            }
        } catch (RuntimeException exception) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Rate limiter unavailable");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String authenticatedPrincipal(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
            && authentication.getName() != null && !"anonymousUser".equals(authentication.getName())) {
            return authentication.getName();
        }
        return request.getRemoteAddr();
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash rate-limit key", exception);
        }
    }
}
