package com.wayline.payment.domain.events;

import com.wayline.common.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when payment outcome is unknown (e.g., provider timeout).
 * Awaiting webhook, status inquiry, or reconciliation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class PaymentUnknownEvent extends DomainEvent {
    private Long paymentId;
    private String provider;
    private String reason;

    public PaymentUnknownEvent(Long paymentId, String provider, String reason, Instant occurredAt) {
        super(paymentId.toString(), occurredAt);
        this.paymentId = paymentId;
        this.provider = provider;
        this.reason = reason;
        this.setEventType("PaymentUnknown");
    }
}
