package com.wayline.provider.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Provider payment response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProviderPaymentResponse {
    private String providerPaymentId;
    private String status; // SUCCESS, FAILED, UNKNOWN, PENDING
    private String failureCode;
    private String failureMessage;
    private Long amount;
    private String currency;
}
