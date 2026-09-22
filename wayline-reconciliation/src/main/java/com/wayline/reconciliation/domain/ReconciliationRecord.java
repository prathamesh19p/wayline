package com.wayline.reconciliation.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Reconciliation record matching internal payment against provider settlement.
 */
@Entity
@Table(name = "reconciliation_records", indexes = {
    @Index(name = "idx_reconciliation_records_settlement_id", columnList = "settlement_id"),
    @Index(name = "idx_reconciliation_records_payment_id", columnList = "payment_id"),
    @Index(name = "idx_reconciliation_records_status", columnList = "status"),
    @Index(name = "idx_reconciliation_records_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long settlementId; // Settlement this reconciliation belongs to

    private Long paymentId; // Associated payment (may be null if payment not found)

    @Column(nullable = false)
    private Long expectedAmount; // Amount from settlement

    private Long actualAmount; // Amount from payment

    private Long difference; // actualAmount - expectedAmount

    @Column(nullable = false, length = 20)
    private String status; // MATCHED, MISMATCH, INVESTIGATING, RESOLVED

    @Column(columnDefinition = "TEXT")
    private String reason; // Mismatch reason

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    private Instant resolvedAt; // When mismatch was resolved

    /**
     * Check if amounts match.
     */
    public boolean isMatched() {
        return actualAmount != null && expectedAmount.equals(actualAmount);
    }

    /**
     * Mark as resolved.
     */
    public void resolve(String resolution) {
        this.status = "RESOLVED";
        this.reason = resolution;
        this.resolvedAt = Instant.now();
    }
}
