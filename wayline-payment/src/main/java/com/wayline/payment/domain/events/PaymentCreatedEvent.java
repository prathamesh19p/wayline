package com.wayline.payment.domain.events;

import com.wayline.common.event.DomainEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Event published when payment is created.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = true)
public class PaymentCreatedEvent extends DomainEvent {
    private Long paymentId;
    private String merchantId;
    private Long amount;
    private String currency;
    private String paymentMethod;

    public PaymentCreatedEvent(Long paymentId, String merchantId, Long amount, String currency, 
                              String paymentMethod, Instant occurredAt) {
        super(paymentId.toString(), occurredAt);
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethod = paymentMethod;
        this.setEventType("PaymentCreated");
    }
}
