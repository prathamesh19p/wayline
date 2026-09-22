package com.wayline.ledger.infrastructure;

import com.wayline.ledger.domain.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LedgerEntry entity.
 * Entries are immutable - only supports read operations.
 */
@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByLedgerTransactionIdOrderByCreatedAtAsc(Long ledgerTransactionId);
    List<LedgerEntry> findByAccountIdOrderByCreatedAtAsc(Long accountId);
}
