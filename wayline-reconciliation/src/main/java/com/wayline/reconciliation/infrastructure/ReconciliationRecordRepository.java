package com.wayline.reconciliation.infrastructure;

import com.wayline.reconciliation.domain.ReconciliationRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ReconciliationRecord entity.
 */
@Repository
public interface ReconciliationRecordRepository extends JpaRepository<ReconciliationRecord, Long> {
    List<ReconciliationRecord> findBySettlementId(Long settlementId);
    List<ReconciliationRecord> findByStatus(String status);
    List<ReconciliationRecord> findByPaymentId(Long paymentId);
}
