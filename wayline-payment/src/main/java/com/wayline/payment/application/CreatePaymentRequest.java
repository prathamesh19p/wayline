package com.wayline.payment.application;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "A request to charge a merchant's customer.")
public class CreatePaymentRequest {

    @Schema(description = "Amount in the minor unit of the currency. 12500 with currency INR is 125.00 INR.",
        example = "12500", requiredMode = Schema.RequiredMode.REQUIRED, minimum = "1")
    private Long amount;

    @Schema(description = "ISO 4217 currency code.", example = "INR",
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String currency;

    @Schema(description = "Instrument to charge. Determines which providers are eligible.",
        example = "CARD", allowableValues = {"CARD", "UPI", "NETBANKING", "WALLET"},
        requiredMode = Schema.RequiredMode.REQUIRED)
    private String paymentMethod;

    @Schema(description = "Free-text description retained for operational investigation.",
        example = "Order #4417 - annual subscription")
    private String description;

    @Schema(description = "Merchant's own identifier for the paying customer.", example = "cust_8812")
    private String customerId;

    @Schema(description = "Merchant's own order reference.", example = "order_4417")
    private String orderId;
}
