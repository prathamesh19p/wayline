package com.wayline.common.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Background service for publishing outbox events to Kafka.
 * Runs periodically to ensure reliable event delivery.
 * 
 * Failure handling:
 * - If Kafka is unavailable, event remains in PENDING state
 * - Retry picks it up next cycle
 * - Kafka topic routing based on aggregate type
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Publish pending outbox events to Kafka.
     * Runs every 5 seconds.
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findPendingEvents();

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Publishing {} pending events", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                event.markPublished();
                outboxEventRepository.save(event);
                log.debug("Published outbox event: id={} type={}", event.getId(), event.getEventType());
            } catch (Exception e) {
                log.warn("Failed to publish outbox event: id={}", event.getId(), e);
                event.markFailed();
                outboxEventRepository.save(event);
            }
        }
    }

    /**
     * Retry failed events with exponential backoff.
     * Runs every 30 seconds.
     */
    @Scheduled(fixedDelay = 30000)
    @Transactional
    public void retryFailedEvents() {
        List<OutboxEvent> failedEvents = outboxEventRepository.findEventsNeedingRetry();

        for (OutboxEvent event : failedEvents) {
            if (event.shouldRetry()) {
                try {
                    publishEvent(event);
                    event.markPublished();
                    outboxEventRepository.save(event);
                    log.info("Retried outbox event: id={} retry_count={}", event.getId(), event.getRetryCount());
                } catch (Exception e) {
                    log.warn("Retry failed for outbox event: id={} retry_count={}", event.getId(), event.getRetryCount(), e);
                    event.markFailed();
                    outboxEventRepository.save(event);
                }
            }
        }
    }

    /**
     * Publish event to appropriate Kafka topic based on aggregate type.
     */
    private void publishEvent(OutboxEvent event) throws Exception {
        String topic = getTopicForAggregateType(event.getAggregateType());
        String key = event.getAggregateId();

        kafkaTemplate.send(topic, key, event.getPayload()).get(10, TimeUnit.SECONDS);
        log.debug("Event sent to Kafka: topic={} key={}", topic, key);
    }

    /**
     * Get Kafka topic name for aggregate type.
     */
    private String getTopicForAggregateType(String aggregateType) {
        return switch (aggregateType) {
            case "PAYMENT" -> "payment.events";
            case "LEDGER" -> "ledger.events";
            case "SETTLEMENT" -> "settlement.events";
            case "RECONCILIATION" -> "reconciliation.events";
            case "WEBHOOK" -> "payment.webhooks";
            default -> "events";
        };
    }
}
