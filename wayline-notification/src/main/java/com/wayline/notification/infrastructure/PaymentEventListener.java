package com.wayline.notification.infrastructure;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wayline.notification.application.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    @KafkaListener(topics = "payment.events", groupId = "wayline-notifications")
    public void onPaymentEvent(String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = event.path("eventId").asText(null);
            String eventType = event.path("eventType").asText("PaymentEvent");
            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException("Payment event is missing eventId");
            }
            notificationService.handlePaymentEvent(eventType, payload);
        } catch (Exception exception) {
            log.error("Payment notification processing failed", exception);
            throw new IllegalStateException("Payment notification processing failed", exception);
        }
    }
}
