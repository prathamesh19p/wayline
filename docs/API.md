# API Documentation

Complete API reference for Wayline Payment Orchestration & Reconciliation Platform.

## Base URL
```
http://localhost:8080/api/v1
```

## Authentication
All endpoints (except `/auth/login` and `/webhooks/**`) require JWT token:
```
Authorization: Bearer <JWT_TOKEN>
```

Obtain token via `/auth/login` endpoint.

---

## Authentication Endpoints

### POST /auth/login
Authenticate and receive JWT token.

**Request:**
```json
{
  "username": "merchant@example.com",
  "password": "password123"
}
```

**Response (200):**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9..."
}
```

**Errors:**
- 400: Invalid credentials format
- 401: Authentication failed

---

## Payment Endpoints

### POST /payments
Create a new payment.

**Request:**
```json
{
  "merchantId": "merchant_123",
  "idempotencyKey": "unique-key-per-merchant",
  "amount": 10000,
  "currency": "INR",
  "paymentMethod": "CARD",
  "description": "Product purchase"
}
```

**Response (200):**
```json
{
  "id": 1,
  "merchantId": "merchant_123",
  "idempotencyKey": "unique-key-per-merchant",
  "amount": 10000,
  "currency": "INR",
  "paymentMethod": "CARD",
  "description": "Product purchase",
  "status": "CREATED",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**Status Codes:**
- 200: Payment created successfully
- 400: Invalid request (missing/invalid fields)
- 401: Unauthorized
- 409: Conflict (duplicate idempotencyKey with different amount)

**Idempotency:**
Multiple requests with same `idempotencyKey` return same payment. Request must be identical (amount, currency, etc.).

---

### GET /payments/{id}
Retrieve payment details.

**Response (200):**
```json
{
  "id": 1,
  "merchantId": "merchant_123",
  "idempotencyKey": "unique-key-per-merchant",
  "amount": 10000,
  "currency": "INR",
  "paymentMethod": "CARD",
  "description": "Product purchase",
  "status": "PROCESSING",
  "attempts": 2,
  "lastAttemptAt": "2024-01-15T10:30:05Z",
  "createdAt": "2024-01-15T10:30:00Z",
  "updatedAt": "2024-01-15T10:30:05Z"
}
```

**Statuses:**
- `CREATED`: Payment created, awaiting processing
- `PROCESSING`: Currently being processed with provider
- `SUCCESS`: Payment successful
- `FAILED`: Payment failed
- `UNKNOWN`: Payment status unknown (may be success or failure)
- `CANCELLED`: Payment cancelled

---

### GET /payments/{id}/timeline
Get payment status change history.

**Response (200):**
```json
{
  "paymentId": 1,
  "timeline": [
    {
      "status": "CREATED",
      "timestamp": "2024-01-15T10:30:00Z",
      "reason": null
    },
    {
      "status": "PROCESSING",
      "timestamp": "2024-01-15T10:30:01Z",
      "reason": "Sent to ProviderA"
    },
    {
      "status": "UNKNOWN",
      "timestamp": "2024-01-15T10:30:10Z",
      "reason": "Provider timeout"
    },
    {
      "status": "SUCCESS",
      "timestamp": "2024-01-15T10:30:45Z",
      "reason": "Webhook received from provider"
    }
  ]
}
```

---

### GET /payments?merchantId={id}&status={status}&limit={limit}&offset={offset}
List payments with optional filtering.

**Query Parameters:**
- `merchantId` (optional): Filter by merchant
- `status` (optional): Filter by status (CREATED, PROCESSING, SUCCESS, FAILED, UNKNOWN, CANCELLED)
- `limit` (optional, default 20): Max results
- `offset` (optional, default 0): Pagination offset

**Response (200):**
```json
{
  "payments": [
    {
      "id": 1,
      "merchantId": "merchant_123",
      "amount": 10000,
      "status": "SUCCESS",
      "createdAt": "2024-01-15T10:30:00Z"
    }
  ],
  "total": 1,
  "limit": 20,
  "offset": 0
}
```

---

## Settlement Endpoints

### POST /settlements/import
Import settlement records from provider.

**Request:**
```json
{
  "providerId": "razorpay",
  "settlementReference": "payout_123456",
  "settlementDate": "2024-01-14T23:59:59Z",
  "records": [
    {
      "paymentId": 1,
      "amount": 9700,
      "currency": "INR"
    }
  ]
}
```

**Response (200):**
```json
{
  "settlementId": 42,
  "providerId": "razorpay",
  "settlementReference": "payout_123456",
  "settlementDate": "2024-01-14T23:59:59Z",
  "status": "PENDING",
  "totalAmount": 9700,
  "recordCount": 1,
  "createdAt": "2024-01-15T11:00:00Z"
}
```

**Errors:**
- 400: Invalid settlement data
- 401: Unauthorized
- 409: Duplicate settlement reference

---

### GET /settlements/{id}
Get settlement details.

**Response (200):**
```json
{
  "settlementId": 42,
  "providerId": "razorpay",
  "settlementReference": "payout_123456",
  "settlementDate": "2024-01-14T23:59:59Z",
  "status": "COMPLETED",
  "totalAmount": 9700,
  "recordCount": 1,
  "createdAt": "2024-01-15T11:00:00Z",
  "completedAt": "2024-01-15T11:15:00Z"
}
```

---

## Reconciliation Endpoints

### POST /reconciliation/settlements/{settlementId}/run
Run reconciliation for a settlement.

**Request:**
```json
[
  {
    "paymentId": 1,
    "amount": 9700
  }
]
```

**Response (200):**
```json
{
  "settlementId": 42,
  "totalRecords": 100,
  "matchedRecords": 98,
  "mismatchedRecords": 2,
  "mismatchDetails": [
    {
      "recordId": 1,
      "paymentId": null,
      "reason": "Payment not found"
    },
    {
      "recordId": 2,
      "paymentId": 50,
      "expectedAmount": 5000,
      "actualAmount": 4950,
      "reason": "Amount mismatch"
    }
  ]
}
```

---

### GET /reconciliation/mismatches
Get all unresolved mismatches.

**Query Parameters:**
- `limit` (optional, default 50): Max results
- `offset` (optional, default 0): Pagination

**Response (200):**
```json
{
  "mismatches": [
    {
      "id": 1,
      "settlementId": 42,
      "paymentId": null,
      "expectedAmount": 1000,
      "actualAmount": null,
      "reason": "Payment not found",
      "status": "MISMATCH",
      "createdAt": "2024-01-15T11:15:00Z"
    }
  ],
  "total": 1,
  "limit": 50,
  "offset": 0
}
```

---

### POST /reconciliation/mismatches/{id}/resolve
Resolve a mismatch record.

**Request:**
```json
{
  "resolution": "Fee was correctly deducted by provider. Amount difference expected."
}
```

**Response (200):**
```json
{
  "id": 1,
  "status": "RESOLVED",
  "resolution": "Fee was correctly deducted by provider. Amount difference expected.",
  "resolvedAt": "2024-01-15T12:00:00Z"
}
```

---

## Webhook Endpoints

### POST /webhooks/{provider}
Receive webhook from payment provider.

**Headers (required):**
```
Content-Type: application/json
X-Provider-Signature: <HMAC-SHA256 signature>
X-Provider-Timestamp: 1705318200
```

**Request (example from Razorpay):**
```json
{
  "event": "payment.authorized",
  "payload": {
    "payment": {
      "entity": "payment",
      "id": "pay_123456",
      "amount": 10000,
      "currency": "INR",
      "status": "authorized"
    }
  }
}
```

**Response (200):**
```json
{
  "message": "Webhook received and processed"
}
```

**Webhook Guarantees:**
- Signature verified using shared secret
- At-most-once processing via idempotency (X-Provider-Event-ID)
- Automatic retry if processing fails
- Dead letter queue for persistently failing webhooks

---

## Health & Monitoring Endpoints

### GET /actuator/health
Application health check.

**Response (200):**
```json
{
  "status": "UP",
  "components": {
    "db": {"status": "UP"},
    "kafkaProducer": {"status": "UP"},
    "redis": {"status": "UP"}
  }
}
```

---

### GET /actuator/metrics
List all available metrics.

**Response (200):**
```json
{
  "names": [
    "payment.created.count",
    "payment.succeeded.count",
    "payment.failed.count",
    "provider.request.count",
    "outbox.pending.count",
    "kafka.message.sent.count",
    ...
  ]
}
```

---

### GET /actuator/metrics/{metricName}
Get specific metric value.

**Example:**
```
GET /actuator/metrics/payment.succeeded.count
```

**Response (200):**
```json
{
  "name": "payment.succeeded.count",
  "description": "Total successful payments",
  "baseUnit": null,
  "measurements": [
    {
      "statistic": "COUNT",
      "value": 42
    }
  ],
  "availableTags": []
}
```

---

### GET /actuator/prometheus
Export metrics in Prometheus format.

**Response (200):**
```
# HELP payment_created_count_total Total payment created count
# TYPE payment_created_count_total counter
payment_created_count_total{} 100.0

# HELP payment_succeeded_count_total Total payment succeeded count
# TYPE payment_succeeded_count_total counter
payment_succeeded_count_total{} 95.0

...
```

---

## Error Responses

All error responses follow standard format:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable error message",
  "timestamp": "2024-01-15T10:30:00Z",
  "path": "/api/v1/payments"
}
```

### Common Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| VALIDATION_ERROR | 400 | Invalid request parameters |
| AUTHENTICATION_ERROR | 401 | Missing or invalid JWT token |
| AUTHORIZATION_ERROR | 403 | Insufficient permissions |
| NOT_FOUND | 404 | Resource not found |
| DUPLICATE_REQUEST | 409 | Duplicate idempotency key with different data |
| PROVIDER_ERROR | 502 | Payment provider error |
| DATABASE_ERROR | 500 | Database connection or query error |
| INTERNAL_ERROR | 500 | Unexpected server error |

---

## Rate Limiting

**Current:** No rate limiting (implement in production)

**Recommended:**
- 1000 requests/minute per merchant
- 100 requests/minute per API token
- 10 concurrent requests per merchant

---

## Pagination

All list endpoints support pagination:

```
GET /payments?limit=20&offset=0
```

**Response includes:**
- `items[]`: Array of resources
- `total`: Total count (may be approximate for large datasets)
- `limit`: Items per page
- `offset`: Current offset
- `hasMore`: Whether more items exist

---

## Date/Time Format

All dates use ISO 8601 format with UTC timezone:
```
2024-01-15T10:30:45.123Z
```

---

## Idempotency

Payment creation is idempotent for retry safety:

```
POST /payments
Idempotency-Key: merchant-123-abc-def
```

**Behavior:**
- Same key returns same payment (regardless of current state)
- Different amount with same key → 409 Conflict
- Useful for network retry scenarios

---

## Related Documentation

- [Architecture Decision Records](./ADR.md)
- [Operational Runbook](./RUNBOOK.md)
- [Database Schema](./DATABASE.md)
- [Implementation Guide](./IMPLEMENTATION.md)
