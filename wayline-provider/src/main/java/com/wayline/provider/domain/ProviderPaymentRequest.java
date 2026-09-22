package com.wayline.provider.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider payment request.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderPaymentRequest {
    private Long amount;
    private String currency;
    private String paymentMethod;
    private String merchantId;
    private String orderId;
    private String description;
    private String idempotencyKey;
}
