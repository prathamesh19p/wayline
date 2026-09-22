package com.wayline.common.kafka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for ProcessedEvent entity.
 * Used for Kafka consumer idempotency tracking.
 */
@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    /**
     * Check if event has already been processed by consumer.
     *
     * @param eventId Event identifier
     * @param consumerName Consumer service name
     * @return true if already processed
     */
    boolean existsByEventIdAndConsumerName(String eventId, String consumerName);

    /**
     * Get processed event record.
     */
    Optional<ProcessedEvent> findByEventIdAndConsumerName(String eventId, String consumerName);
}
