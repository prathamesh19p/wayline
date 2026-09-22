package com.wayline.settlement.application;

import com.wayline.common.outbox.OutboxEvent;
import com.wayline.common.outbox.OutboxEventRepository;
import com.wayline.settlement.domain.Settlement;
import com.wayline.settlement.infrastructure.SettlementRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing payment settlements.
 * Handles provider settlement import and processing.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Create or update settlement.
     */
    @Transactional
    public Settlement importSettlement(String provider, String settlementReference, LocalDate settlementDate,
                                      String currency, Long grossAmount, Long fees) {
        log.info("Importing settlement: provider={} reference={} date={} amount={}",
            provider, settlementReference, settlementDate, grossAmount);

        // Check if settlement already exists
        Optional<Settlement> existing = settlementRepository
            .findByProviderAndSettlementReference(provider, settlementReference);

        if (existing.isPresent()) {
            log.warn("Settlement already exists: {}", settlementReference);
            return existing.get();
        }

        if (provider == null || provider.isBlank() || settlementReference == null || settlementReference.isBlank()
            || settlementDate == null || currency == null || currency.length() != 3
            || grossAmount == null || fees == null) {
            throw new IllegalArgumentException("Provider, reference, date, currency, gross amount, and fees are required");
        }
        // Validate amounts
        if (grossAmount < 0 || fees < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }

        if (fees > grossAmount) {
            throw new IllegalArgumentException("Fees cannot exceed gross amount");
        }

        // Create settlement
        Settlement settlement = Settlement.builder()
            .provider(provider)
            .settlementReference(settlementReference)
            .settlementDate(settlementDate)
            .currency(currency)
            .grossAmount(grossAmount)
            .fees(fees)
            .netAmount(grossAmount - fees)
            .status("PENDING")
            .build();

        Settlement saved = settlementRepository.save(settlement);

        // Create outbox event
        createOutboxEvent(saved);

        log.info("Settlement imported: id={}", saved.getId());
        return saved;
    }

    /**
     * Get settlement by ID.
     */
    public Optional<Settlement> getSettlement(Long id) {
        return settlementRepository.findById(id);
    }

    /**
     * Get settlements by status.
     */
    public List<Settlement> getSettlementsByStatus(String status) {
        return settlementRepository.findByStatus(status);
    }

    /**
     * Mark settlement as processing.
     */
    @Transactional
    public Settlement markAsProcessing(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));

        settlement.setStatus("PROCESSING");
        return settlementRepository.save(settlement);
    }

    /**
     * Mark settlement as completed.
     */
    @Transactional
    public Settlement markAsCompleted(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new IllegalArgumentException("Settlement not found: " + settlementId));

        settlement.setStatus("COMPLETED");
        settlement.setProcessedAt(Instant.now());
        return settlementRepository.save(settlement);
    }

    /**
     * Create outbox event for settlement.
     */
    private void createOutboxEvent(Settlement settlement) {
        try {
            String payload = objectMapper.writeValueAsString(settlement);

            OutboxEvent event = OutboxEvent.builder()
                .aggregateType("SETTLEMENT")
                .aggregateId(settlement.getId().toString())
                .eventType("SettlementReceived")
                .payload(payload)
                .status("PENDING")
                .build();

            outboxEventRepository.save(event);
        } catch (Exception e) {
            log.error("Failed to create outbox event for settlement", e);
            throw new RuntimeException("Failed to create outbox event", e);
        }
    }
}
