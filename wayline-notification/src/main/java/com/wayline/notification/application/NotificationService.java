package com.wayline.notification.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayline.notification.domain.Notification;
import com.wayline.notification.domain.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    @Value("${wayline.notification.webhook-url:}")
    private String webhookUrl;

    public void handlePaymentEvent(String eventType, String payload) {
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = text(event, "eventId");
            if (eventId == null) {
                log.warn("Ignoring payment event without eventId: type={}", eventType);
                return;
            }

            Notification notification = new Notification(
                eventId,
                NotificationType.WEBHOOK,
                webhookUrl,
                eventType,
                payload,
                java.time.Instant.now()
            );
            deliver(notification);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payment event payload", exception);
        }
    }

    public void deliver(Notification notification) {
        if (notification.type() != NotificationType.WEBHOOK) {
            log.info("Notification queued: eventId={} type={} recipient={}",
                notification.eventId(), notification.type(), notification.recipient());
            return;
        }
        if (notification.recipient() == null || notification.recipient().isBlank()) {
            log.info("Webhook delivery disabled: eventId={}", notification.eventId());
            return;
        }

        Exception lastFailure = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                restClientBuilder.build()
                    .post()
                    .uri(notification.recipient())
                    .header("X-Wayline-Event", notification.subject())
                    .body(notification.payload())
                    .retrieve()
                    .toBodilessEntity();
                log.info("Webhook delivered: eventId={} attempt={}", notification.eventId(), attempt);
                return;
            } catch (Exception exception) {
                lastFailure = exception;
                log.warn("Webhook delivery attempt failed: eventId={} attempt={}",
                    notification.eventId(), attempt, exception);
                if (attempt < 3) {
                    try {
                        Thread.sleep(100L * (1L << (attempt - 1)));
                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Webhook retry interrupted", interruptedException);
                    }
                }
            }
        }
        throw new IllegalStateException("Webhook delivery failed after retries", lastFailure);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
