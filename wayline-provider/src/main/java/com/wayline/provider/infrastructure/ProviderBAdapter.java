package com.wayline.provider.infrastructure;

import com.wayline.provider.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provider B implementation (e.g., Stripe-like provider).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProviderBAdapter implements PaymentProvider {

    private static final String PROVIDER_NAME = "ProviderB";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supports(String paymentMethod, String currency) {
        // Support CARD, WALLET for any currency
        return paymentMethod.equalsIgnoreCase("CARD") ||
               paymentMethod.equalsIgnoreCase("WALLET") ||
               paymentMethod.equalsIgnoreCase("BANK_TRANSFER");
    }

    @Override
    public ProviderPaymentResponse createPayment(ProviderPaymentRequest request) throws ProviderException {
        log.info("Creating payment with ProviderB: amount={} currency={}", request.getAmount(), request.getCurrency());

        try {
            String providerPaymentId = "PROV_B_" + UUID.randomUUID().toString().substring(0, 12);

            // Simulate occasional timeouts
            if (request.getAmount() % 500000 == 0) {
                log.warn("Simulating timeout for amount: {}", request.getAmount());
                Thread.sleep(15000); // Simulate timeout
            }

            log.info("Payment created with ProviderB: {}", providerPaymentId);

            return ProviderPaymentResponse.builder()
                .providerPaymentId(providerPaymentId)
                .status("SUCCESS")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProviderException(
                "Payment request timed out",
                PROVIDER_NAME,
                "TIMEOUT"
            );
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException(
                "Failed to create payment with ProviderB: " + e.getMessage(),
                PROVIDER_NAME,
                e
            );
        }
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerPaymentId) throws ProviderException {
        log.info("Getting payment status from ProviderB: {}", providerPaymentId);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("SUCCESS")
            .build();
    }

    @Override
    public ProviderPaymentResponse refundPayment(String providerPaymentId, Long amount) throws ProviderException {
        log.info("Refunding payment with ProviderB: {} amount={}", providerPaymentId, amount);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId + "_REFUND")
            .status("SUCCESS")
            .amount(amount)
            .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return signature != null && !signature.isBlank();
    }

    @Override
    public boolean isHealthy() {
        return true;
    }
}
