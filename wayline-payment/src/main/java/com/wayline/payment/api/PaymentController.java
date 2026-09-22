package com.wayline.payment.api;

import com.wayline.payment.application.CreatePaymentRequest;
import com.wayline.payment.application.PaymentService;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentAttempt;
import com.wayline.payment.domain.PaymentStateHistory;
import com.wayline.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API controller for payment operations.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final com.wayline.payment.application.PaymentOrchestrationService paymentOrchestrationService;

    /**
     * Create a new payment.
     * Idempotent using Idempotency-Key header.
     *
     * @param idempotencyKey Unique request key header
     * @param request Payment creation request
     * @param authentication Current authenticated user
     * @return Payment response with ID and status
     */
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody CreatePaymentRequest request,
            Authentication authentication) {
        log.info("Creating payment with idempotency key: {}", idempotencyKey);
        
        String merchantId = authentication.getName();
        Payment payment = paymentService.createPayment(merchantId, idempotencyKey, request);
        if (payment.getStatus() == PaymentStatus.CREATED) {
            payment = paymentOrchestrationService.process(payment);
        }
        
        PaymentResponse response = PaymentResponse.builder()
            .id(payment.getId())
            .merchantId(payment.getMerchantId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .status(payment.getStatus())
            .selectedProvider(payment.getSelectedProvider())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Get payment by ID.
     *
     * @param id Payment ID
     * @return Payment details
     */
    @GetMapping("/{id}")
    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id, Authentication authentication) {
        log.info("Retrieving payment: {}", id);
        
        Payment payment = paymentService.getPaymentForMerchant(id, authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id));

        PaymentResponse response = PaymentResponse.builder()
            .id(payment.getId())
            .merchantId(payment.getMerchantId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .status(payment.getStatus())
            .selectedProvider(payment.getSelectedProvider())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get complete payment timeline with all state changes, attempts, and webhooks.
     * Used for operational investigation.
     *
     * @param id Payment ID
     * @return Payment timeline response
     */
    @GetMapping("/{id}/timeline")
    public ResponseEntity<PaymentTimelineResponse> getPaymentTimeline(@PathVariable Long id, Authentication authentication) {
        log.info("Retrieving payment timeline: {}", id);
        
        Payment payment = paymentService.getPaymentForMerchant(id, authentication.getName())
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + id));

        List<PaymentAttempt> attempts = paymentService.getPaymentAttempts(id);
        List<PaymentStateHistory> stateHistory = paymentService.getStateHistory(id);

        PaymentResponse paymentResponse = PaymentResponse.builder()
            .id(payment.getId())
            .merchantId(payment.getMerchantId())
            .amount(payment.getAmount())
            .currency(payment.getCurrency())
            .paymentMethod(payment.getPaymentMethod())
            .status(payment.getStatus())
            .selectedProvider(payment.getSelectedProvider())
            .createdAt(payment.getCreatedAt())
            .updatedAt(payment.getUpdatedAt())
            .build();

        PaymentTimelineResponse timeline = PaymentTimelineResponse.builder()
            .payment(paymentResponse)
            .stateTransitions(stateHistory.stream()
                .map(h -> new PaymentTimelineResponse.StateTransition(
                    PaymentStatus.valueOf(h.getFromState() != null ? h.getFromState() : "CREATED"),
                    PaymentStatus.valueOf(h.getToState()),
                    h.getReason(),
                    h.getSource(),
                    h.getCreatedAt()
                ))
                .toList())
            .providerAttempts(attempts.stream()
                .map(a -> new PaymentTimelineResponse.ProviderAttempt(
                    a.getAttemptNumber(),
                    a.getProvider(),
                    a.getProviderPaymentId(),
                    a.getStatus(),
                    a.getRequestTime(),
                    a.getResponseTime(),
                    a.getFailureCode(),
                    a.getFailureMessage()
                ))
                .toList())
            .build();

        return ResponseEntity.ok(timeline);
    }
}

