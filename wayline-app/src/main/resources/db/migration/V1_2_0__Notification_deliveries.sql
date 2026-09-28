CREATE TABLE notification_deliveries (
    id BIGSERIAL PRIMARY KEY,
    event_id VARCHAR(256) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    recipient VARCHAR(2048) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failure TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    delivered_at TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chk_notification_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'IN_PROGRESS', 'RETRY', 'DELIVERED', 'SKIPPED', 'DEAD'))
);

CREATE INDEX idx_notification_delivery_due ON notification_deliveries(status, next_attempt_at);
CREATE INDEX idx_notification_delivery_created ON notification_deliveries(created_at);
