package com.wayline.payment.api;

import com.wayline.payment.domain.Refund;
import com.wayline.payment.domain.RefundStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

import java.time.Instant;

@Builder
@Schema(description = "The current state of a refund.")
public record RefundResponse(

    @Schema(description = "Wayline's identifier for the refund.", example = "77")
    Long id,

    @Schema(description = "Payment this refund returns money for.", example = "1042")
    Long paymentId,

    @Schema(description = "Amount returned, in the minor unit of the currency.", example = "5000")
    Long amount,

    @Schema(example = "INR")
    String currency,

    @Schema(description = "UNKNOWN means the provider did not answer in time. The money may "
        + "already have been returned, so the refund is not retried automatically; it still "
        + "counts against the payment's refundable balance.", example = "SUCCESS")
    RefundStatus status,

    @Schema(example = "Customer cancelled within the return window")
    String reason,

    @Schema(description = "Provider that handled the original charge.", example = "PROVIDER_A")
    String provider,

    @Schema(description = "The provider's own identifier for this refund.")
    String providerRefundId,

    @Schema(description = "Set when the provider declined the refund.", example = "INSUFFICIENT_BALANCE")
    String failureCode,

    String failureMessage,

    Instant createdAt,

    Instant updatedAt
) {
    public static RefundResponse from(Refund refund) {
        return RefundResponse.builder()
            .id(refund.getId())
            .paymentId(refund.getPaymentId())
            .amount(refund.getAmount())
            .currency(refund.getCurrency())
            .status(refund.getStatus())
            .reason(refund.getReason())
            .provider(refund.getProvider())
            .providerRefundId(refund.getProviderRefundId())
            .failureCode(refund.getFailureCode())
            .failureMessage(refund.getFailureMessage())
            .createdAt(refund.getCreatedAt())
            .updatedAt(refund.getUpdatedAt())
            .build();
    }
}
