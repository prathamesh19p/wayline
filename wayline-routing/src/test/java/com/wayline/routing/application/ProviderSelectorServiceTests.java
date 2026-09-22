package com.wayline.routing.application;

import com.wayline.provider.domain.PaymentProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(MockitoExtension.class)
class ProviderSelectorServiceTests {
    @Mock private ProviderHealthService healthService;

    @Test
    void rejectsUnsupportedPaymentWhenNoProvidersExist() {
        ProviderSelectorService service = new ProviderSelectorService(List.<PaymentProvider>of(), healthService);
        assertThrows(IllegalArgumentException.class, () -> service.selectProvider("CARD", "INR"));
    }
}
