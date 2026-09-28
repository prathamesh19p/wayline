package com.wayline.payment.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.payment.domain.IdempotencyRecord;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.payment.infrastructure.IdempotencyRecordRepository;
import com.wayline.payment.infrastructure.PaymentAttemptRepository;
import com.wayline.payment.infrastructure.PaymentRepository;
import com.wayline.payment.infrastructure.PaymentStateHistoryRepository;
import com.wayline.payment.infrastructure.PaymentWebhookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentStateHistoryRepository stateHistoryRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private PaymentWebhookRepository paymentWebhookRepository;
    @Mock private OutboxEventRepository outboxEventRepository;
    @Mock private PaymentLedgerPoster ledgerPoster;

    @InjectMocks private PaymentService service;

    @Test
    void createsPaymentInCreatedState() {
        when(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("merchant", "key"))
            .thenReturn(Optional.empty());
        echoSavedPaymentWithId(1L);

        Payment payment = service.createPayment("merchant", "key", request(100L));

        assertEquals(1L, payment.getId());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
    }

    @Test
    void replayedIdempotencyKeyReturnsOriginalPaymentWithoutCreatingASecondOne() {
        Payment original = Payment.builder().id(1L).merchantId("merchant").amount(100L)
            .currency("INR").paymentMethod("CARD").status(PaymentStatus.SUCCESS).build();
        when(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("merchant", "key"))
            .thenReturn(Optional.of(recordFor(request(100L))));
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(original));

        Payment payment = service.createPayment("merchant", "key", request(100L));

        assertEquals(1L, payment.getId());
        assertEquals(PaymentStatus.SUCCESS, payment.getStatus());
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void reusingAnIdempotencyKeyWithADifferentAmountIsRejected() {
        when(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("merchant", "key"))
            .thenReturn(Optional.of(recordFor(request(100L))));

        assertThrows(IllegalArgumentException.class,
            () -> service.createPayment("merchant", "key", request(999L)));
    }

    @Test
    void aLateWebhookCannotMoveAPaymentOutOfATerminalState() {
        when(paymentRepository.findById(1L))
            .thenReturn(Optional.of(Payment.builder().id(1L).status(PaymentStatus.SUCCESS).build()));

        assertThrows(IllegalStateException.class,
            () -> service.transitionPaymentStatus(1L, PaymentStatus.FAILED, "late webhook", "WEBHOOK"));
    }

    @Test
    void outboxEventIsPersistedAlongsideTheStateChange() {
        when(paymentRepository.findById(1L))
            .thenReturn(Optional.of(Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build()));
        echoSavedPaymentWithId(1L);
        OutboxEvent event = event("PaymentSucceeded");

        service.transitionPaymentStatus(1L, PaymentStatus.SUCCESS, "provider ok", "PROVIDER", event);

        verify(outboxEventRepository).save(event);
    }

    @Test
    void reachingSuccessPostsTheCaptureToTheLedger() {
        Payment processing = Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(processing));
        echoSavedPaymentWithId(1L);

        service.transitionPaymentStatus(1L, PaymentStatus.SUCCESS, "provider ok", "PROVIDER");

        verify(ledgerPoster).postCapture(processing);
    }

    @Test
    void aFailedPaymentPostsNothingToTheLedger() {
        Payment processing = Payment.builder().id(1L).status(PaymentStatus.PROCESSING).build();
        when(paymentRepository.findById(1L)).thenReturn(Optional.of(processing));
        echoSavedPaymentWithId(1L);

        service.transitionPaymentStatus(1L, PaymentStatus.FAILED, "declined", "PROVIDER");

        verify(ledgerPoster, never()).postCapture(any(Payment.class));
    }

    @Test
    void aRejectedTransitionEmitsNoOutboxEvent() {
        when(paymentRepository.findById(1L))
            .thenReturn(Optional.of(Payment.builder().id(1L).status(PaymentStatus.SUCCESS).build()));
        OutboxEvent event = event("PaymentFailed");

        assertThrows(IllegalStateException.class,
            () -> service.transitionPaymentStatus(1L, PaymentStatus.FAILED, "late", "WEBHOOK", event));

        verify(outboxEventRepository, never()).save(any(OutboxEvent.class));
    }

    private static CreatePaymentRequest request(long amount) {
        return CreatePaymentRequest.builder()
            .amount(amount)
            .currency("INR")
            .paymentMethod("CARD")
            .build();
    }

    private static OutboxEvent event(String type) {
        return OutboxEvent.builder()
            .aggregateType("PAYMENT").aggregateId("1").eventType(type).payload("{}").build();
    }

    private void echoSavedPaymentWithId(long id) {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(id);
            return payment;
        });
    }

    private static IdempotencyRecord recordFor(CreatePaymentRequest request) {
        return IdempotencyRecord.builder()
            .merchantId("merchant")
            .idempotencyKey("key")
            .requestHash(hashOf(request))
            .paymentId(1L)
            .build();
    }

    /** Mirrors PaymentService#hashRequest so the tests read as intent rather than a literal digest. */
    private static String hashOf(CreatePaymentRequest request) {
        String combined = request.getAmount() + "|" + request.getCurrency() + "|" + request.getPaymentMethod();
        try {
            StringBuilder hex = new StringBuilder();
            for (byte b : MessageDigest.getInstance("SHA-256").digest(combined.getBytes(StandardCharsets.UTF_8))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
