package com.wayline.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a payment.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("currency")
    private String currency;

    @JsonProperty("paymentMethod")
    private String paymentMethod;

    @JsonProperty("description")
    private String description;

    @JsonProperty("customerId")
    private String customerId;

    @JsonProperty("orderId")
    private String orderId;
}
