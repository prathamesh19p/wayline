package com.wayline.routing.application;

import com.wayline.provider.domain.PaymentProvider;
import com.wayline.routing.domain.ProviderHealth;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for selecting the best provider for a payment.
 * 
 * Algorithm:
 * 1. Filter providers that support payment method/currency
 * 2. Remove disabled providers
 * 3. Remove unhealthy providers (circuit breaker open)
 * 4. Apply configured priority
 * 5. Select highest-priority healthy provider
 * 6. If multiple qualify, use weighted score based on success rate
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProviderSelectorService {

    private final List<PaymentProvider> providers;
    private final ProviderHealthService healthService;

    /**
     * Select best provider for payment.
     *
     * @param paymentMethod Payment method (UPI, CARD, etc)
     * @param currency Currency code (INR, USD, etc)
     * @return Selected provider
     */
    public PaymentProvider selectProvider(String paymentMethod, String currency) {
        log.info("Selecting provider for method={} currency={}", paymentMethod, currency);

        // Step 1: Filter supported providers
        List<PaymentProvider> supportedProviders = providers.stream()
            .filter(p -> p.supports(paymentMethod, currency))
            .collect(Collectors.toList());

        if (supportedProviders.isEmpty()) {
            throw new IllegalArgumentException(
                String.format("No provider supports %s for %s", paymentMethod, currency)
            );
        }

        log.debug("Supported providers: {}", 
            supportedProviders.stream().map(PaymentProvider::getProviderName).toList());

        // Step 2-3: Filter healthy providers
        List<PaymentProvider> healthyProviders = supportedProviders.stream()
            .filter(p -> {
                ProviderHealth health = healthService.getProviderHealth(p.getProviderName());
                boolean isHealthy = health != null && 
                                   Boolean.TRUE.equals(health.getHealthy()) &&
                                   !"OPEN".equals(health.getCircuitBreakerState());
                log.debug("Provider {} health: {}", p.getProviderName(), isHealthy);
                return isHealthy;
            })
            .collect(Collectors.toList());

        if (healthyProviders.isEmpty()) {
            log.warn("No healthy providers available, using fallback");
            // Fallback: use first supported provider
            return supportedProviders.get(0);
        }

        // Step 4-5: Select by priority and success rate
        return selectByPriority(healthyProviders);
    }

    /**
     * Select provider based on priority and success rate.
     */
    private PaymentProvider selectByPriority(List<PaymentProvider> providers) {
        Map<String, ProviderHealth> healthMap = new HashMap<>();
        for (PaymentProvider provider : providers) {
            ProviderHealth health = healthService.getProviderHealth(provider.getProviderName());
            if (health != null) {
                healthMap.put(provider.getProviderName(), health);
            }
        }

        // Sort by priority (lower number = higher priority), then by success rate
        PaymentProvider selected = providers.stream()
            .min((p1, p2) -> {
                ProviderHealth h1 = healthMap.get(p1.getProviderName());
                ProviderHealth h2 = healthMap.get(p2.getProviderName());

                int p1Priority = h1 != null && h1.getPriority() != null ? h1.getPriority() : Integer.MAX_VALUE;
                int p2Priority = h2 != null && h2.getPriority() != null ? h2.getPriority() : Integer.MAX_VALUE;

                // First compare by priority
                int priorityComparison = Integer.compare(p1Priority, p2Priority);
                if (priorityComparison != 0) {
                    return priorityComparison;
                }

                // If same priority, compare by success rate (higher is better)
                double p1Rate = h1 != null && h1.getSuccessRate() != null ? h1.getSuccessRate() : 0;
                double p2Rate = h2 != null && h2.getSuccessRate() != null ? h2.getSuccessRate() : 0;

                return Double.compare(p2Rate, p1Rate);
            })
            .orElse(providers.get(0));

        log.info("Selected provider: {}", selected.getProviderName());
        return selected;
    }
}
