package com.wayline.payment.domain;

/**
 * Payment status states.
 *
 * Flow:
 * CREATED → PROCESSING → SUCCESS/FAILED
 * CREATED → PROCESSING → UNKNOWN → SUCCESS/FAILED (via webhook/status inquiry)
 * CREATED → CANCELLED
 */
public enum PaymentStatus {
    /**
     * Payment has been created but not yet sent to provider.
     */
    CREATED,

    /**
     * Payment is being processed by the provider.
     */
    PROCESSING,

    /**
     * Payment succeeded.
     */
    SUCCESS,

    /**
     * Payment failed.
     */
    FAILED,

    /**
     * Provider outcome is unknown (timeout, lost response, etc).
     * Awaiting webhook, status inquiry, or reconciliation.
     */
    UNKNOWN,

    /**
     * Payment was cancelled by merchant.
     */
    CANCELLED
}
