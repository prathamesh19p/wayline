package com.wayline.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.wayline.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for payment queries.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("merchantId")
    private String merchantId;

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("status")
    private PaymentStatus status;

    @JsonProperty("selectedProvider")
    private String selectedProvider;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("updatedAt")
    private Instant updatedAt;
}
