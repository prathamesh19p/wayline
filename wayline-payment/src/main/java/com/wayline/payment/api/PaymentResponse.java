package com.wayline.payment.api;

import com.wayline.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "The current state of a payment.")
public class PaymentResponse {

    @Schema(description = "Wayline's identifier for the payment.", example = "1042")
    private Long id;

    @Schema(description = "Merchant that owns the payment.", example = "test-merchant")
    private String merchantId;

    @Schema(description = "Amount in the minor unit of the currency.", example = "12500")
    private Long amount;

    @Schema(example = "INR")
    private String currency;

    @Schema(example = "CARD")
    private String paymentMethod;

    @Schema(description = "UNKNOWN means the provider did not answer in time; the outcome is not "
        + "yet established and will be resolved by a later webhook.", example = "SUCCESS")
    private PaymentStatus status;

    @Schema(description = "Provider chosen by the routing layer for this payment.",
        example = "PROVIDER_A")
    private String selectedProvider;

    private Instant createdAt;

    private Instant updatedAt;
}
