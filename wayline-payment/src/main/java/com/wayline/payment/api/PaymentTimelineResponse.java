package com.wayline.payment.api;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    @JsonProperty("payment")
    private PaymentResponse payment;

    @JsonProperty("stateTransitions")
    private List<StateTransition> stateTransitions;

    @JsonProperty("providerAttempts")
    private List<ProviderAttempt> providerAttempts;

    @JsonProperty("webhooks")
    private List<WebhookEvent> webhooks;

    @JsonProperty("ledgerTransactions")
    private List<LedgerTransaction> ledgerTransactions;

    @JsonProperty("settlement")
    private SettlementInfo settlement;

    @JsonProperty("reconciliation")
    private ReconciliationInfo reconciliation;

    /**
     * Single state transition in payment lifecycle.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StateTransition {
        @JsonProperty("fromState")
        public PaymentStatus fromState;

        @JsonProperty("toState")
        public PaymentStatus toState;

        @JsonProperty("reason")
        public String reason;

        @JsonProperty("source")
        public String source;

        @JsonProperty("timestamp")
        public Instant timestamp;
    }

    /**
     * Provider attempt details.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProviderAttempt {
        @JsonProperty("attemptNumber")
        public Integer attemptNumber;

        @JsonProperty("provider")
        public String provider;

        @JsonProperty("providerPaymentId")
        public String providerPaymentId;

        @JsonProperty("status")
        public String status;

        @JsonProperty("requestTime")
        public Instant requestTime;

        @JsonProperty("responseTime")
        public Instant responseTime;

        @JsonProperty("failureCode")
        public String failureCode;

        @JsonProperty("failureMessage")
        public String failureMessage;
    }

    /**
     * Webhook event received from provider.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WebhookEvent {
        @JsonProperty("provider")
        public String provider;

        @JsonProperty("providerEventId")
        public String providerEventId;

        @JsonProperty("eventType")
        public String eventType;

        @JsonProperty("receivedAt")
        public Instant receivedAt;

        @JsonProperty("processedAt")
        public Instant processedAt;

        @JsonProperty("status")
        public String status;
    }

    /**
     * Ledger transaction for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerTransaction {
        @JsonProperty("id")
        public Long id;

        @JsonProperty("transactionType")
        public String transactionType;

        @JsonProperty("entries")
        public List<LedgerEntry> entries;

        @JsonProperty("createdAt")
        public Instant createdAt;
    }

    /**
     * Individual ledger entry (debit/credit).
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LedgerEntry {
        @JsonProperty("account")
        public String account;

        @JsonProperty("entryType")
        public String entryType;

        @JsonProperty("amount")
        public Long amount;

        @JsonProperty("currency")
        public String currency;
    }

    /**
     * Settlement information for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SettlementInfo {
        @JsonProperty("settlementId")
        public Long settlementId;

        @JsonProperty("provider")
        public String provider;

        @JsonProperty("settlementDate")
        public String settlementDate;

        @JsonProperty("status")
        public String status;
    }

    /**
     * Reconciliation information for payment.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReconciliationInfo {
        @JsonProperty("recordId")
        public Long recordId;

        @JsonProperty("status")
        public String status;

        @JsonProperty("expectedAmount")
        public Long expectedAmount;

        @JsonProperty("actualAmount")
        public Long actualAmount;

        @JsonProperty("difference")
        public Long difference;

        @JsonProperty("reason")
        public String reason;
    }
}
