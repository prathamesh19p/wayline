package com.wayline.settlement.api;

import com.wayline.settlement.application.SettlementService;
import com.wayline.settlement.domain.Settlement;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/v1/settlements")
@RequiredArgsConstructor
public class SettlementController {
    private final SettlementService settlementService;

    @PostMapping("/import")
    public ResponseEntity<Settlement> importSettlement(@RequestBody ImportSettlementRequest request) {
        Settlement settlement = settlementService.importSettlement(
            request.provider(),
            request.settlementReference(),
            request.settlementDate(),
            request.currency(),
            request.grossAmount(),
            request.fees()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(settlement);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Settlement> getSettlement(@PathVariable Long id) {
        return settlementService.getSettlement(id)
            .map(ResponseEntity::ok)
            .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<Settlement>> getByStatus(@RequestParam(defaultValue = "PENDING") String status) {
        return ResponseEntity.ok(settlementService.getSettlementsByStatus(status));
    }

    @PostMapping("/{id}/processing")
    public ResponseEntity<Settlement> markProcessing(@PathVariable Long id) {
        return ResponseEntity.ok(settlementService.markAsProcessing(id));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<Settlement> markCompleted(@PathVariable Long id) {
        return ResponseEntity.ok(settlementService.markAsCompleted(id));
    }

    public record ImportSettlementRequest(
        String provider,
        String settlementReference,
        LocalDate settlementDate,
        String currency,
        Long grossAmount,
        Long fees
    ) {}
}
