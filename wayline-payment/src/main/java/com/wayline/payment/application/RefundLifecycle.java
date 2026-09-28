package com.wayline.payment.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentWebhook;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.payment.domain.Refund;
import com.wayline.payment.domain.RefundStatus;
import com.wayline.payment.infrastructure.PaymentRepository;
import com.wayline.payment.infrastructure.PaymentWebhookRepository;
import com.wayline.payment.infrastructure.RefundRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the refund flow.
 *
 * <p>Separate from {@link RefundService} because Spring applies {@code @Transactional} through a
 * proxy: a self-invoked method on the same bean runs with no transaction at all. Keeping these
 * operations on their own bean means each one genuinely gets the boundary it declares.
 *
 * <p>The split also keeps the provider network call outside any transaction, so a slow provider
 * never holds a row lock.
 */
@Service
@Slf4j
@RequiredArgsConstructor
class RefundLifecycle {

    private final RefundRepository refundRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentWebhookRepository webhookRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final PaymentLedgerPoster ledgerPoster;

    /**
     * Validates the request and reserves the amount against the payment while holding a row lock
     * on it, so two concurrent refunds cannot each observe the same remaining balance and both
     * be allowed through.
     */
    @Transactional
    Refund reserve(String merchantId, String idempotencyKey, Long paymentId, Long amount,
                   String reason) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Refund amount must be positive");
        }

        Refund existing = refundRepository
            .findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey).orElse(null);
        if (existing != null) {
            assertReplayMatches(existing, paymentId, amount);
            log.info("Idempotency key replayed for refund {}", existing.getId());
            return existing;
        }

        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));

        if (!payment.getMerchantId().equals(merchantId)) {
            // Deliberately the same error as a missing payment: otherwise this endpoint reveals
            // which payment ids belong to other merchants.
            throw new IllegalArgumentException("Payment not found: " + paymentId);
        }
        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException("Only a successful payment can be refunded; payment "
                + paymentId + " is " + payment.getStatus());
        }

        long reserved = refundRepository.sumReservedAmountForPayment(paymentId, RefundStatus.FAILED);
        long remaining = payment.getAmount() - reserved;
        if (amount > remaining) {
            throw new IllegalStateException("Refund of " + amount
                + " exceeds the remaining refundable balance of " + remaining
                + " for payment " + paymentId);
        }

        try {
            return refundRepository.save(Refund.builder()
                .paymentId(paymentId)
                .merchantId(merchantId)
                .idempotencyKey(idempotencyKey)
                .amount(amount)
                .currency(payment.getCurrency())
                .reason(reason)
                .provider(payment.getSelectedProvider())
                .status(RefundStatus.CREATED)
                .build());
        } catch (DataIntegrityViolationException exception) {
            // Two requests raced on the same key and the unique index picked a winner.
            return refundRepository.findByMerchantIdAndIdempotencyKey(merchantId, idempotencyKey)
                .orElseThrow(() -> exception);
        }
    }

    @Transactional
    Refund markProcessing(Long refundId) {
        Refund refund = load(refundId);
        refund.transitionTo(RefundStatus.PROCESSING);
        return refundRepository.save(refund);
    }

    /**
     * Records the outcome and, on success, posts the compensating ledger transaction and the
     * outbox event in the same transaction as the status change.
     */
    @Transactional
    Refund settle(Long refundId, RefundStatus outcome, String providerRefundId,
                  String failureCode, String failureMessage, OutboxEvent event) {
        Refund refund = load(refundId);
        refund.transitionTo(outcome);
        refund.setProviderRefundId(providerRefundId);
        refund.setFailureCode(failureCode);
        refund.setFailureMessage(failureMessage);
        Refund saved = refundRepository.save(refund);

        if (outcome == RefundStatus.SUCCESS) {
            Payment payment = paymentRepository.findById(saved.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                    "Payment missing for refund " + saved.getId()));
            ledgerPoster.postRefund(payment, saved);
        }

        outboxEventRepository.save(event);
        log.info("Refund settled: id={} status={} amount={}",
            saved.getId(), saved.getStatus(), saved.getAmount());
        return saved;
    }

    @Transactional
    Refund resolveFromWebhook(String provider, String providerEventId, String payload,
                              Long refundId, RefundStatus outcome, String providerRefundId,
                              OutboxEvent event) {
        if (webhookRepository.findByProviderAndProviderEventId(provider, providerEventId).isPresent()) {
            return load(refundId);
        }

        Refund refund = load(refundId);
        if (!provider.equals(refund.getProvider())) {
            throw new IllegalStateException("Webhook provider does not own refund " + refundId);
        }
        if (refund.getStatus() != RefundStatus.UNKNOWN) {
            throw new IllegalStateException("Refund " + refundId + " is not awaiting provider resolution");
        }

        refund.transitionTo(outcome);
        refund.setProviderRefundId(providerRefundId);
        Refund saved = refundRepository.save(refund);

        if (outcome == RefundStatus.SUCCESS) {
            Payment payment = paymentRepository.findById(saved.getPaymentId())
                .orElseThrow(() -> new IllegalStateException(
                    "Payment missing for refund " + saved.getId()));
            ledgerPoster.postRefund(payment, saved);
        }

        webhookRepository.save(PaymentWebhook.builder()
            .provider(provider)
            .providerEventId(providerEventId)
            .paymentId(saved.getPaymentId())
            .eventType("refund.status")
            .payload(payload)
            .status("PROCESSED")
            .processedAt(java.time.Instant.now())
            .build());
        outboxEventRepository.save(event);
        return saved;
    }

    private Refund load(Long refundId) {
        return refundRepository.findById(refundId)
            .orElseThrow(() -> new IllegalArgumentException("Refund not found: " + refundId));
    }

    private static void assertReplayMatches(Refund existing, Long paymentId, Long amount) {
        if (!existing.getPaymentId().equals(paymentId) || !existing.getAmount().equals(amount)) {
            throw new IllegalArgumentException(
                "Idempotency key was already used for a different refund");
        }
    }
}
