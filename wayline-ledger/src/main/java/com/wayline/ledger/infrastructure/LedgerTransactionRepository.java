package com.wayline.ledger.infrastructure;

import com.wayline.ledger.domain.LedgerTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for LedgerTransaction entity.
 */
@Repository
public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, Long> {
    List<LedgerTransaction> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
