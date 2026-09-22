package com.wayline.payment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentAttempt;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.provider.domain.PaymentProvider;
import com.wayline.provider.domain.ProviderException;
import com.wayline.provider.domain.ProviderPaymentRequest;
import com.wayline.provider.domain.ProviderPaymentResponse;
import com.wayline.routing.application.ProviderHealthService;
import com.wayline.routing.application.ProviderSelectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrationService {
    private final PaymentService paymentService;
    private final ProviderSelectorService providerSelectorService;
    private final ProviderHealthService providerHealthService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Value("${wayline.provider.timeout-seconds:10}")
    private long providerTimeoutSeconds;

    public Payment process(Payment payment) {
        PaymentProvider provider = providerSelectorService.selectProvider(
            payment.getPaymentMethod(),
            payment.getCurrency()
        );
        paymentService.startProcessing(payment.getId(), provider.getProviderName());

        int attemptNumber = paymentService.getPaymentAttempts(payment.getId()).size() + 1;
        PaymentAttempt attempt = paymentService.recordAttempt(
            payment.getId(),
            provider.getProviderName(),
            attemptNumber
        );

        ProviderPaymentRequest request = ProviderPaymentRequest.builder()
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .merchantId(payment.getMerchantId())
            .idempotencyKey(payment.getIdempotencyKey())
            .build();

        try {
            ProviderPaymentResponse response = invokeWithTimeout(provider, request);
            paymentService.updateAttempt(
                attempt.getId(),
                response.getStatus(),
                response.getProviderPaymentId(),
                response.getFailureCode(),
                response.getFailureMessage()
            );
            providerHealthService.recordSuccess(provider.getProviderName());

            PaymentStatus status = mapStatus(response.getStatus());
            Payment updated = paymentService.transitionPaymentStatus(
                payment.getId(),
                status,
                "Provider response: " + response.getStatus(),
                "PROVIDER"
            );
            publishPaymentEvent(updated, status, provider.getProviderName(), response.getProviderPaymentId());
            return updated;
        } catch (TimeoutException exception) {
            return handleUnknown(payment, attempt, provider, "Provider timeout");
        } catch (ProviderException exception) {
            paymentService.updateAttempt(
                attempt.getId(),
                "FAILED",
                null,
                exception.getErrorCode(),
                exception.getMessage()
            );
            if ("TIMEOUT".equalsIgnoreCase(exception.getErrorCode())) {
                return handleUnknown(payment, attempt, provider, exception.getMessage());
            }
            providerHealthService.recordFailure(provider.getProviderName());
            Payment failed = paymentService.transitionPaymentStatus(
                payment.getId(),
                PaymentStatus.FAILED,
                exception.getMessage(),
                "PROVIDER"
            );
            publishPaymentEvent(failed, PaymentStatus.FAILED, provider.getProviderName(), null);
            return failed;
        } catch (Exception exception) {
            providerHealthService.recordFailure(provider.getProviderName());
            Payment failed = paymentService.transitionPaymentStatus(
                payment.getId(),
                PaymentStatus.FAILED,
                exception.getMessage(),
                "ORCHESTRATOR"
            );
            publishPaymentEvent(failed, PaymentStatus.FAILED, provider.getProviderName(), null);
            return failed;
        }
    }

    private ProviderPaymentResponse invokeWithTimeout(PaymentProvider provider, ProviderPaymentRequest request)
        throws Exception {
        return CompletableFuture.supplyAsync(() -> provider.createPayment(request))
            .get(providerTimeoutSeconds, TimeUnit.SECONDS);
    }

    private Payment handleUnknown(
        Payment payment,
        PaymentAttempt attempt,
        PaymentProvider provider,
        String reason
    ) {
        paymentService.updateAttempt(attempt.getId(), "UNKNOWN", null, "TIMEOUT", reason);
        providerHealthService.recordTimeout(provider.getProviderName());
        Payment unknown = paymentService.transitionPaymentStatus(
            payment.getId(),
            PaymentStatus.UNKNOWN,
            reason,
            "ORCHESTRATOR"
        );
        publishPaymentEvent(unknown, PaymentStatus.UNKNOWN, provider.getProviderName(), null);
        return unknown;
    }

    private PaymentStatus mapStatus(String status) {
        return switch (status == null ? "UNKNOWN" : status.toUpperCase()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PENDING", "UNKNOWN" -> PaymentStatus.UNKNOWN;
            default -> throw new IllegalArgumentException("Unsupported provider status: " + status);
        };
    }

    private void publishPaymentEvent(Payment payment, PaymentStatus status, String provider, String providerPaymentId) {
        try {
            String eventType = "Payment" + status.name().charAt(0) + status.name().substring(1).toLowerCase();
            String payload = objectMapper.writeValueAsString(new PaymentEventPayload(
                UUID.randomUUID().toString(),
                payment.getId(),
                payment.getMerchantId(),
                payment.getAmount(),
                payment.getCurrency(),
                status.name(),
                provider,
                providerPaymentId,
                Instant.now()
            ));
            outboxEventRepository.save(OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(payment.getId().toString())
                .eventType(eventType)
                .payload(payload)
                .build());
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to publish payment event", exception);
        }
    }

    private record PaymentEventPayload(
        String eventId,
        Long paymentId,
        String merchantId,
        Long amount,
        String currency,
        String status,
        String provider,
        String providerPaymentId,
        Instant occurredAt
    ) {}
}