package com.wayline.payment.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.payment.domain.PaymentAttempt;
import com.wayline.payment.domain.Refund;
import com.wayline.payment.domain.RefundStatus;
import com.wayline.payment.infrastructure.PaymentAttemptRepository;
import com.wayline.payment.infrastructure.RefundRepository;
import com.wayline.provider.domain.PaymentProvider;
import com.wayline.provider.domain.ProviderException;
import com.wayline.provider.domain.ProviderPaymentResponse;
import com.wayline.provider.infrastructure.ProviderCallExecutor;
import com.wayline.provider.infrastructure.ProviderRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeoutException;

/**
 * Returns money for a payment that already succeeded.
 *
 * <p>A refund never edits the original payment. The payment row and its ledger entries are left
 * exactly as they were and the refund posts its own compensating ledger transaction, so the
 * record of what was believed, and when, survives.
 *
 * <p>The provider call deliberately sits between two short transactions rather than inside one:
 * holding the payment's row lock across a network call would serialise every refund in the
 * system behind the slowest provider.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefundService {

    private final RefundLifecycle lifecycle;
    private final RefundRepository refundRepository;
    private final PaymentAttemptRepository paymentAttemptRepository;
    private final ProviderRegistry providerRegistry;
    private final ProviderCallExecutor providerCallExecutor;
    private final ObjectMapper objectMapper;

    public Refund requestRefund(String merchantId, String idempotencyKey, Long paymentId,
                                Long amount, String reason) {
        Refund refund = lifecycle.reserve(merchantId, idempotencyKey, paymentId, amount, reason);
        if (refund.getStatus() != RefundStatus.CREATED) {
            // A replayed idempotency key: the original outcome stands.
            return refund;
        }
        return execute(refund);
    }

    private Refund execute(Refund reserved) {
        Refund refund = lifecycle.markProcessing(reserved.getId());
        String providerPaymentId = findProviderPaymentId(refund.getPaymentId());
        PaymentProvider provider = providerRegistry.require(refund.getProvider());

        try {
            ProviderPaymentResponse response = providerCallExecutor.call(
                () -> provider.refundPayment(providerPaymentId, refund.getAmount()));
            return settle(refund, RefundStatus.SUCCESS, response.getProviderPaymentId(), null, null);
        } catch (TimeoutException exception) {
            return settle(refund, RefundStatus.UNKNOWN, null, "TIMEOUT",
                "Provider did not respond within the refund timeout");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return settle(refund, RefundStatus.UNKNOWN, null, "TIMEOUT",
                "Interrupted while awaiting provider");
        } catch (ProviderException exception) {
            // A timeout reported by the adapter is still an unknown outcome, not a decline.
            if ("TIMEOUT".equalsIgnoreCase(exception.getErrorCode())) {
                return settle(refund, RefundStatus.UNKNOWN, null, "TIMEOUT", exception.getMessage());
            }
            return settle(refund, RefundStatus.FAILED, null,
                exception.getErrorCode(), exception.getMessage());
        } catch (RuntimeException exception) {
            log.error("Refund {} failed unexpectedly", refund.getId(), exception);
            return settle(refund, RefundStatus.FAILED, null, "INTERNAL_ERROR",
                exception.getMessage());
        }
    }

    private Refund settle(Refund refund, RefundStatus outcome, String providerRefundId,
                          String failureCode, String failureMessage) {
        OutboxEvent event = buildEvent(refund, outcome, providerRefundId);
        return lifecycle.settle(refund.getId(), outcome, providerRefundId,
            failureCode, failureMessage, event);
    }

    public List<Refund> getRefundsForPayment(Long paymentId) {
        return refundRepository.findByPaymentIdOrderByCreatedAtAsc(paymentId);
    }

    public Refund getRefundForMerchant(Long refundId, String merchantId) {
        return refundRepository.findById(refundId)
            .filter(refund -> refund.getMerchantId().equals(merchantId))
            .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
    }

    public Refund resolveWebhook(String provider, String providerEventId, String payload,
                                 Long refundId, String providerRefundId, RefundStatus outcome) {
        Refund refund = refundId == null
            ? refundRepository.findByProviderAndProviderRefundId(provider, providerRefundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found"))
            : refundRepository.findById(refundId)
                .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
        OutboxEvent event = buildEvent(refund, outcome, providerRefundId);
        return lifecycle.resolveFromWebhook(provider, providerEventId, payload, refund.getId(),
            outcome, providerRefundId, event);
    }

    /**
     * The provider's own id for the charge, taken from the most recent attempt that produced one.
     * A refund has to reference the charge as the provider knows it.
     */
    private String findProviderPaymentId(Long paymentId) {
        return paymentAttemptRepository.findByPaymentIdOrderByAttemptNumberAsc(paymentId).stream()
            .map(PaymentAttempt::getProviderPaymentId)
            .filter(id -> id != null && !id.isBlank())
            .reduce((first, second) -> second)
            .orElseThrow(() -> new IllegalStateException(
                "Payment " + paymentId + " has no provider payment id to refund against"));
    }

    private OutboxEvent buildEvent(Refund refund, RefundStatus outcome, String providerRefundId) {
        try {
            String payload = objectMapper.writeValueAsString(new RefundEventPayload(
                UUID.randomUUID().toString(),
                refund.getId(),
                refund.getPaymentId(),
                refund.getMerchantId(),
                refund.getAmount(),
                refund.getCurrency(),
                outcome.name(),
                refund.getProvider(),
                providerRefundId,
                Instant.now()));
            return OutboxEvent.builder()
                .aggregateType("REFUND")
                .aggregateId(refund.getId().toString())
                .eventType(EVENT_TYPES.get(outcome))
                .payload(payload)
                .build();
        } catch (JacksonException exception) {
            throw new IllegalStateException("Unable to serialise refund event", exception);
        }
    }

    private static final java.util.Map<RefundStatus, String> EVENT_TYPES = java.util.Map.of(
        RefundStatus.SUCCESS, "RefundSucceeded",
        RefundStatus.FAILED, "RefundFailed",
        RefundStatus.UNKNOWN, "RefundUnknown"
    );

    private record RefundEventPayload(
        String eventId,
        Long refundId,
        Long paymentId,
        String merchantId,
        Long amount,
        String currency,
        String status,
        String provider,
        String providerRefundId,
        Instant occurredAt
    ) {}
}
