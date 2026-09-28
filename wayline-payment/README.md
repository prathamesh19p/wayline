# wayline-payment

The core module: payment lifecycle, idempotency, and the state machine.

## The state machine

```
CREATED ──► PROCESSING ──┬──► SUCCESS    (terminal)
   │                     ├──► FAILED     (terminal)
   │                     └──► UNKNOWN ───┬──► SUCCESS
   └──► CANCELLED (terminal)             └──► FAILED
```

`Payment.canTransitionTo` is the single authority. Terminal states accept no further
transitions, so a webhook arriving late cannot flip a settled payment.

**`UNKNOWN` is not a failure.** It means the provider did not answer in time and the real
outcome is not yet established. Treating that as `FAILED` is how a system double-charges a
customer: the money may well have moved. The payment stays `UNKNOWN` until a webhook or
reconciliation resolves it.

## Idempotency

`POST /api/v1/payments` requires an `Idempotency-Key`. `PaymentService.createPayment`:

1. Looks up `(merchantId, idempotencyKey)`, which carries a unique constraint.
2. On a hit, compares a SHA-256 hash of `amount|currency|paymentMethod`. A mismatch is rejected
   with `400` so a retry cannot quietly charge a different amount.
3. On a match, returns the original payment without creating anything.

## Orchestration

`PaymentOrchestrationService.process` selects a provider, records an attempt, and calls the
provider with a timeout. Two details matter:

- Provider calls run on a **dedicated bounded executor**, not `ForkJoinPool.commonPool`, which
  is sized for CPU-bound work and would be starved by blocking HTTP.
- The future's `ExecutionException` is **unwrapped** before dispatch, otherwise the
  `catch (ProviderException)` branch is unreachable and the `TIMEOUT -> UNKNOWN` mapping never
  fires.

The resulting outbox event is passed *into* `transitionPaymentStatus` so the state change and
the event are written in one transaction.

## Refunds

A refund returns money for a payment in `SUCCESS`. It **never edits the original payment**: the
payment row and its ledger entries are left exactly as they were, and the refund posts its own
compensating ledger transaction. The record of what was charged, and when, survives.

`POST /api/v1/payments/{id}/refunds` — omit `amount` to refund the full remaining balance.

### The invariant that matters

A payment may be refunded several times, but the sum can never exceed what was charged. That
check spans rows, so it cannot be a column constraint; `RefundLifecycle.reserve` performs it
while holding a **`SELECT ... FOR UPDATE` row lock on the payment**. Without the lock two
concurrent requests both read the same refunded total, both pass, and the merchant refunds more
than it collected.

`UNKNOWN` refunds count against that balance even though they are not terminal. If a refund timed
out the money may already have gone, and paying a customer twice is the more expensive mistake.

### Transaction boundaries

The flow is three short transactions with the provider call *between* them, not one long one:

```
reserve (tx, row lock)  →  provider call (no tx)  →  settle (tx)
```

Holding the payment's lock across a network call would serialise every refund in the system
behind the slowest provider.

Those transactional steps live on `RefundLifecycle`, a separate bean from `RefundService`. Spring
applies `@Transactional` through a proxy, so a self-invoked method on the same bean would run
with **no transaction at all** — silently, with no error.

## The ledger

A payment posts to the ledger in the same transaction as its `SUCCESS` transition, so a payment
can never be marked captured without the matching entries. `PaymentLedgerPoster` owns the account
convention; see [wayline-ledger](../wayline-ledger/README.md).

## Layout

`api` HTTP layer · `application` services and use cases · `domain` entities and state rules ·
`infrastructure` Spring Data repositories.
