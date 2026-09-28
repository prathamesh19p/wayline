package com.wayline.routing.application;

import com.wayline.provider.domain.PaymentProvider;
import com.wayline.routing.domain.ProviderHealth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProviderSelectorServiceTests {
    @Mock private ProviderHealthService healthService;

    @Test
    void rejectsUnsupportedPaymentWhenNoProvidersExist() {
        ProviderSelectorService service = new ProviderSelectorService(List.<PaymentProvider>of(), healthService);
        assertThrows(IllegalArgumentException.class, () -> service.selectProvider("CARD", "INR"));
    }

    @Test
    void failsClosedWhenEverySupportedProviderIsUnhealthy() {
        PaymentProvider provider = provider("provider-a");
        when(healthService.getProviderHealth("provider-a")).thenReturn(health("provider-a", false, "OPEN", 1));
        ProviderSelectorService service = new ProviderSelectorService(List.of(provider), healthService);

        assertThrows(IllegalStateException.class, () -> service.selectProvider("CARD", "INR"));
    }

    @Test
    void returnsHealthyCandidatesInPriorityOrderAndHonorsExclusions() {
        PaymentProvider preferred = provider("preferred");
        PaymentProvider fallback = provider("fallback");
        when(healthService.getProviderHealth("preferred"))
            .thenReturn(health("preferred", true, "CLOSED", 1));
        when(healthService.getProviderHealth("fallback"))
            .thenReturn(health("fallback", true, "CLOSED", 2));
        ProviderSelectorService service = new ProviderSelectorService(List.of(fallback, preferred), healthService);

        assertEquals(List.of(preferred, fallback), service.getEligibleProviders("CARD", "INR", Set.of()));
        assertEquals(List.of(fallback), service.getEligibleProviders("CARD", "INR", Set.of("preferred")));
    }

    private PaymentProvider provider(String name) {
        return new PaymentProvider() {
            @Override public String getProviderName() { return name; }
            @Override public boolean supports(String method, String currency) { return true; }
            @Override public com.wayline.provider.domain.ProviderPaymentResponse createPayment(
                com.wayline.provider.domain.ProviderPaymentRequest request) { return null; }
            @Override public com.wayline.provider.domain.ProviderPaymentResponse getPaymentStatus(String id) { return null; }
            @Override public com.wayline.provider.domain.ProviderPaymentResponse refundPayment(String id, Long amount) { return null; }
            @Override public boolean isHealthy() { return true; }
        };
    }

    private ProviderHealth health(String name, boolean healthy, String state, int priority) {
        return ProviderHealth.builder()
            .providerName(name)
            .healthy(healthy)
            .circuitBreakerState(state)
            .priority(priority)
            .successRate(99.0)
            .build();
    }
}
