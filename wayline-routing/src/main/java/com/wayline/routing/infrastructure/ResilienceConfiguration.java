package com.wayline.routing.infrastructure;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Resilience4j configuration for payment provider calls.
 */
@Configuration
public class ResilienceConfiguration {

    /**
     * Circuit breaker configuration for provider calls.
     * Opens after 50% failure rate on 100 calls.
     */
    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
            .failureRateThreshold(50.0f)
            .slowCallRateThreshold(50.0f)
            .slowCallDurationThreshold(Duration.ofSeconds(10))
            .slidingWindowSize(100)
            .minimumNumberOfCalls(10)
            .waitDurationInOpenState(Duration.ofSeconds(60))
            .permittedNumberOfCallsInHalfOpenState(5)
            .recordExceptions(Exception.class)
            .build();

        return registry.circuitBreaker("payment-provider", config);
    }

    /**
     * Retry configuration for provider calls.
     * Retries with exponential backoff: 100ms, 200ms, 400ms
     */
    @Bean
    public Retry paymentRetry(RetryRegistry registry) {
        RetryConfig config = RetryConfig.custom()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(100))
            .intervalFunction(io.github.resilience4j.core.IntervalFunction.ofExponentialBackoff(100, 2))
            .retryOnException(e -> !(e instanceof IllegalArgumentException))
            .build();

        return registry.retry("payment-provider", config);
    }

    /**
     * Time limiter configuration for provider calls.
     * 10 second timeout for provider API calls.
     */
    @Bean
    public TimeLimiter paymentTimeLimiter(TimeLimiterRegistry registry) {
        TimeLimiterConfig config = TimeLimiterConfig.custom()
            .timeoutDuration(Duration.ofSeconds(10))
            .cancelRunningFuture(true)
            .build();

        return registry.timeLimiter("payment-provider", config);
    }
}
