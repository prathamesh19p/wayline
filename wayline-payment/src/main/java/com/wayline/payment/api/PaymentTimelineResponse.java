package com.wayline.payment.api;

import com.wayline.payment.domain.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Complete payment timeline for operational investigation.
 * Shows the full lifecycle of a payment including state changes,
 * provider attempts, webhooks, and reconciliation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentTimelineResponse {

    private PaymentResponse payment;

    private List<StateTransition> stateTransitions;

    private List<ProviderAttempt> providerAttempts;

    private List<WebhookEvent> webhooks;

    private List<LedgerTransaction> ledgerTransactions;

    private SettlementInfo settlement;

    private ReconciliationInfo reconciliation;

    /**
     * Single state transition in payment lifecycle.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateTransition {
        public PaymentStatus fromState;

        public PaymentStatus toState;

        public String reason;

        public String source;

        public Instant timestamp;
    }

    /**
     * Provider attempt details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderAttempt {
        public Integer attemptNumber;

        public String provider;

        public String providerPaymentId;

        public String status;

        public Instant requestTime;

        public Instant responseTime;

        public String failureCode;

        public String failureMessage;
    }

    /**
     * Webhook event received from provider.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookEvent {
        public String provider;

        public String providerEventId;

        public String eventType;

        public Instant receivedAt;

        public Instant processedAt;

        public String status;
    }

    /**
     * Ledger transaction for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerTransaction {
        public Long id;

        public String transactionType;

        public List<LedgerEntry> entries;

        public Instant createdAt;
    }

    /**
     * Individual ledger entry (debit/credit).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntry {
        public String account;

        public String entryType;

        public Long amount;

        public String currency;
    }

    /**
     * Settlement information for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementInfo {
        public Long settlementId;

        public String provider;

        public String settlementDate;

        public String status;
    }

    /**
     * Reconciliation information for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationInfo {
        public Long recordId;

        public String status;

        public Long expectedAmount;

        public Long actualAmount;

        public Long difference;

        public String reason;
    }
}
