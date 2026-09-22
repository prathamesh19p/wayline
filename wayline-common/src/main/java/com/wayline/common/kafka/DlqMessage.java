package com.wayline.common.kafka;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Dead Letter Queue message for failed Kafka events.
 * Tracks events that failed after all retry attempts.
 */
@Entity
@Table(name = "dlq_messages", indexes = {
    @Index(name = "idx_dlq_messages_status", columnList = "status"),
    @Index(name = "idx_dlq_messages_created_at", columnList = "created_at"),
    @Index(name = "idx_dlq_messages_topic", columnList = "topic")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DlqMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String topic; // Kafka topic name

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalEvent; // Original event JSON

    @Column(nullable = false, columnDefinition = "TEXT")
    private String failureReason; // Why it failed

    @Column(columnDefinition = "TEXT")
    private String exception; // Stack trace or exception message

    @Column(nullable = false)
    @Default
    private Integer attemptCount = 1; // How many times it was attempted

    @Column(nullable = false)
    private Instant firstFailureTime;

    @Column(nullable = false)
    private Instant lastFailureTime;

    @Column(nullable = false, length = 20)
    @Default
    private String status = "FAILED"; // FAILED, REPLAY_INITIATED, RESOLVED

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    /**
     * Mark that replay was initiated.
     */
    public void markReplayInitiated() {
        this.status = "REPLAY_INITIATED";
    }

    /**
     * Mark message as resolved.
     */
    public void markResolved() {
        this.status = "RESOLVED";
    }
}
