# wayline-routing

Chooses which provider should handle a payment.

`ProviderSelectorService` filters the registered `PaymentProvider` beans down to those that
support the requested method and currency, drops the ones currently considered unhealthy, and
picks the best remaining candidate. If nothing qualifies the request is rejected rather than
sent to a provider that cannot serve it.

`ProviderHealthService` keeps the health signal. It is fed by real call outcomes recorded by the
orchestrator — `recordSuccess`, `recordFailure`, `recordTimeout` — rather than by polling the
provider, so the score reflects traffic the system actually depends on.

Timeouts are tracked separately from failures on purpose: a timeout means the outcome is
unknown, which is a different operational signal from a clean decline.

`ResilienceConfiguration` holds the Resilience4j circuit breaker and retry settings.
