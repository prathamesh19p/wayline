package com.wayline.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ledger transaction representing one logical financial transaction.
 * Must have balancing debit and credit entries.
 */
@Entity
@Table(name = "ledger_transactions", indexes = {
    @Index(name = "idx_ledger_transactions_payment_id", columnList = "payment_id"),
    @Index(name = "idx_ledger_transactions_created_at", columnList = "created_at"),
    @Index(name = "idx_ledger_transactions_reference_id", columnList = "reference_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long paymentId; // Associated payment ID (optional)

    @Column(nullable = false, length = 50)
    private String transactionType; // PAYMENT, REFUND, FEE, SETTLEMENT, etc

    @Column(length = 256)
    private String referenceId; // External reference if applicable

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}
