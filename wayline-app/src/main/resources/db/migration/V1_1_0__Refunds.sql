-- Refunds return money for a payment that already succeeded.
--
-- A refund is never an edit of the original payment: the payment row and its ledger entries stay
-- exactly as they were, and the refund posts its own compensating ledger transaction. That keeps
-- the record of what was believed, and when, intact.
--
-- Partial refunds are supported, so a payment may have many refunds. The invariant that their sum
-- cannot exceed the payment amount is enforced in RefundService under a row lock on the payment,
-- because it spans rows and cannot be expressed as a column constraint.

CREATE TABLE refunds (
    id BIGSERIAL PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    merchant_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(256) NOT NULL,
    amount BIGINT NOT NULL,
    currency VARCHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    reason VARCHAR(500),
    provider VARCHAR(100),
    provider_refund_id VARCHAR(256),
    failure_code VARCHAR(50),
    failure_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_refunds_payment FOREIGN KEY (payment_id) REFERENCES payments(id),
    CONSTRAINT chk_refunds_amount_positive CHECK (amount > 0)
);

-- Makes a replayed Idempotency-Key a database-level impossibility rather than a race between
-- two concurrent requests that both read "no existing refund".
CREATE UNIQUE INDEX uq_refunds_merchant_idempotency_key
    ON refunds(merchant_id, idempotency_key);

CREATE INDEX idx_refunds_payment_id ON refunds(payment_id);
CREATE INDEX idx_refunds_status ON refunds(status);
CREATE INDEX idx_refunds_created_at ON refunds(created_at);
CREATE INDEX idx_refunds_provider_refund_id ON refunds(provider, provider_refund_id);
