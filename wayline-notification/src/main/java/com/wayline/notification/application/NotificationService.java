package com.wayline.notification.application;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wayline.notification.domain.NotificationDelivery;
import com.wayline.notification.infrastructure.NotificationDeliveryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    private static final int MAX_ATTEMPTS = 8;
    private static final int BATCH_SIZE = 50;
    private static final long LEASE_SECONDS = 120;

    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final NotificationDeliveryRepository deliveryRepository;

    @Value("${wayline.notification.webhook-url:}")
    private String webhookUrl;

    @Transactional
    public void handlePaymentEvent(String eventType, String payload) {
        JsonNode event;
        try {
            event = objectMapper.readTree(payload);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid payment event payload", exception);
        }

        String eventId = text(event, "eventId");
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("Payment event is missing eventId");
        }
        if (deliveryRepository.findByEventId(eventId).isPresent()) {
            return;
        }

        boolean deliveryConfigured = webhookUrl != null && !webhookUrl.isBlank();
        deliveryRepository.save(NotificationDelivery.builder()
            .eventId(eventId)
            .status(deliveryConfigured ? "PENDING" : "SKIPPED")
            .recipient(deliveryConfigured ? webhookUrl : "disabled://not-configured")
            .eventType(eventType)
            .payload(payload)
            .nextAttemptAt(Instant.now())
            .lastFailure(deliveryConfigured ? null : "Webhook callback URL is not configured")
            .build());
    }

    @Transactional(readOnly = true)
    public List<NotificationDelivery> getDeadDeliveries() {
        return deliveryRepository.findByStatusOrderByCreatedAtAsc("DEAD");
    }

    @Transactional
    public NotificationDelivery replayDeadDelivery(Long id) {
        NotificationDelivery delivery = deliveryRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Notification delivery not found: " + id));
        if (!"DEAD".equals(delivery.getStatus())) {
            throw new IllegalStateException("Only DEAD notification deliveries can be replayed");
        }
        delivery.setStatus("PENDING");
        delivery.setAttemptCount(0);
        delivery.setNextAttemptAt(Instant.now());
        delivery.setLastFailure(null);
        return deliveryRepository.save(delivery);
    }

    @Scheduled(fixedDelayString = "${wayline.notification.poll-interval-ms:5000}")
    public void deliverDueNotifications() {
        Instant now = Instant.now();
        List<NotificationDelivery> due = deliveryRepository
            .findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
                List.of("PENDING", "RETRY", "IN_PROGRESS"), now, PageRequest.of(0, BATCH_SIZE));
        for (NotificationDelivery candidate : due) {
                if (deliveryRepository.claim(candidate.getId(), now, now.plusSeconds(LEASE_SECONDS)) == 1) {
                deliverClaimed(candidate.getId());
            }
        }
    }

    private void deliverClaimed(Long id) {
        NotificationDelivery delivery = deliveryRepository.findById(id).orElse(null);
        if (delivery == null || !"IN_PROGRESS".equals(delivery.getStatus())) {
            return;
        }
        if (delivery.getAttemptCount() > MAX_ATTEMPTS) {
            delivery.setStatus("DEAD");
            delivery.setLastFailure("Delivery lease expired after maximum attempts");
            deliveryRepository.save(delivery);
            return;
        }

        try {
            restClientBuilder.build()
                .post()
                .uri(delivery.getRecipient())
                .header("X-Wayline-Event", delivery.getEventType())
                .header("X-Wayline-Event-Id", delivery.getEventId())
                .body(delivery.getPayload())
                .retrieve()
                .toBodilessEntity();
            delivery.setStatus("DELIVERED");
            delivery.setDeliveredAt(Instant.now());
            delivery.setLastFailure(null);
            deliveryRepository.save(delivery);
            log.info("Webhook delivered: eventId={} attempt={}",
                delivery.getEventId(), delivery.getAttemptCount());
        } catch (Exception exception) {
            int attempts = delivery.getAttemptCount();
            delivery.setLastFailure(exception.getMessage());
            if (attempts >= MAX_ATTEMPTS) {
                delivery.setStatus("DEAD");
            } else {
                delivery.setStatus("RETRY");
                long backoffSeconds = Math.min(3600L, 5L << Math.min(attempts - 1, 9));
                delivery.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
            }
            deliveryRepository.save(delivery);
            log.warn("Webhook delivery failed: eventId={} attempts={} status={}",
                delivery.getEventId(), attempts, delivery.getStatus(), exception);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
