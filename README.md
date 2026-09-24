# Wayline Payment Orchestration & Reconciliation Platform

Wayline is a payment orchestration platform built with Java 21 and Spring Boot 3.
It handles the complete payment flow from provider selection and payment execution to webhooks, settlement, and reconciliation. The system is designed around real payment failure scenarios such as duplicate requests, provider timeouts, retries, ambiguous payment outcomes, duplicate or out-of-order webhooks, and reconciliation mismatches.
The platform uses a modular monolith architecture with dedicated modules for payments, provider routing, provider integrations, ledger, settlement, and reconciliation. PostgreSQL acts as the source of truth, while Kafka, Redis, Resilience4j, and the transactional outbox pattern are used for asynchronous processing, reliability, and coordination.

## Project Vision

The Wayline platform solves real backend problems that exist around payment processing:

- **Provider Selection**: Intelligent routing to multiple payment providers with health-aware fallbacks
- **Unreliable External APIs**: Resilience4j circuit breakers, retries, and timeout handling
- **Duplicate Requests**: Idempotent payment creation using unique keys
- **Asynchronous Callbacks**: Webhook processing with state validation and duplicate detection
- **Transaction Consistency**: Strong consistency guarantees for financial operations
- **Event Delivery**: Transactional outbox pattern with reliable Kafka publishing
- **Immutable Financial Records**: Double-entry ledger with immutable entries and compensating transactions
- **Settlement & Reconciliation**: Provider settlement import and multi-source reconciliation
- **Operational Investigation**: Complete payment timeline with full audit trail

## Technology Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| **Language & Framework** | Java 21, Spring Boot 3 | Modern JVM platform with latest features |
| **Database** | PostgreSQL | Source of truth for all transactional and financial state |
| **Message Queue** | Apache Kafka | Asynchronous event processing and reliable delivery |
| **Caching & Coordination** | Redis | Short-lived idempotency, provider health, distributed locks |
| **Resilience** | Resilience4j | Circuit breakers, retries, timeouts, bulkheads |
| **Authentication** | Spring Security + JWT | Stateless API authentication |
| **Database Migration** | Flyway | Versioned schema management |
| **Observability** | Micrometer, Prometheus, Grafana | Metrics collection and visualization |
| **Distributed Tracing** | OpenTelemetry, Jaeger | End-to-end request tracing |
| **Testing** | JUnit 5, Mockito, Testcontainers | Comprehensive test coverage |
| **Load Testing** | k6 | Performance and throughput testing |

## Start the Application

With Java 21, Maven 3.8+, and Docker Desktop, Colima, or another Docker-compatible runtime installed, run:

```bash
./scripts/start-wayline.sh
```

This starts the infrastructure, builds the Maven reactor, and launches the API at `http://localhost:8080`. Set `WAYLINE_AUTH_USERNAME`, `WAYLINE_AUTH_PASSWORD`, `JWT_SECRET`, and `WAYLINE_WEBHOOK_SECRET` before running it for non-default credentials. See [QUICKSTART.md](QUICKSTART.md) for manual startup and verification steps.

## Architecture: Modular Monolith

The project uses a modular monolith with clear module boundaries that could later be extracted into microservices. Each module has a distinct responsibility:

### Core Modules

1. **wayline-common**: Shared utilities, domain events, exceptions, security, and observability
2. **wayline-payment**: Payment lifecycle, idempotency, state machine, and status management
3. **wayline-routing**: Provider selection algorithm and health-aware routing
4. **wayline-provider**: Provider abstractions, adapters, and provider simulators
5. **wayline-ledger**: Immutable double-entry ledger system for financial correctness
6. **wayline-settlement**: Provider settlement import and processing
7. **wayline-reconciliation**: Reconciliation engine and mismatch exception handling
8. **wayline-notification**: Async notification and callback handling
9. **wayline-app**: Main application bootstrap and infrastructure configuration

### Infrastructure Components

- **PostgreSQL**: Authoritative source of truth for payments, ledger, settlements
- **Kafka**: Event streaming for asynchronous processing
- **Redis**: Caching, idempotency optimization, provider health tracking
- **Prometheus/Grafana**: Metrics visualization
- **Jaeger**: Distributed tracing and request flow visualization

## Payment Lifecycle

```
CREATED
  ↓ (Validate → Select Provider → Execute)
PROCESSING
  ├─→ SUCCESS (settled, ledger updated)
  ├─→ FAILED (retry or cancel)
  └─→ UNKNOWN (awaiting webhook or status inquiry)
        ↓ (Provider status lookup / webhook / reconciliation)
        ├─→ SUCCESS
        └─→ FAILED
```

## Key Features

### 1. Idempotent Payment Creation
- Unique idempotency key prevents duplicate payments
- Stored in PostgreSQL (not Redis only)
- Returns existing payment on retry with same key
- Detects key reuse with different request body

### 2. Smart Provider Routing
- Filters by payment method, currency, amount
- Removes disabled or health-failing providers
- Applies configured priority rules
- Uses weighted scoring based on success rate
- Health-aware with circuit breaker integration

### 3. Resilient Provider Execution
- Timeouts prevent hanging requests
- Retries with exponential backoff
- Circuit breakers prevent cascade failures
- Distinguishes between timeout and actual failure
- Marks ambiguous outcomes as UNKNOWN (not automatically FAILED)

### 4. Webhook Processing
- Signature verification for provider authenticity
- Idempotent using provider event ID
- State machine validation for valid transitions
- Async processing to return quickly to provider
- Duplicate webhook detection and handling
- Out-of-order webhook handling

### 5. Double-Entry Ledger
- Every financial transaction must balance
- Debit and credit entries always together
- Immutable entries (corrections via compensating transactions)
- Separate ledger transactions from payment status
- Payment success ≠ ledger correctness

### 6. Transactional Outbox Pattern
- Ensures no Kafka events are lost if DB commits
- Outbox table stores events along with state changes
- Background publisher delivers events to Kafka
- Automatic retry with exponential backoff
- Failed events move to DLQ for investigation

### 7. Kafka Consumer Idempotency
- Processed events table tracks consumed events
- Prevents duplicate business processing
- Unique constraint on (event_id, consumer_name)
- Enables safe message replays from DLQ

### 8. Settlement & Reconciliation
- Import provider settlement data (CSV/JSON initially)
- Compare internal payment state vs provider data
- Detect mismatches and create exceptions
- Reconciliation status: MATCHED, MISMATCH, INVESTIGATING, RESOLVED
- Full audit trail for investigation

### 9. Operational Investigation
- `/api/v1/payments/{id}/timeline` shows complete payment history
- Payment state transitions with timestamps
- All provider attempts and responses
- Webhook events and processing
- Ledger transactions and entries
- Settlement and reconciliation results
- Full request-response with trace IDs

## API Design

### Authentication
```bash
POST /api/v1/auth/login
{
  "username": "merchant-demo",
  "password": "change-this-password"
}
```
Configure `WAYLINE_AUTH_USERNAME` and `WAYLINE_AUTH_PASSWORD` before starting the application. The response contains a JWT token for subsequent requests.

### Payments
```bash
# Create payment (idempotent with Idempotency-Key header)
POST /api/v1/payments
Headers: Idempotency-Key: unique-key
{
  "amount": 100000,
  "currency": "INR",
  "paymentMethod": "UPI",
  "description": "Order 123"
}

# Get payment
GET /api/v1/payments/{id}

# Get payment timeline
GET /api/v1/payments/{id}/timeline
```

### Webhooks
```bash
# Receive webhook from provider
POST /api/v1/webhooks/{provider}
{
  "eventId": "provider-event-123",
  "eventType": "payment.success",
  "paymentId": "provider-payment-456",
  "timestamp": "2024-01-15T10:30:00Z"
}
```
Include the `X-Provider-Signature` header generated with `WAYLINE_WEBHOOK_SECRET`.

### Providers
```bash
# Get active providers
GET /api/v1/providers

# Check provider health
GET /api/v1/providers/{provider}/health
```

### Settlement
```bash
# Import settlement data
POST /api/v1/settlements/import
{
  "provider": "provider-a",
  "settlementDate": "2024-01-15",
  "records": [...]
}

# Get settlement
GET /api/v1/settlements/{id}
```

### Reconciliation
```bash
# Run reconciliation for settlement
POST /api/v1/reconciliation/settlements/{settlementId}/run

# Get reconciliation records
GET /api/v1/reconciliation/settlements/{settlementId}

# Get mismatches
GET /api/v1/reconciliation/mismatches

# Resolve mismatch
POST /api/v1/reconciliation/{recordId}/resolve
```

### DLQ Management
```bash
# List DLQ messages
GET /api/v1/dlq

# Replay message from DLQ
POST /api/v1/dlq/{eventId}/replay
```

## Database Schema Highlights

### Idempotency
- `idempotency_records`: Tracks merchant requests with TTL for cleanup
- Unique constraint: (merchant_id, idempotency_key)

### Payment State
- `payments`: Core payment record with optimistic locking (version field)
- `payment_attempts`: Multiple attempts per payment with provider responses
- `payment_state_history`: Complete state transition audit trail

### Webhooks
- `payment_webhooks`: Webhook events with provider event ID
- Unique constraint: (provider, provider_event_id) prevents duplicates

### Financial Correctness
- `ledger_transactions`: Logical financial transaction grouping
- `ledger_entries`: Individual debit/credit entries (immutable)
- `accounts`: Merchant, platform, provider, clearing accounts

### Asynchronous Processing
- `outbox_events`: Transactional outbox for reliable Kafka publishing
- `processed_events`: Kafka consumer idempotency tracking
- `dlq_messages`: Dead letter queue for failed events

### Reconciliation
- `settlements`: Provider settlement import records
- `reconciliation_records`: Matching and mismatch detection

## Configuration

### application.yml
Core configuration in `wayline-app/src/main/resources/application.yml`:

```yaml
wayline:
  payment:
    idempotency-cache-ttl-minutes: 60
    webhook-timeout-seconds: 30
    webhook-max-retries: 3
  provider:
    timeout-seconds: 10
    retry-attempts: 2
    circuit-breaker-threshold: 50
  reconciliation:
    batch-size: 1000
    schedule-cron: "0 0 2 * * *"
```

## Running the Project

### Prerequisites
- Java 21
- Maven 3.8+
- Docker & Docker Compose

### Setup Infrastructure

```bash
# Start all infrastructure services
docker-compose up -d

# Verify services are running
docker-compose ps

# Access services:
# - PostgreSQL: localhost:5432 (wayline/wayline_password)
# - Redis: localhost:6379
# - Kafka: localhost:9092
# - Prometheus: http://localhost:9090
# - Grafana: http://localhost:3000 (admin/admin)
# - Jaeger: http://localhost:16686
```

### Build Project

```bash
# Build all modules
mvn clean install

# Build with tests
mvn clean install -DskipTests=false

# Run integration tests with Testcontainers
mvn verify
```

### Start Application

```bash
cd wayline-app

# Run with Maven
mvn spring-boot:run

# Or build and run JAR
mvn clean package
java -jar target/wayline-app-1.0.0-SNAPSHOT.jar
```

### Verify Health

```bash
# Application is running on port 8080
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Prometheus metrics
curl http://localhost:8080/actuator/prometheus
```

## Testing Strategy

### Unit Tests
Test payment state machine, routing algorithm, idempotency, ledger balancing, etc.

```bash
mvn test
```

### Integration Tests
Use Testcontainers for PostgreSQL, Kafka, Redis:

```bash
mvn verify
```

### Failure Tests
Explicit scenarios for:
- Provider timeout → UNKNOWN payment
- Provider success + lost response
- Kafka unavailable
- Database rollback
- Duplicate webhook
- Out-of-order webhook
- Circuit breaker opening

### Load Testing
Use k6 for performance validation:

```bash
k6 run tests/load/payment-creation.js
```

## Observability

### Metrics
Prometheus collects:
- `payment.success.count`, `payment.failure.count`, `payment.unknown.count`
- `payment.latency` (p50, p95, p99)
- `provider.success_rate`, `provider.timeout.count`
- `provider.circuit_breaker.state`
- `kafka.consumer.lag`
- `outbox.pending.count`
- `reconciliation.mismatch.count`
- `dlq.message.count`

### Tracing
Jaeger traces:
- API request → payment service logic
- Provider call execution
- Database transactions
- Kafka producer → consumer
- Ledger transaction
- Reconciliation processing

All traces include:
- `payment_id`: For payment correlation
- `trace_id`: For distributed tracing
- `attempt_id`: For attempt tracking
- `provider`: Provider name
- `event_id`: For event correlation

### Logs
Structured logging with:
- Timestamp
- Log level
- Logger name
- Message
- Payment/trace context

Example:
```
2024-01-15 10:30:45 [http-nio-8080-exec-1] INFO  PaymentService - payment_id=123 status=PROCESSING provider=ProviderA
```

## Important Design Principles

1. **PostgreSQL is the source of truth** - Use it for all transactional and financial state
2. **Kafka is for asynchronous communication** - Not the primary database
3. **Redis is an optimization layer** - Not authoritative for payment state
4. **External calls happen outside transactions** - Never hold DB transaction while calling provider
5. **Payment creation is idempotent** - Unique key prevents duplicates
6. **Webhook processing is idempotent** - Provider event ID prevents duplicates
7. **Kafka consumers are idempotent** - Processed events tracking
8. **Ledger entries are immutable** - Corrections use compensating transactions
9. **Unknown provider outcomes are not FAILED** - Await webhook/status lookup
10. **Database constraints are the final protection** - Against concurrent duplicates
11. **Every async operation has retries and DLQ** - For reliability
12. **Every payment operation is traceable** - Via payment_id and trace_id

## Failure Scenarios Handled

| Scenario | Solution |
|----------|----------|
| Duplicate payment request | Idempotency key lookup |
| Provider timeout | Mark as UNKNOWN, await webhook/status |
| Provider success + lost response | Reconciliation detects and corrects |
| DB commit succeeds but Kafka fails | Outbox pattern with background publisher |
| Kafka publishes but consumer fails | Event remains in Kafka, retried safely |
| Duplicate webhook | Provider event ID deduplication |
| Out-of-order webhook | State machine rejects invalid transitions |
| Consumer crashes midway | Transaction rolls back, message replayed |
| Provider circuit breaker opens | Route to alternative provider |
| Reconciliation mismatch | Exception created with full audit trail |

## Project Structure

```
wayline/
├── pom.xml                          # Root POM with dependency management
├── docker-compose.yml               # Infrastructure setup
├── docker/
│   └── prometheus.yml               # Prometheus configuration
├── wayline-common/                  # Shared utilities
├── wayline-payment/                 # Payment lifecycle
├── wayline-routing/                 # Provider routing
├── wayline-provider/                # Provider abstractions
├── wayline-ledger/                  # Double-entry ledger
├── wayline-settlement/              # Settlement processing
├── wayline-reconciliation/          # Reconciliation engine
├── wayline-notification/            # Notifications
├── wayline-app/                     # Main application
│   ├── src/main/
│   │   ├── java/com/wayline/
│   │   │   └── WaylineApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/
│   │           └── V1_0_0__Initial_schema.sql
│   └── pom.xml
├── tests/
│   ├── unit/                        # Unit tests
│   ├── integration/                 # Integration tests
│   ├── failure/                     # Failure scenario tests
│   └── load/                        # k6 load tests
└── README.md                        # This file
```

## Contributing

This is a portfolio project demonstrating production backend engineering practices. Code should emphasize:

- **Correctness over cleverness**: Explicit, readable code
- **Failure handling as a first-class concern**: Every async operation has retry + DLQ
- **Strong consistency where financial data matters**: Weak consistency elsewhere
- **Operational observability**: Every important operation is traceable
- **Clear module boundaries**: Modules could be extracted to services later
- **Complete audit trails**: Full history for investigation

