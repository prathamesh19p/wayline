package com.wayline.routing.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Provider health and routing information.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderHealth {
    private String providerName;
    private Boolean healthy;
    private Integer successCount;
    private Integer failureCount;
    private Integer timeoutCount;
    private Double successRate; // percentage 0-100
    private Integer priority; // lower is higher priority
    private Instant lastUpdated;
    private String circuitBreakerState; // CLOSED, OPEN, HALF_OPEN

    public void recordSuccess() {
        successCount = (successCount != null ? successCount : 0) + 1;
        updateSuccessRate();
    }

    public void recordFailure() {
        failureCount = (failureCount != null ? failureCount : 0) + 1;
        updateSuccessRate();
    }

    public void recordTimeout() {
        timeoutCount = (timeoutCount != null ? timeoutCount : 0) + 1;
        updateSuccessRate();
    }

    private void updateSuccessRate() {
        int total = (successCount != null ? successCount : 0) + 
                   (failureCount != null ? failureCount : 0) + 
                   (timeoutCount != null ? timeoutCount : 0);
        if (total > 0) {
            this.successRate = ((double) (successCount != null ? successCount : 0) / total) * 100;
        }
    }
}
