package com.wayline.payment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A request to return money for a payment that already succeeded.")
public class CreateRefundRequest {

    @Schema(
        description = "Amount to return, in the minor unit of the payment's currency. Omit to "
            + "refund the full remaining refundable balance. Partial refunds are supported, and "
            + "the sum of a payment's refunds can never exceed what was charged.",
        example = "5000", minimum = "1")
    private Long amount;

    @Schema(description = "Why the money is being returned. Retained for investigation.",
        example = "Customer cancelled within the return window")
    private String reason;
}
