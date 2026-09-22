-- V1.0.0__Initial_schema.sql
-- Core tables for Wayline Payment Platform

-- Accounts
CREATE TABLE accounts (
    id BIGSERIAL PRIMARY KEY,
    account_type VARCHAR(50) NOT NULL,
    owner_id VARCHAR(100) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_account_owner_currency UNIQUE (account_type, owner_id, currency)
);

CREATE INDEX idx_accounts_owner_id ON accounts(owner_id);
CREATE INDEX idx_accounts_status ON accounts(status);

-- Payments
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    selected_provider VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_payments_merchant_id ON payments(merchant_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_created_at ON payments(created_at);
CREATE INDEX idx_payments_idempotency_key ON payments(idempotency_key);

-- Payment Attempts
CREATE TABLE payment_attempts (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    provider VARCHAR(100) NOT NULL,
    provider_payment_id VARCHAR(256),
    attempt_number INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    request_time TIMESTAMP,
    response_time TIMESTAMP,
    failure_code VARCHAR(50),
    failure_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_attempts_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_payment_attempts_payment_id ON payment_attempts(payment_id);
CREATE INDEX idx_payment_attempts_provider_payment_id ON payment_attempts(provider_payment_id);
CREATE INDEX idx_payment_attempts_status ON payment_attempts(status);

-- Payment State History
CREATE TABLE payment_state_history (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    from_state VARCHAR(20),
    to_state VARCHAR(20) NOT NULL,
    reason TEXT,
    source VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_payment_state_history FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_payment_state_history_payment_id ON payment_state_history(payment_id);
CREATE INDEX idx_payment_state_history_created_at ON payment_state_history(created_at);

-- Payment Webhooks
CREATE TABLE payment_webhooks (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    provider_event_id VARCHAR(256) NOT NULL,
    payment_id BIGINT,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    received_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'RECEIVED',
    CONSTRAINT fk_payment_webhooks_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT uk_webhook_event_id UNIQUE (provider, provider_event_id)
);

CREATE INDEX idx_payment_webhooks_payment_id ON payment_webhooks(payment_id);
CREATE INDEX idx_payment_webhooks_status ON payment_webhooks(status);
CREATE INDEX idx_payment_webhooks_received_at ON payment_webhooks(received_at);

-- Idempotency Records
CREATE TABLE idempotency_records (
    id BIGSERIAL PRIMARY KEY,
    merchant_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    request_hash VARCHAR(256) NOT NULL,
    payment_id BIGINT NOT NULL,
    response_status VARCHAR(20) NOT NULL,
    response_body TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_idempotency_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT uk_idempotency UNIQUE (merchant_id, idempotency_key)
);

CREATE INDEX idx_idempotency_records_expires_at ON idempotency_records(expires_at);
CREATE INDEX idx_idempotency_records_merchant_id ON idempotency_records(merchant_id);

-- Outbox Events (Transactional Outbox Pattern)
CREATE TABLE outbox_events (
    id BIGSERIAL PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(256) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP,
    CONSTRAINT uk_outbox_event_id UNIQUE (aggregate_type, aggregate_id, event_type, created_at)
);

CREATE INDEX idx_outbox_events_status ON outbox_events(status);
CREATE INDEX idx_outbox_events_created_at ON outbox_events(created_at);
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- Ledger Transactions
CREATE TABLE ledger_transactions (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT,
    transaction_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(256),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_transaction_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_ledger_transactions_payment_id ON ledger_transactions(payment_id);
CREATE INDEX idx_ledger_transactions_created_at ON ledger_transactions(created_at);
CREATE INDEX idx_ledger_transactions_reference_id ON ledger_transactions(reference_id);

-- Ledger Entries (Double-entry bookkeeping)
CREATE TABLE ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    ledger_transaction_id BIGINT NOT NULL,
    account_id BIGINT NOT NULL,
    entry_type VARCHAR(20) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (ledger_transaction_id) REFERENCES ledger_transactions(id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts(id)
);

CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries(ledger_transaction_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries(account_id);
CREATE INDEX idx_ledger_entries_created_at ON ledger_entries(created_at);

-- Settlements
CREATE TABLE settlements (
    id BIGSERIAL PRIMARY KEY,
    provider VARCHAR(100) NOT NULL,
    settlement_reference VARCHAR(256) NOT NULL,
    settlement_date DATE NOT NULL,
    currency VARCHAR(3) NOT NULL,
    gross_amount BIGINT NOT NULL,
    fees BIGINT NOT NULL,
    net_amount BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_settlement_reference UNIQUE (provider, settlement_reference)
);

CREATE INDEX idx_settlements_provider ON settlements(provider);
CREATE INDEX idx_settlements_status ON settlements(status);
CREATE INDEX idx_settlements_settlement_date ON settlements(settlement_date);

-- Reconciliation Records
CREATE TABLE reconciliation_records (
    id BIGSERIAL PRIMARY KEY,
    settlement_id BIGINT NOT NULL,
    payment_id BIGINT,
    expected_amount BIGINT NOT NULL,
    actual_amount BIGINT,
    difference BIGINT,
    status VARCHAR(20) NOT NULL DEFAULT 'MATCHED',
    reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_reconciliation_settlement FOREIGN KEY (settlement_id) REFERENCES settlements(id),
    CONSTRAINT fk_reconciliation_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
);

CREATE INDEX idx_reconciliation_records_settlement_id ON reconciliation_records(settlement_id);
CREATE INDEX idx_reconciliation_records_payment_id ON reconciliation_records(payment_id);
CREATE INDEX idx_reconciliation_records_status ON reconciliation_records(status);
CREATE INDEX idx_reconciliation_records_created_at ON reconciliation_records(created_at);

-- Processed Events (Consumer Idempotency)
CREATE TABLE processed_events (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(256) NOT NULL,
    consumer_name VARCHAR(100) NOT NULL,
    processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_processed_event UNIQUE (event_id, consumer_name)
);

CREATE INDEX idx_processed_events_consumer ON processed_events(consumer_name);
CREATE INDEX idx_processed_events_processed_at ON processed_events(processed_at);

-- DLQ (Dead Letter Queue) Messages
CREATE TABLE dlq_messages (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(100) NOT NULL,
    original_event TEXT NOT NULL,
    failure_reason TEXT NOT NULL,
    exception TEXT,
    attempt_count INT NOT NULL DEFAULT 1,
    first_failure_time TIMESTAMP NOT NULL,
    last_failure_time TIMESTAMP NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'FAILED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dlq_message_id UNIQUE (topic, original_event)
);

CREATE INDEX idx_dlq_messages_status ON dlq_messages(status);
CREATE INDEX idx_dlq_messages_created_at ON dlq_messages(created_at);
CREATE INDEX idx_dlq_messages_topic ON dlq_messages(topic);
