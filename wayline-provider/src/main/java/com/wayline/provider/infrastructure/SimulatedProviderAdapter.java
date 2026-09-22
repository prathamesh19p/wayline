package com.wayline.provider.infrastructure;

import com.wayline.provider.domain.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Simulated provider for testing failure scenarios.
 * Can produce success, failure, timeout, duplicate, and delayed responses.
 */
@Component
@Slf4j
public class SimulatedProviderAdapter implements PaymentProvider {

    private static final String PROVIDER_NAME = "SimulatedProvider";

    @Override
    public String getProviderName() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean supports(String paymentMethod, String currency) {
        // Supports all payment methods and currencies
        return true;
    }

    @Override
    public ProviderPaymentResponse createPayment(ProviderPaymentRequest request) throws ProviderException {
        log.info("SimulatedProvider creating payment: amount={}", request.getAmount());

        String scenario = determineScenario(request.getAmount());

        return switch (scenario) {
            case "SUCCESS" -> handleSuccess(request);
            case "FAILURE" -> handleFailure();
            case "TIMEOUT" -> handleTimeout();
            case "SLOW" -> handleSlowResponse(request);
            case "UNKNOWN" -> handleUnknownResponse(request);
            default -> handleSuccess(request);
        };
    }

    @Override
    public ProviderPaymentResponse getPaymentStatus(String providerPaymentId) throws ProviderException {
        log.info("SimulatedProvider getting status: {}", providerPaymentId);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("SUCCESS")
            .build();
    }

    @Override
    public ProviderPaymentResponse refundPayment(String providerPaymentId, Long amount) throws ProviderException {
        log.info("SimulatedProvider refunding: {} amount={}", providerPaymentId, amount);

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

    private String determineScenario(Long amount) {
        long mod = Math.abs(amount % 10);
        return switch ((int) mod) {
            case 0 -> "SUCCESS";
            case 1 -> "FAILURE";
            case 2 -> "TIMEOUT";
            case 3 -> "SLOW";
            case 4 -> "UNKNOWN";
            default -> "SUCCESS";
        };
    }

    private ProviderPaymentResponse handleSuccess(ProviderPaymentRequest request) {
        String providerPaymentId = "SIM_" + UUID.randomUUID().toString().substring(0, 12);
        log.info("Simulated SUCCESS response: {}", providerPaymentId);

        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("SUCCESS")
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .build();
    }

    private ProviderPaymentResponse handleFailure() throws ProviderException {
        log.warn("Simulated FAILURE response");
        throw new ProviderException(
            "Simulated provider failure",
            PROVIDER_NAME,
            "DECLINED"
        );
    }

    private ProviderPaymentResponse handleTimeout() throws ProviderException {
        log.warn("Simulating TIMEOUT");
        try {
            Thread.sleep(15000); // Simulate hanging request
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        throw new ProviderException(
            "Simulated timeout",
            PROVIDER_NAME,
            "TIMEOUT"
        );
    }

    private ProviderPaymentResponse handleSlowResponse(ProviderPaymentRequest request) throws ProviderException {
        log.warn("Simulated SLOW response");
        try {
            Thread.sleep(5000); // Slow but eventual success
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String providerPaymentId = "SIM_SLOW_" + UUID.randomUUID().toString().substring(0, 8);
        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("SUCCESS")
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .build();
    }

    private ProviderPaymentResponse handleUnknownResponse(ProviderPaymentRequest request) throws ProviderException {
        log.warn("Simulated UNKNOWN response");
        String providerPaymentId = "SIM_UNKNOWN_" + UUID.randomUUID().toString().substring(0, 8);

        // Return pending status to simulate ambiguous outcome
        return ProviderPaymentResponse.builder()
            .providerPaymentId(providerPaymentId)
            .status("PENDING")
            .amount(request.getAmount())
            .currency(request.getCurrency())
            .build();
    }
}
