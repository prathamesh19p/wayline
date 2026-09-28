# wayline-common

Cross-cutting infrastructure every other module depends on. It owns no business concepts of its
own; anything payment-specific belongs in a feature module.

## Contents

| Package | Responsibility |
|---|---|
| `outbox` | Transactional outbox: `OutboxEvent` rows written in the same transaction as a state change, then drained to Kafka by `OutboxEventPublisher`. |
| `kafka` | `ProcessedEvent` for consumer-side de-duplication and `DlqMessage` for poison messages. |
| `security` | `JwtTokenProvider`, `JwtAuthenticationFilter`, `WebhookSignatureVerifier`. |
| `exception` | `WaylineException`, the unchecked base for domain errors. |

## Why the outbox exists

Writing to the database and publishing to Kafka are two separate systems, so they cannot be
committed atomically. Publishing directly from a service risks either an event for a state that
was rolled back, or a committed state whose event was lost. Instead the event is inserted as a
row in `outbox_events` inside the business transaction, and a poller ships it afterwards.

Delivery is therefore at-least-once. Consumers must be idempotent; `ProcessedEvent` records
event ids already handled.

## Security notes

`JwtTokenProvider` refuses to start if `wayline.security.jwt.secret` is shorter than 32
characters, so the application cannot boot with a guessable signing key.

`WebhookSignatureVerifier` compares HMAC-SHA256 digests in constant time. It is the only
webhook authentication in the system; provider adapters deliberately do not implement their own.
