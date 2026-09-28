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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefundLifecycleTests {

    private static final long PAYMENT_ID = 1L;
    private static final String MERCHANT = "merchant";

    @Mock private RefundRepository refundRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentWebhookRepository webhookRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PaymentLedgerPoster ledgerPoster;

    @InjectMocks private RefundLifecycle lifecycle;

    private Payment settledPayment(long amount) {
        return Payment.builder().id(PAYMENT_ID).merchantId(MERCHANT).amount(amount)
            .currency("INR").paymentMethod("CARD").status(PaymentStatus.SUCCESS)
            .selectedProvider("PROVIDER_A").build();
    }

    private void paymentIs(Payment payment) {
        when(paymentRepository.findByIdForUpdate(PAYMENT_ID)).thenReturn(Optional.of(payment));
    }

    private void alreadyRefunded(long total) {
        when(refundRepository.sumReservedAmountForPayment(PAYMENT_ID, RefundStatus.FAILED))
            .thenReturn(total);
    }

    private void echoSavedRefund() {
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund refund = invocation.getArgument(0);
            if (refund.getId() == null) {
                refund.setId(99L);
            }
            return refund;
        });
    }

    @Test
    void reservesARefundAgainstASuccessfulPayment() {
        paymentIs(settledPayment(10_000L));
        alreadyRefunded(0L);
        echoSavedRefund();

        Refund refund = lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 4_000L, "duplicate order");

        assertEquals(4_000L, refund.getAmount());
        assertEquals("INR", refund.getCurrency(), "refund must inherit the payment's currency");
        assertEquals(RefundStatus.CREATED, refund.getStatus());
    }

    @Test
    void refusesToRefundMoreThanWasCharged() {
        paymentIs(settledPayment(10_000L));
        alreadyRefunded(0L);

        assertThrows(IllegalStateException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 10_001L, null));
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void partialRefundsAccumulateAndCannotExceedTheTotal() {
        paymentIs(settledPayment(10_000L));
        alreadyRefunded(7_000L);

        assertThrows(IllegalStateException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 3_001L, null));
    }

    @Test
    void anUnknownRefundStillConsumesTheRefundableBalance() {
        // sumReservedAmountForPayment excludes only FAILED, so UNKNOWN counts. Refunding on the
        // assumption that a timed-out refund did not happen is how a customer is paid twice.
        paymentIs(settledPayment(10_000L));
        alreadyRefunded(10_000L);

        assertThrows(IllegalStateException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 1L, null));
    }

    @Test
    void refusesToRefundAPaymentThatNeverSucceeded() {
        Payment failed = settledPayment(10_000L);
        failed.setStatus(PaymentStatus.FAILED);
        paymentIs(failed);

        assertThrows(IllegalStateException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 100L, null));
    }

    @Test
    void refusesANonPositiveAmount() {
        assertThrows(IllegalArgumentException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 0L, null));
        assertThrows(IllegalArgumentException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, -5L, null));
    }

    @Test
    void anotherMerchantsPaymentIsReportedAsNotFound() {
        paymentIs(settledPayment(10_000L));

        // Must not be distinguishable from a missing payment, or this endpoint leaks which
        // payment ids exist.
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
            () -> lifecycle.reserve("someone-else", "key", PAYMENT_ID, 100L, null));
        assertEquals("Payment not found: 1", thrown.getMessage());
    }

    @Test
    void replayingAnIdempotencyKeyReturnsTheOriginalRefund() {
        Refund original = Refund.builder().id(77L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.SUCCESS).build();
        when(refundRepository.findByMerchantIdAndIdempotencyKey(MERCHANT, "key"))
            .thenReturn(Optional.of(original));

        Refund refund = lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 4_000L, null);

        assertEquals(77L, refund.getId());
        verify(refundRepository, never()).save(any(Refund.class));
    }

    @Test
    void replayingAnIdempotencyKeyWithADifferentAmountIsRejected() {
        Refund original = Refund.builder().id(77L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.SUCCESS).build();
        when(refundRepository.findByMerchantIdAndIdempotencyKey(MERCHANT, "key"))
            .thenReturn(Optional.of(original));

        assertThrows(IllegalArgumentException.class,
            () -> lifecycle.reserve(MERCHANT, "key", PAYMENT_ID, 9_999L, null));
    }

    @Test
    void settlingSuccessfullyPostsTheCompensatingLedgerTransaction() {
        Refund processing = Refund.builder().id(99L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.PROCESSING).build();
        when(refundRepository.findById(99L)).thenReturn(Optional.of(processing));
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(settledPayment(10_000L)));
        echoSavedRefund();
        OutboxEvent event = OutboxEvent.builder().aggregateType("REFUND").aggregateId("99")
            .eventType("RefundSucceeded").payload("{}").build();

        Refund settled = lifecycle.settle(99L, RefundStatus.SUCCESS, "prov_ref_1", null, null, event);

        assertEquals(RefundStatus.SUCCESS, settled.getStatus());
        verify(ledgerPoster).postRefund(any(Payment.class), eq(settled));
        verify(outboxEventRepository).save(event);
    }

    @Test
    void aFailedRefundPostsNoLedgerEntriesButStillEmitsAnEvent() {
        Refund processing = Refund.builder().id(99L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.PROCESSING).build();
        when(refundRepository.findById(99L)).thenReturn(Optional.of(processing));
        echoSavedRefund();
        OutboxEvent event = OutboxEvent.builder().aggregateType("REFUND").aggregateId("99")
            .eventType("RefundFailed").payload("{}").build();

        lifecycle.settle(99L, RefundStatus.FAILED, null, "DECLINED", "declined by issuer", event);

        verify(ledgerPoster, never()).postRefund(any(Payment.class), any(Refund.class));
        verify(outboxEventRepository).save(event);
    }

    @Test
    void aTimedOutRefundIsLeftUnknownAndPostsNoLedgerEntries() {
        Refund processing = Refund.builder().id(99L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.PROCESSING).build();
        when(refundRepository.findById(99L)).thenReturn(Optional.of(processing));
        echoSavedRefund();
        OutboxEvent event = OutboxEvent.builder().aggregateType("REFUND").aggregateId("99")
            .eventType("RefundUnknown").payload("{}").build();

        Refund settled = lifecycle.settle(99L, RefundStatus.UNKNOWN, null, "TIMEOUT",
            "no response", event);

        assertEquals(RefundStatus.UNKNOWN, settled.getStatus());
        // Money may or may not have moved, so nothing is asserted in the ledger yet.
        verify(ledgerPoster, never()).postRefund(any(Payment.class), any(Refund.class));
    }

    @Test
    void aRefundWebhookResolvesAnUnknownRefundAndPostsTheLedgerEntry() {
        Refund unknown = Refund.builder().id(99L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .provider("PROVIDER_A").amount(4_000L).currency("INR")
            .status(RefundStatus.UNKNOWN).build();
        when(refundRepository.findById(99L)).thenReturn(Optional.of(unknown));
        when(webhookRepository.findByProviderAndProviderEventId("PROVIDER_A", "event-1"))
            .thenReturn(Optional.empty());
        when(paymentRepository.findById(PAYMENT_ID)).thenReturn(Optional.of(settledPayment(10_000L)));
        echoSavedRefund();
        OutboxEvent event = OutboxEvent.builder().aggregateType("REFUND").aggregateId("99")
            .eventType("RefundSucceeded").payload("{}").build();

        Refund resolved = lifecycle.resolveFromWebhook("PROVIDER_A", "event-1", "{}", 99L,
            RefundStatus.SUCCESS, "provider-refund-1", event);

        assertEquals(RefundStatus.SUCCESS, resolved.getStatus());
        assertEquals("provider-refund-1", resolved.getProviderRefundId());
        verify(ledgerPoster).postRefund(any(Payment.class), eq(resolved));
        verify(webhookRepository).save(any(PaymentWebhook.class));
        verify(outboxEventRepository).save(event);
    }

    @Test
    void aSettledRefundCannotBeSettledAgain() {
        Refund done = Refund.builder().id(99L).paymentId(PAYMENT_ID).merchantId(MERCHANT)
            .amount(4_000L).currency("INR").status(RefundStatus.SUCCESS).build();
        when(refundRepository.findById(99L)).thenReturn(Optional.of(done));
        OutboxEvent event = OutboxEvent.builder().aggregateType("REFUND").aggregateId("99")
            .eventType("RefundFailed").payload("{}").build();

        assertThrows(IllegalStateException.class,
            () -> lifecycle.settle(99L, RefundStatus.FAILED, null, "X", "y", event));
    }
}
