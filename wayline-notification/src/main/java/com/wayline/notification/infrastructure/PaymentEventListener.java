package com.wayline.notification.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayline.common.kafka.ProcessedEvent;
import com.wayline.common.kafka.ProcessedEventRepository;
import com.wayline.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    private static final String CONSUMER_NAME = "notification-service";

    private final ObjectMapper objectMapper;
    private final ProcessedEventRepository processedEventRepository;
    private final NotificationService notificationService;

    @KafkaListener(topics = "payment.events", groupId = "wayline-notifications")
    @Transactional
    public void onPaymentEvent(String payload, Acknowledgment acknowledgment) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = event.path("eventId").asText(null);
            String eventType = event.path("eventType").asText("PaymentEvent");
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("Payment event is missing eventId");
            }
            if (processedEventRepository.existsByEventIdAndConsumerName(eventId, CONSUMER_NAME)) {
                acknowledgment.acknowledge();
                return;
            }

            notificationService.handlePaymentEvent(eventType, payload);
            processedEventRepository.save(ProcessedEvent.builder()
                .eventId(eventId)
                .consumerName(CONSUMER_NAME)
                .build());
            acknowledgment.acknowledge();
        } catch (Exception exception) {
            log.error("Payment notification processing failed", exception);
            throw new IllegalStateException("Payment notification processing failed", exception);
        }
    }
}
