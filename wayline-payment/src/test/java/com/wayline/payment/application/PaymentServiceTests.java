package com.wayline.payment.application;

import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.payment.infrastructure.IdempotencyRecordRepository;
import com.wayline.payment.infrastructure.PaymentAttemptRepository;
import com.wayline.payment.infrastructure.PaymentRepository;
import com.wayline.payment.infrastructure.PaymentStateHistoryRepository;
import com.wayline.payment.infrastructure.PaymentWebhookRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTests {
    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentAttemptRepository paymentAttemptRepository;
    @Mock private PaymentStateHistoryRepository stateHistoryRepository;
    @Mock private IdempotencyRecordRepository idempotencyRecordRepository;
    @Mock private PaymentWebhookRepository paymentWebhookRepository;

    @Test
    void createsPaymentWithInitialStatus() {
        PaymentService service = new PaymentService(
            paymentRepository,
            paymentAttemptRepository,
            stateHistoryRepository,
            idempotencyRecordRepository,
            paymentWebhookRepository
        );
        when(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("merchant", "key"))
            .thenReturn(Optional.empty());
        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment payment = invocation.getArgument(0);
            payment.setId(1L);
            return payment;
        });

        Payment payment = service.createPayment(
            "merchant",
            "key",
            CreatePaymentRequest.builder()
                .amount(100L)
                .currency("INR")
                .paymentMethod("CARD")
                .build()
        );

        assertEquals(1L, payment.getId());
        assertEquals(PaymentStatus.CREATED, payment.getStatus());
    }

    @Test
    void rejectsMismatchedIdempotentRequest() {
        PaymentService service = new PaymentService(
            paymentRepository,
            paymentAttemptRepository,
            stateHistoryRepository,
            idempotencyRecordRepository,
            paymentWebhookRepository
        );
        when(idempotencyRecordRepository.findByMerchantIdAndIdempotencyKey("merchant", "key"))
            .thenReturn(Optional.of(com.wayline.payment.domain.IdempotencyRecord.builder()
                .merchantId("merchant")
                .idempotencyKey("key")
                .requestHash("different")
                .paymentId(1L)
                .build()));

        assertThrows(IllegalArgumentException.class, () -> service.createPayment(
            "merchant",
            "key",
            CreatePaymentRequest.builder().amount(100L).currency("INR").paymentMethod("CARD").build()
        ));
    }
}
