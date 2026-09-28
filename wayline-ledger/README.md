# wayline-ledger

Double-entry bookkeeping. This is the financial record of the system.

## Rules enforced in code

- Every transaction must balance: total debits equal total credits, or `LedgerService` rejects
  it. There is no path that writes an unbalanced transaction.
- Entries are **immutable**. `LedgerEntry` throws from `@PreUpdate` and `@PreRemove`, so a
  correction must be a new compensating transaction rather than an edit. The history of what was
  believed, and when, survives.
- Amounts are `Long` in the currency's minor unit. Floating point never touches money.

## Layout

`Account` is the chart of accounts, `LedgerTransaction` groups entries, `LedgerEntry` is a single
debit or credit.

## Accounts and what gets posted

Two accounts are in play. `PROVIDER_CLEARING` is what a provider owes Wayline for money collected
but not yet settled. `MERCHANT_PAYABLE` is what Wayline owes the merchant.

| Event | Debit | Credit |
|---|---|---|
| `PAYMENT_CAPTURE` | `PROVIDER_CLEARING` | `MERCHANT_PAYABLE` |
| `PAYMENT_REFUND` | `MERCHANT_PAYABLE` | `PROVIDER_CLEARING` |

A refund is the exact reverse of the capture for the refunded amount. The capture entries are
never touched, which is what makes it a compensating transaction rather than a correction.

Posting is driven by `PaymentLedgerPoster` in `wayline-payment` and happens **inside the same
transaction** as the payment or refund status change, so the ledger and the payment state can
never disagree.
