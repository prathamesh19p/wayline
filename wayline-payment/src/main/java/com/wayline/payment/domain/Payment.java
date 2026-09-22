package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Payment entity representing a single payment request.
 *
 * Uses optimistic locking (version field) to prevent concurrent update conflicts.
 */
@Entity
@Table(name = "payments", indexes = {
    @Index(name = "idx_payments_merchant_id", columnList = "merchant_id"),
    @Index(name = "idx_payments_status", columnList = "status"),
    @Index(name = "idx_payments_created_at", columnList = "created_at"),
    @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String merchantId;

    @Column(nullable = false, length = 256)
    private String idempotencyKey;

    @Column(nullable = false)
    private Long amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 50)
    private String paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Default
    private PaymentStatus status = PaymentStatus.CREATED;

    @Column(length = 100)
    private String selectedProvider;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Default
    private Instant updatedAt = Instant.now();

    /**
     * Optimistic lock version for concurrent update protection.
     */
    @Version
    @Column(nullable = false)
    @Default
    private Long version = 0L;

    /**
     * Check if payment state transition is valid.
     *
     * @param newStatus Target status
     * @return true if transition is allowed
     */
    public boolean canTransitionTo(PaymentStatus newStatus) {
        return switch (this.status) {
            case CREATED -> newStatus == PaymentStatus.PROCESSING || newStatus == PaymentStatus.CANCELLED;
            case PROCESSING -> newStatus == PaymentStatus.SUCCESS || newStatus == PaymentStatus.FAILED || newStatus == PaymentStatus.UNKNOWN;
            case UNKNOWN -> newStatus == PaymentStatus.SUCCESS || newStatus == PaymentStatus.FAILED;
            case SUCCESS, FAILED, CANCELLED -> false; // Terminal states
        };
    }

    /**
     * Transition payment to new status.
     *
     * @param newStatus Target status
     * @throws IllegalStateException if transition is not allowed
     */
    public void transitionTo(PaymentStatus newStatus) {
        if (!canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid transition from %s to %s", this.status, newStatus)
            );
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
    }

    /**
     * Check if payment is in a terminal state.
     */
    public boolean isTerminal() {
        return status == PaymentStatus.SUCCESS || 
               status == PaymentStatus.FAILED || 
               status == PaymentStatus.CANCELLED;
    }
}
