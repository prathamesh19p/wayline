package com.wayline.payment.domain;

/**
 * Lifecycle of a refund. Mirrors {@link PaymentStatus} deliberately: the same timeout reasoning
 * applies, so the two are read the same way.
 */
public enum RefundStatus {

    /** Recorded and validated, not yet sent to the provider. */
    CREATED,

    /** Sent to the provider, awaiting an outcome. */
    PROCESSING,

    /** The provider confirmed the money was returned. Terminal. */
    SUCCESS,

    /** The provider declined. No money moved. Terminal. */
    FAILED,

    /**
     * The provider did not answer in time. The refund may or may not have been applied, so it is
     * not treated as failed: retrying on a FAILED assumption is how a customer gets refunded
     * twice. Resolved later by a webhook or by reconciliation.
     */
    UNKNOWN;

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED;
    }

    /**
     * UNKNOWN counts against the payment's refundable balance even though it is not terminal,
     * because the money may already have left.
     */
    public boolean reservesFunds() {
        return this != FAILED;
    }

    public boolean canTransitionTo(RefundStatus next) {
        return switch (this) {
            case CREATED -> next == PROCESSING || next == FAILED;
            case PROCESSING -> next == SUCCESS || next == FAILED || next == UNKNOWN;
            case UNKNOWN -> next == SUCCESS || next == FAILED;
            case SUCCESS, FAILED -> false;
        };
    }
}
