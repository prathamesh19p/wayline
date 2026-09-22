package com.wayline.payment.domain.events;

import com.wayline.common.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when payment fails.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class PaymentFailedEvent extends DomainEvent {
    private Long paymentId;
    private String provider;
    private String failureCode;
    private String failureReason;

    public PaymentFailedEvent(Long paymentId, String provider, String failureCode, String failureReason, Instant occurredAt) {
        super(paymentId.toString(), occurredAt);
        this.paymentId = paymentId;
        this.provider = provider;
        this.failureCode = failureCode;
        this.failureReason = failureReason;
        this.setEventType("PaymentFailed");
    }
}
