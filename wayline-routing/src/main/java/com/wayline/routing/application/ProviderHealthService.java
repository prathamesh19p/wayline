package com.wayline.routing.application;

import com.wayline.routing.domain.ProviderHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * Service for tracking provider health and circuit breaker state.
 * Uses Redis for short-lived health information.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderHealthService {

    private static final String HEALTH_KEY_PREFIX = "provider:health:";
    private static final long HEALTH_TTL_SECONDS = 3600; // 1 hour

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Get provider health status.
     */
    public ProviderHealth getProviderHealth(String providerName) {
        String key = HEALTH_KEY_PREFIX + providerName;
        Object cached = redisTemplate.opsForValue().get(key);
        
        if (cached instanceof ProviderHealth) {
            return (ProviderHealth) cached;
        }

        // Initialize with default healthy state
        return ProviderHealth.builder()
            .providerName(providerName)
            .healthy(true)
            .successCount(0)
            .failureCount(0)
            .timeoutCount(0)
            .successRate(100.0)
            .priority(50)
            .circuitBreakerState("CLOSED")
            .lastUpdated(Instant.now())
            .build();
    }

    /**
     * Record successful payment with provider.
     */
    public void recordSuccess(String providerName) {
        ProviderHealth health = getProviderHealth(providerName);
        health.recordSuccess();
        updateHealth(providerName, health);
        
        log.debug("Provider {} success recorded. Success rate: {}", providerName, health.getSuccessRate());
    }

    /**
     * Record failed payment with provider.
     */
    public void recordFailure(String providerName) {
        ProviderHealth health = getProviderHealth(providerName);
        health.recordFailure();
        updateHealth(providerName, health);

        // Check if should open circuit breaker
        if (health.getSuccessRate() != null && health.getSuccessRate() < 50) {
            health.setCircuitBreakerState("OPEN");
            health.setHealthy(false);
            updateHealth(providerName, health);
            log.warn("Circuit breaker OPENED for provider: {}", providerName);
        }

        log.debug("Provider {} failure recorded. Success rate: {}", providerName, health.getSuccessRate());
    }

    /**
     * Record timeout with provider.
     */
    public void recordTimeout(String providerName) {
        ProviderHealth health = getProviderHealth(providerName);
        health.recordTimeout();
        updateHealth(providerName, health);

        log.debug("Provider {} timeout recorded. Success rate: {}", providerName, health.getSuccessRate());
    }

    /**
     * Update provider health in Redis.
     */
    private void updateHealth(String providerName, ProviderHealth health) {
        health.setLastUpdated(Instant.now());
        String key = HEALTH_KEY_PREFIX + providerName;
        redisTemplate.opsForValue().set(key, health, HEALTH_TTL_SECONDS, TimeUnit.SECONDS);
    }

    /**
     * Reset circuit breaker to HALF_OPEN for retry.
     */
    public void resetCircuitBreaker(String providerName) {
        ProviderHealth health = getProviderHealth(providerName);
        health.setCircuitBreakerState("HALF_OPEN");
        health.setHealthy(true);
        updateHealth(providerName, health);
        
        log.info("Circuit breaker reset to HALF_OPEN for provider: {}", providerName);
    }

    /**
     * Close circuit breaker after successful recovery.
     */
    public void closeCircuitBreaker(String providerName) {
        ProviderHealth health = getProviderHealth(providerName);
        health.setCircuitBreakerState("CLOSED");
        health.setHealthy(true);
        updateHealth(providerName, health);
        
        log.info("Circuit breaker CLOSED for provider: {}", providerName);
    }
}
