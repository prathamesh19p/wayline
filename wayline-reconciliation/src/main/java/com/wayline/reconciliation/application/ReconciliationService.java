package com.wayline.reconciliation.application;

import com.wayline.payment.infrastructure.PaymentRepository;
import com.wayline.settlement.application.SettlementService;
import com.wayline.settlement.infrastructure.SettlementRepository;
import com.wayline.reconciliation.domain.ReconciliationRecord;
import com.wayline.reconciliation.infrastructure.ReconciliationRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for payment reconciliation.
 * Matches internal payments against provider settlements and detects mismatches.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReconciliationService {

    private final ReconciliationRecordRepository reconciliationRecordRepository;
    private final PaymentRepository paymentRepository;
    private final SettlementRepository settlementRepository;

    /**
     * Run reconciliation for a settlement.
     *
     * Algorithm:
     * 1. For each settlement transaction:
     *    a. Find matching internal payment by amount and date
     *    b. If found, verify status matches
     *    c. If not found, create MISMATCH record
     * 2. Find internal payments not in settlement
     * 3. Create exception records for all mismatches
     *
     * @param settlementId Settlement to reconcile
     * @param settlementRecords Records from provider settlement
     * @return List of reconciliation records
     */
    @Transactional
    public List<ReconciliationRecord> runReconciliation(Long settlementId, List<SettlementRecordData> settlementRecords) {
        log.info("Running reconciliation for settlement: {}", settlementId);

        settlementRepository.findById(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));

        List<ReconciliationRecord> records = new ArrayList<>();
        Set<Long> matchedPaymentIds = new HashSet<>();

        // Step 1: Match settlement records to payments
        for (SettlementRecordData record : settlementRecords) {
            ReconciliationRecord reconRecord = matchSettlementRecord(settlementId, record);
            records.add(reconRecord);

            if (reconRecord.getPaymentId() != null) {
                matchedPaymentIds.add(reconRecord.getPaymentId());
            }

            reconciliationRecordRepository.save(reconRecord);
        }

        settlementRepository.findById(settlementId).ifPresent(settlement ->
            paymentRepository.findByStatusAndCurrency(
                com.wayline.payment.domain.PaymentStatus.SUCCESS,
                settlement.getCurrency()
            ).stream()
                .filter(payment -> !matchedPaymentIds.contains(payment.getId()))
                .map(payment -> ReconciliationRecord.builder()
                    .settlementId(settlementId)
                    .paymentId(payment.getId())
                    .expectedAmount(payment.getAmount())
                    .actualAmount(null)
                    .difference(-payment.getAmount())
                    .status("MISMATCH")
                    .reason("Successful payment missing from settlement")
                    .build())
                .forEach(record -> {
                    records.add(record);
                    reconciliationRecordRepository.save(record);
                })
        );

        log.info("Reconciliation complete for settlement {}: {} records", settlementId, records.size());
        return records;
    }

    /**
     * Match single settlement record to internal payment.
     */
    private ReconciliationRecord matchSettlementRecord(Long settlementId, SettlementRecordData record) {
        // Try to find payment by amount (simplified - in production would use date, merchant, etc)
        if (record.paymentId == null || record.amount == null || record.amount < 0) {
            throw new IllegalArgumentException("Settlement paymentId and non-negative amount are required");
        }
        Optional<com.wayline.payment.domain.Payment> payment = paymentRepository.findById(record.paymentId);

        if (payment.isEmpty()) {
            log.warn("Payment not found for settlement record: {}", record.paymentId);

            return ReconciliationRecord.builder()
                .settlementId(settlementId)
                .paymentId(null)
                .expectedAmount(record.amount)
                .actualAmount(null)
                .status("MISMATCH")
                .reason("Payment not found")
                .build();
        }

        com.wayline.payment.domain.Payment p = payment.get();

        // Check if amounts match
        if (!p.getAmount().equals(record.amount)) {
            log.warn("Amount mismatch for payment {}: expected={} actual={}", 
                p.getId(), record.amount, p.getAmount());

            return ReconciliationRecord.builder()
                .settlementId(settlementId)
                .paymentId(p.getId())
                .expectedAmount(record.amount)
                .actualAmount(p.getAmount())
                .difference(p.getAmount() - record.amount)
                .status("MISMATCH")
                .reason("Amount mismatch")
                .build();
        }

        // Check if status is SUCCESS
        if (!p.getStatus().equals(com.wayline.payment.domain.PaymentStatus.SUCCESS)) {
            log.warn("Payment status mismatch for {}: expected=SUCCESS actual={}", 
                p.getId(), p.getStatus());

            return ReconciliationRecord.builder()
                .settlementId(settlementId)
                .paymentId(p.getId())
                .expectedAmount(record.amount)
                .actualAmount(p.getAmount())
                .status("MISMATCH")
                .reason("Payment status is " + p.getStatus() + ", expected SUCCESS")
                .build();
        }

        // All checks passed
        log.debug("Payment reconciled successfully: id={}", p.getId());

        return ReconciliationRecord.builder()
            .settlementId(settlementId)
            .paymentId(p.getId())
            .expectedAmount(record.amount)
            .actualAmount(p.getAmount())
            .status("MATCHED")
            .build();
    }

    /**
     * Get reconciliation records for settlement.
     */
    public List<ReconciliationRecord> getSettlementReconciliation(Long settlementId) {
        return reconciliationRecordRepository.findBySettlementId(settlementId);
    }

    /**
     * Get mismatched records for investigation.
     */
    public List<ReconciliationRecord> getMismatches() {
        return reconciliationRecordRepository.findByStatus("MISMATCH");
    }

    /**
     * Resolve mismatch with explanation.
     */
    @Transactional
    public ReconciliationRecord resolveMismatch(Long recordId, String resolution) {
        ReconciliationRecord record = reconciliationRecordRepository.findById(recordId)
            .orElseThrow(() -> new IllegalArgumentException("Record not found: " + recordId));

        record.resolve(resolution);
        return reconciliationRecordRepository.save(record);
    }

    /**
     * Settlement record data from provider file.
     */
    public static class SettlementRecordData {
        public Long paymentId;
        public Long amount;

        public SettlementRecordData(Long paymentId, Long amount) {
            this.paymentId = paymentId;
            this.amount = amount;
        }
    }
}
