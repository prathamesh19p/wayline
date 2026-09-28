# wayline-reconciliation

Compares provider settlement records against Wayline's own view and records every disagreement.

For each settlement row `ReconciliationService` locates the payment and classifies the outcome:
matched, missing internally, missing at the provider, or an amount mismatch. Discrepancies are
persisted as `ReconciliationRecord` rows rather than logged, because a mismatch is a finding
that someone must work through, not a transient error.

Matching currently keys on the provider-supplied payment reference. A production matcher would
fall back to fuzzy matching on merchant, amount and date when that reference is absent — the
code says so where it matters.

Reconciliation is the backstop that resolves payments stuck in `UNKNOWN`: if the provider
settled it, the money moved.
