package com.wayline.common.kafka;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tracks processed Kafka events for consumer idempotency.
 * Prevents duplicate business processing even if event is consumed multiple times.
 * 
 * Unique constraint ensures:
 * (event_id, consumer_name) combination is processed only once
 */
@Entity
@Table(name = "processed_events", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "consumer_name"}, name = "uk_processed_event")
}, indexes = {
    @Index(name = "idx_processed_events_consumer", columnList = "consumer_name"),
    @Index(name = "idx_processed_events_processed_at", columnList = "processed_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 256)
    private String eventId; // Must match event.eventId in domain event

    @Column(nullable = false, length = 100)
    private String consumerName; // e.g., "ledger-service", "settlement-service"

    @Column(nullable = false, updatable = false)
    @Default
    private Instant processedAt = Instant.now();
}
