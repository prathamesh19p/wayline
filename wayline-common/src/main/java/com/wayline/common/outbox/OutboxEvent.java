package com.wayline.common.outbox;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Outbox event for transactional outbox pattern.
 * Ensures no events are lost even if Kafka is unavailable.
 * 
 * Pattern:
 * 1. Application writes to database
 * 2. Application inserts outbox event in same transaction
 * 3. Transaction commits atomically
 * 4. Background publisher reads unpublished events
 * 5. Publisher sends to Kafka
 * 6. Publisher marks event as published
 * 
 * Benefits:
 * - If DB commits but Kafka fails, event remains in outbox for retry
 * - If Kafka publishes but publisher crashes, event retried from outbox
 * - Guaranteed at-least-once event delivery
 */
@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_events_status", columnList = "status"),
    @Index(name = "idx_outbox_events_created_at", columnList = "created_at"),
    @Index(name = "idx_outbox_events_aggregate", columnList = "aggregate_type,aggregate_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String aggregateType; // e.g., "PAYMENT", "LEDGER", "SETTLEMENT"

    @Column(nullable = false, length = 256)
    private String aggregateId; // e.g., "payment_123"

    @Column(nullable = false, length = 100)
    private String eventType; // e.g., "PaymentCreated", "PaymentSucceeded"

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload; // JSON serialized event

    @Column(nullable = false, length = 20)
    @Default
    private String status = "PENDING"; // PENDING, PUBLISHED, FAILED

    @Column(nullable = false)
    @Default
    private Integer retryCount = 0;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    private Instant publishedAt;

    /**
     * Check if event should be retried.
     * Retry with exponential backoff: 5s, 30s, 2m, 10m, stop
     */
    public boolean shouldRetry() {
        if (!"FAILED".equals(status)) {
            return false;
        }

        if (retryCount >= 4) {
            return false; // Give up after 4 retries
        }

        // Calculate backoff: 5, 30, 120, 600 seconds
        long[] backoffSeconds = {5, 30, 120, 600};
        long backoffSec = backoffSeconds[retryCount];
        Instant retryTime = createdAt.plusSeconds(backoffSec);

        return Instant.now().isAfter(retryTime);
    }

    /**
     * Mark event as failed for retry.
     */
    public void markFailed() {
        this.status = "FAILED";
        this.retryCount = (retryCount != null ? retryCount : 0) + 1;
    }

    /**
     * Mark event as successfully published.
     */
    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = Instant.now();
    }
}
