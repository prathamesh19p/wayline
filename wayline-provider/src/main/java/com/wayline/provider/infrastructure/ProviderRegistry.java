package com.wayline.provider.infrastructure;

import com.wayline.provider.domain.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Looks providers up by name. Routing picks a provider for a new payment; follow-up operations
 * such as refunds must reach the provider that handled the original charge, not a freshly
 * selected one.
 */
@Component
public class ProviderRegistry {

    private final Map<String, PaymentProvider> providersByName;

    public ProviderRegistry(List<PaymentProvider> providers) {
        this.providersByName = providers.stream()
            .collect(Collectors.toUnmodifiableMap(PaymentProvider::getProviderName,
                Function.identity()));
    }

    public PaymentProvider require(String providerName) {
        PaymentProvider provider = providersByName.get(providerName);
        if (provider == null) {
            throw new IllegalStateException("No adapter registered for provider " + providerName);
        }
        return provider;
    }
}
