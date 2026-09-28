# wayline-settlement

Ingests the settlement files providers publish — what they say they actually paid out, as
opposed to what Wayline believes it charged.

A `Settlement` records the provider, the settlement date, and gross, fee and net amounts, all as
`Long` minor units. Fees usually appear here for the first time, since they are deducted by the
provider rather than known at authorisation time.

This module only imports and stores. Comparing a settlement against internal state is
`wayline-reconciliation`'s job, which keeps ingestion independent of matching policy.
