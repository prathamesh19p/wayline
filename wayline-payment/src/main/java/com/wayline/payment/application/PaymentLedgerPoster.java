package com.wayline.payment.application;

import com.wayline.ledger.application.LedgerService;
import com.wayline.ledger.domain.LedgerEntry.EntryType;
import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.Refund;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Translates payment events into double-entry bookkeeping.
 *
 * <p>Two accounts are involved. {@code PROVIDER_CLEARING} is what the provider owes Wayline for
 * money it has collected but not yet settled. {@code MERCHANT_PAYABLE} is what Wayline owes the
 * merchant. A capture increases both; a refund reverses both by the refunded amount.
 *
 * <p>Callers must already be inside a transaction: posting is never allowed to land separately
 * from the state change that caused it.
 */
@Component
@RequiredArgsConstructor
public class PaymentLedgerPoster {

    private static final String PROVIDER_CLEARING = "PROVIDER_CLEARING";
    private static final String MERCHANT_PAYABLE = "MERCHANT_PAYABLE";

    private final LedgerService ledgerService;

    /**
     * Money collected from the customer and now owed to the merchant.
     */
    public void postCapture(Payment payment) {
        ledgerService.createTransaction("PAYMENT_CAPTURE", List.of(
            entry(PROVIDER_CLEARING, providerOf(payment), EntryType.DEBIT, payment.getAmount(),
                payment.getCurrency()),
            entry(MERCHANT_PAYABLE, payment.getMerchantId(), EntryType.CREDIT, payment.getAmount(),
                payment.getCurrency())
        ), payment.getId());
    }

    /**
     * The exact reverse of {@link #postCapture} for the refunded amount. The original entries are
     * left untouched, which is what makes this a compensating transaction rather than a correction.
     */
    public void postRefund(Payment payment, Refund refund) {
        ledgerService.createTransaction("PAYMENT_REFUND", List.of(
            entry(MERCHANT_PAYABLE, payment.getMerchantId(), EntryType.DEBIT, refund.getAmount(),
                refund.getCurrency()),
            entry(PROVIDER_CLEARING, providerOf(payment), EntryType.CREDIT, refund.getAmount(),
                refund.getCurrency())
        ), payment.getId());
    }

    private static String providerOf(Payment payment) {
        return payment.getSelectedProvider() == null ? "UNASSIGNED" : payment.getSelectedProvider();
    }

    private static LedgerService.EntryData entry(String accountType, String ownerId,
                                                 EntryType type, Long amount, String currency) {
        return new LedgerService.EntryData(accountType, ownerId, type, amount, currency);
    }
}
