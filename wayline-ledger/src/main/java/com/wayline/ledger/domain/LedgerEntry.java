package com.wayline.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Immutable ledger entry (debit or credit).
 * Never update or delete ledger entries.
 * Corrections are done via compensating transactions.
 */
@Entity
@Table(name = "ledger_entries", indexes = {
    @Index(name = "idx_ledger_entries_transaction_id", columnList = "ledger_transaction_id"),
    @Index(name = "idx_ledger_entries_account_id", columnList = "account_id"),
    @Index(name = "idx_ledger_entries_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ledgerTransactionId;

    @Column(nullable = false)
    private Long accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EntryType entryType; // DEBIT, CREDIT

    @Column(nullable = false)
    private Long amount; // In smallest currency unit (paisa for INR)

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @PreUpdate
    public void preventUpdate() {
        throw new IllegalStateException("Ledger entries are immutable and cannot be updated");
    }

    @PreRemove
    public void preventDelete() {
        throw new IllegalStateException("Ledger entries are immutable and cannot be deleted");
    }

    public enum EntryType {
        DEBIT, CREDIT
    }
}
