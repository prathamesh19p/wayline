package com.wayline.payment.application;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payment creation request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentRequest {
    private Long amount;
    private String currency;
    private String paymentMethod;
    private String description;
    private String customerId;
    private String orderId;
}
