package com.wayline.payment.domain.events;

import com.wayline.common.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when payment succeeds.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class PaymentSucceededEvent extends DomainEvent {
    private Long paymentId;
    private String provider;
    private String providerPaymentId;

    public PaymentSucceededEvent(Long paymentId, String provider, String providerPaymentId, Instant occurredAt) {
        super(paymentId.toString(), occurredAt);
        this.paymentId = paymentId;
        this.provider = provider;
        this.providerPaymentId = providerPaymentId;
        this.setEventType("PaymentSucceeded");
    }
}
