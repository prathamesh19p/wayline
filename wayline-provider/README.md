# wayline-provider

The boundary between Wayline and external payment processors.

`PaymentProvider` is the port. Everything upstream is written against it, so adding a processor
means adding an adapter and nothing else. `ProviderAAdapter`, `ProviderBAdapter` and
`SimulatedProviderAdapter` are simulators with different latency and failure characteristics,
which is what lets the routing and resilience behaviour be exercised without live credentials.

Adapters signal problems with `ProviderException`, carrying a provider name and an error code.
An error code of `TIMEOUT` is mapped by the orchestrator to `UNKNOWN` rather than `FAILED`.

Adapters deliberately do **not** verify webhook signatures. That is centralised in
`WebhookSignatureVerifier` in `wayline-common`; an adapter-local check was previously a stub
that accepted any non-blank string.

## Calling a provider

`ProviderCallExecutor` runs every provider call with a hard timeout. Two details in it are easy to
get wrong: calls run on a dedicated bounded pool rather than `ForkJoinPool.commonPool`, which is
sized for CPU-bound work and would be starved by blocking HTTP; and `ExecutionException` is
unwrapped before it reaches the caller, without which a `catch (ProviderException)` upstream is
unreachable dead code.

`ProviderRegistry` looks adapters up by name. Routing chooses a provider for a *new* payment,
but a follow-up operation such as a refund has to reach the provider that handled the original
charge rather than a freshly selected one.
