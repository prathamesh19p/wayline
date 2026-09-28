package com.wayline.payment.api;

import com.wayline.payment.application.CreatePaymentRequest;
import com.wayline.payment.application.PaymentOrchestrationService;
import com.wayline.payment.application.PaymentService;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentAttempt;
import com.wayline.payment.domain.PaymentStateHistory;
import com.wayline.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Create payments and inspect their full history.")
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentOrchestrationService paymentOrchestrationService;

    @Operation(
        summary = "Create and attempt a payment",
        description = """
            Creates the payment, selects a healthy provider and attempts the charge synchronously. \
            The response carries whatever state the payment reached.

            The `Idempotency-Key` header is mandatory. Replaying the same key returns the original \
            payment untouched. Reusing a key with a different amount, currency or payment method \
            is rejected, which prevents a retried request from silently charging a new figure.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Payment created and attempted"),
        @ApiResponse(responseCode = "400",
            description = "Invalid request, or the idempotency key was reused with different values",
            content = @Content(schema = @Schema(ref = "#/components/schemas/ApiError"))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token",
            content = @Content),
        @ApiResponse(responseCode = "403", description = "Token lacks the MERCHANT role",
            content = @Content)
    })
    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Parameter(description = "Unique key identifying this creation attempt. Reuse it to "
                + "safely retry without double-charging.", required = true,
                example = "a3f1c9d2-6b4e-4f1a-9c3d-2e8b7a0f5c11")
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

    @Operation(summary = "Fetch a payment",
        description = "Returns the payment only if it belongs to the authenticated merchant.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Payment found"),
        @ApiResponse(responseCode = "400", description = "No such payment for this merchant",
            content = @Content)
    })
    @GetMapping("/{id}")    public ResponseEntity<PaymentResponse> getPayment(@PathVariable Long id, Authentication authentication) {
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

    @Operation(
        summary = "Fetch the full audit trail for a payment",
        description = """
            Returns every state transition, provider attempt and webhook recorded against the \
            payment, in order. This is the endpoint to reach for when investigating a disputed or \
            stuck transaction: it shows which providers were tried, what each returned, and why \
            the payment is in its current state.""")
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

