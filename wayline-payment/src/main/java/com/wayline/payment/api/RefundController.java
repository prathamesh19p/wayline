package com.wayline.payment.api;

import com.wayline.payment.application.PaymentService;
import com.wayline.payment.application.RefundService;
import com.wayline.payment.domain.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
@RequestMapping("/api/v1/payments/{paymentId}/refunds")
@Slf4j
@RequiredArgsConstructor
@Tag(name = "Refunds", description = "Return money for a payment that already succeeded.")
public class RefundController {

    private final RefundService refundService;
    private final PaymentService paymentService;

    @Operation(
        summary = "Refund a payment, in whole or in part",
        description = """
            Returns money for a payment in `SUCCESS`. The original payment is never modified: the \
            refund is recorded separately and posts a compensating ledger transaction, so the \
            history of what was charged stays intact.

            Omit `amount` to refund the full remaining balance. A payment may be refunded several \
            times, but the total can never exceed what was charged \u2014 the check runs under a row \
            lock on the payment, so two concurrent requests cannot both slip past it.

            The `Idempotency-Key` header is mandatory. Replaying a key returns the original refund \
            rather than moving money twice.

            A refund whose provider call times out is left `UNKNOWN`, not `FAILED`, because the \
            money may already have been returned. It still counts against the refundable balance.""")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Refund recorded and attempted"),
        @ApiResponse(responseCode = "400",
            description = "Amount not positive, payment not found, or the idempotency key was "
                + "reused for a different refund"),
        @ApiResponse(responseCode = "409",
            description = "Payment is not in SUCCESS, or the amount exceeds the refundable balance"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "403", description = "Token lacks the MERCHANT role")
    })
    @PostMapping
    public ResponseEntity<RefundResponse> createRefund(
            @PathVariable Long paymentId,
            @Parameter(description = "Unique key identifying this refund attempt. Reuse it to "
                + "safely retry without refunding twice.", required = true,
                example = "b7e2d4a1-9f3c-4c8e-8a21-0d5f6c7b3e94")
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody(required = false) CreateRefundRequest request,
            Authentication authentication) {

        String merchantId = authentication.getName();
        CreateRefundRequest body = request == null ? new CreateRefundRequest() : request;
        Long amount = body.getAmount() == null
            ? remainingBalance(paymentId, merchantId)
            : body.getAmount();

        var refund = refundService.requestRefund(
            merchantId, idempotencyKey, paymentId, amount, body.getReason());

        return ResponseEntity.status(HttpStatus.CREATED).body(RefundResponse.from(refund));
    }

    @Operation(summary = "List a payment's refunds",
        description = "Every refund recorded against the payment, oldest first.")
    @GetMapping
    public ResponseEntity<List<RefundResponse>> listRefunds(
            @PathVariable Long paymentId, Authentication authentication) {
        // Resolve through the merchant-scoped lookup so one merchant cannot read another's refunds.
        requireOwnedPayment(paymentId, authentication.getName());
        return ResponseEntity.ok(refundService.getRefundsForPayment(paymentId).stream()
            .map(RefundResponse::from)
            .toList());
    }

    private Long remainingBalance(Long paymentId, String merchantId) {
        Payment payment = requireOwnedPayment(paymentId, merchantId);
        long reserved = refundService.getRefundsForPayment(paymentId).stream()
            .filter(refund -> refund.getStatus().reservesFunds())
            .mapToLong(refund -> refund.getAmount())
            .sum();
        return payment.getAmount() - reserved;
    }

    private Payment requireOwnedPayment(Long paymentId, String merchantId) {
        return paymentService.getPaymentForMerchant(paymentId, merchantId)
            .orElseThrow(() -> new IllegalArgumentException("Payment not found: " + paymentId));
    }
}
