package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "refunds", indexes = {
    @Index(name = "idx_refunds_payment_id", columnList = "payment_id"),
    @Index(name = "idx_refunds_status", columnList = "status"),
    @Index(name = "idx_refunds_created_at", columnList = "created_at")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 100)
    private String merchantId;

    @Column(nullable = false, length = 256)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Default
    private RefundStatus status = RefundStatus.CREATED;

    @Column(length = 500)
    private String reason;

    @Column(length = 100)
    private String provider;

    @Column(length = 256)
    private String providerRefundId;

    @Column(length = 50)
    private String failureCode;

    @Column(columnDefinition = "TEXT")
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Default
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    @Default
    private Long version = 0L;

    public void transitionTo(RefundStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new IllegalStateException(
                String.format("Cannot transition refund %d from %s to %s", id, status, next));
        }
        this.status = next;
        this.updatedAt = Instant.now();
    }
}
