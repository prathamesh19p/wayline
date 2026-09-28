package com.wayline.payment.application;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.wayline.common.outbox.OutboxEvent;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentAttempt;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.provider.domain.PaymentProvider;
import com.wayline.provider.domain.ProviderException;
import com.wayline.provider.domain.ProviderPaymentRequest;
import com.wayline.provider.domain.ProviderPaymentResponse;
import com.wayline.routing.application.ProviderHealthService;
import com.wayline.routing.application.ProviderSelectorService;
import com.wayline.provider.infrastructure.ProviderCallExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentOrchestrationService {

    private static final Map<PaymentStatus, String> EVENT_TYPES = Map.of(
        PaymentStatus.SUCCESS, "PaymentSucceeded",
        PaymentStatus.FAILED, "PaymentFailed",
        PaymentStatus.UNKNOWN, "PaymentUnknown"
    );

    private final PaymentService paymentService;
    private final ProviderSelectorService providerSelectorService;
    private final ProviderHealthService providerHealthService;
    private final ProviderCallExecutor providerCallExecutor;
    private final ObjectMapper objectMapper;

    public Payment process(Payment payment) {
        var candidates = providerSelectorService.getEligibleProviders(
            payment.getPaymentMethod(),
            payment.getCurrency(),
            Set.of()
        );
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No healthy provider available for this payment");
        }
        paymentService.startProcessing(payment.getId(), candidates.getFirst().getProviderName());

        ProviderPaymentRequest request = ProviderPaymentRequest.builder()
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .merchantId(payment.getMerchantId())
            .idempotencyKey(payment.getIdempotencyKey())
            .build();

        String lastFailure = "All eligible providers failed";
        Set<String> attemptedProviders = new HashSet<>();
        int attemptNumber = paymentService.getPaymentAttempts(payment.getId()).size();
        for (PaymentProvider provider : candidates) {
            if (!attemptedProviders.add(provider.getProviderName())) {
                continue;
            }
            if (attemptNumber > 0) {
                paymentService.selectProviderForProcessingAttempt(payment.getId(), provider.getProviderName());
            }
            PaymentAttempt attempt = paymentService.recordAttempt(
                payment.getId(), provider.getProviderName(), ++attemptNumber);
            try {
                ProviderPaymentResponse response = providerCallExecutor.call(() -> provider.createPayment(request));
                if (response == null) {
                    return handleUnknown(payment, attempt, provider, "Provider returned no response");
                }
                PaymentStatus status = mapStatus(response.getStatus());
                paymentService.updateAttempt(attempt.getId(), response.getStatus(), response.getProviderPaymentId(),
                    response.getFailureCode(), response.getFailureMessage());
                if (status == PaymentStatus.FAILED) {
                    lastFailure = response.getFailureMessage() == null
                        ? "Provider reported a definitive failure"
                        : response.getFailureMessage();
                    providerHealthService.recordFailure(provider.getProviderName());
                    return complete(payment, PaymentStatus.FAILED, lastFailure, "PROVIDER",
                        provider.getProviderName(), response.getProviderPaymentId());
                }
                if (status == PaymentStatus.SUCCESS) {
                    providerHealthService.recordSuccess(provider.getProviderName());
                }
                return complete(payment, status, "Provider response: " + response.getStatus(), "PROVIDER",
                    provider.getProviderName(), response.getProviderPaymentId());
            } catch (TimeoutException exception) {
                return handleUnknown(payment, attempt, provider, "Provider timeout");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return handleUnknown(payment, attempt, provider, "Interrupted while awaiting provider");
            } catch (ProviderException exception) {
                paymentService.updateAttempt(attempt.getId(), "FAILED", null,
                    exception.getErrorCode(), exception.getMessage());
                if ("TIMEOUT".equalsIgnoreCase(exception.getErrorCode())) {
                    return handleUnknown(payment, attempt, provider, exception.getMessage());
                }
                providerHealthService.recordFailure(provider.getProviderName());
                lastFailure = exception.getMessage();
                if (!isSafeToRetryBeforeSubmission(exception.getErrorCode())) {
                    PaymentStatus terminalStatus = "DECLINED".equalsIgnoreCase(exception.getErrorCode())
                        ? PaymentStatus.FAILED
                        : PaymentStatus.UNKNOWN;
                    if (terminalStatus == PaymentStatus.UNKNOWN) {
                        return handleUnknown(payment, attempt, provider, exception.getMessage());
                    }
                    return complete(payment, terminalStatus, lastFailure, "PROVIDER",
                        provider.getProviderName(), null);
                }
            } catch (RuntimeException exception) {
                log.error("Orchestration failed for provider {} payment {}",
                    provider.getProviderName(), payment.getId(), exception);
                paymentService.updateAttempt(attempt.getId(), "FAILED", null,
                    "PROVIDER_ERROR", exception.getMessage());
                providerHealthService.recordFailure(provider.getProviderName());
                lastFailure = exception.getMessage();
            }
        }

        PaymentProvider lastProvider = candidates.getLast();
        return complete(payment, PaymentStatus.FAILED, lastFailure, "PROVIDER", lastProvider.getProviderName(), null);
    }

    private Payment handleUnknown(
        Payment payment,
        PaymentAttempt attempt,
        PaymentProvider provider,
        String reason
    ) {
        paymentService.updateAttempt(attempt.getId(), "UNKNOWN", null, "TIMEOUT", reason);
        providerHealthService.recordTimeout(provider.getProviderName());
        return complete(payment, PaymentStatus.UNKNOWN, reason, "ORCHESTRATOR",
            provider.getProviderName(), null);
    }

    private PaymentStatus mapStatus(String status) {
        return switch (status == null ? "UNKNOWN" : status.toUpperCase()) {
            case "SUCCESS" -> PaymentStatus.SUCCESS;
            case "FAILED" -> PaymentStatus.FAILED;
            case "PENDING", "UNKNOWN" -> PaymentStatus.UNKNOWN;
            default -> PaymentStatus.UNKNOWN;
        };
    }

    private boolean isSafeToRetryBeforeSubmission(String errorCode) {
        return "UNAVAILABLE_BEFORE_SUBMIT".equalsIgnoreCase(errorCode)
            || "RATE_LIMITED_BEFORE_SUBMIT".equalsIgnoreCase(errorCode);
    }

    private Payment complete(Payment payment, PaymentStatus status, String reason, String source,
                             String provider, String providerPaymentId) {
        return paymentService.transitionPaymentStatus(payment.getId(), status, reason, source,
            buildOutboxEvent(payment, status, provider, providerPaymentId));
    }

    private OutboxEvent buildOutboxEvent(Payment payment, PaymentStatus status, String provider,
                                         String providerPaymentId) {
        try {
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
            return OutboxEvent.builder()
                .aggregateType("PAYMENT")
                .aggregateId(payment.getId().toString())
                .eventType(EVENT_TYPES.get(status))
                .payload(payload)
                .build();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialise payment event", exception);
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