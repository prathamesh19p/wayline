# wayline-notification

Handles inbound provider callbacks and outbound merchant notifications.

`WebhookController` is the inbound edge. It is authenticated by HMAC-SHA256 over the raw body
rather than a bearer token, because the caller is a provider rather than a merchant. Processing
is idempotent on the provider's `eventId`, so a redelivery is accepted and ignored.

Status codes are meaningful and distinct: `401` for a signature mismatch, `400` for malformed
JSON or a missing `paymentId`, `422` for a well-formed event that does not apply to the
payment's current state. Each is logged with the provider, so a failing integration can be
diagnosed from the logs alone.

`PaymentEventListener` consumes payment events from Kafka and `NotificationService` fans them
out to merchants.
