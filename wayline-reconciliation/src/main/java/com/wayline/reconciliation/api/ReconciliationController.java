package com.wayline.reconciliation.api;

import com.wayline.reconciliation.application.ReconciliationService;
import com.wayline.reconciliation.domain.ReconciliationRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {
    private final ReconciliationService reconciliationService;

    @PostMapping("/settlements/{settlementId}/run")
    public ResponseEntity<List<ReconciliationRecord>> run(
        @PathVariable Long settlementId,
        @RequestBody List<SettlementRecordRequest> settlementRecords
    ) {
        List<ReconciliationService.SettlementRecordData> records = settlementRecords.stream()
            .map(record -> new ReconciliationService.SettlementRecordData(record.paymentId(), record.amount()))
            .toList();
        return ResponseEntity.ok(reconciliationService.runReconciliation(settlementId, records));
    }

    @GetMapping("/settlements/{settlementId}")
    public ResponseEntity<List<ReconciliationRecord>> getSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(reconciliationService.getSettlementReconciliation(settlementId));
    }

    @GetMapping("/mismatches")
    public ResponseEntity<List<ReconciliationRecord>> getMismatches() {
        return ResponseEntity.ok(reconciliationService.getMismatches());
    }

    @PostMapping("/{recordId}/resolve")
    public ResponseEntity<ReconciliationRecord> resolve(
        @PathVariable Long recordId,
        @RequestBody ResolveRequest request
    ) {
        return ResponseEntity.ok(reconciliationService.resolveMismatch(recordId, request.resolution()));
    }

    public record SettlementRecordRequest(Long paymentId, Long amount) {}
    public record ResolveRequest(String resolution) {}
}
