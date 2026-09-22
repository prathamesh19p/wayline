package com.wayline.provider.infrastructure;

import com.wayline.provider.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Provider A implementation (e.g., Razorpay-like provider).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ProviderAAdapter implements PaymentProvider {

    private static final String PROVIDER_NAME = "ProviderA";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supports(String paymentMethod, String currency) {
        // Support UPI, CARD, NETBANKING for INR
        if (!currency.equals("INR")) {
            return false;
        }
        return paymentMethod.equalsIgnoreCase("UPI") ||
               paymentMethod.equalsIgnoreCase("CARD") ||
               paymentMethod.equalsIgnoreCase("NETBANKING");
    }

    @Override
    public ProviderPaymentResponse createPayment(ProviderPaymentRequest request) throws ProviderException {
        log.info("Creating payment with ProviderA: amount={} currency={}", request.getAmount(), request.getCurrency());

        try {
            // Simulate provider call
            String providerPaymentId = "PROV_A_" + UUID.randomUUID().toString().substring(0, 12);

            // Simulate occasional failures
            if (request.getAmount() % 1000000 == 0) {
                log.warn("Simulating provider failure for amount: {}", request.getAmount());
                throw new ProviderException(
                    "Provider A declined payment",
                    PROVIDER_NAME,
                    "DECLINED"
                );
            }

            log.info("Payment created with ProviderA: {}", providerPaymentId);

            return ProviderPaymentResponse.builder()
                .providerPaymentId(providerPaymentId)
                .status("SUCCESS")
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .build();
        } catch (ProviderException e) {
            throw e;
        } catch (Exception e) {
            throw new ProviderException(
                "Failed to create payment with ProviderA: " + e.getMessage(),
                PROVIDER_NAME,
                e
            );
        }
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerPaymentId) throws ProviderException {
        log.info("Getting payment status from ProviderA: {}", providerPaymentId);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("SUCCESS")
            .build();
    }

    @Override
    public ProviderPaymentResponse refundPayment(String providerPaymentId, Long amount) throws ProviderException {
        log.info("Refunding payment with ProviderA: {} amount={}", providerPaymentId, amount);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId + "_REFUND")
            .status("SUCCESS")
            .amount(amount)
            .build();
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        // Simplified signature verification
        return signature != null && !signature.isBlank();
    }

    @Override
    public boolean isHealthy() {
        // In real implementation, would check provider health
        return true;
    }
}
