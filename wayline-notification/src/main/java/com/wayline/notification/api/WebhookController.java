package com.wayline.notification.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wayline.common.security.WebhookSignatureVerifier;
import com.wayline.payment.application.PaymentService;
import com.wayline.payment.domain.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentService paymentService;
    private final ObjectMapper objectMapper;

    @Value("${wayline.webhooks.secret:}")
    private String webhookSecret;

    @PostMapping("/{provider}")
    public ResponseEntity<Void> receiveWebhook(
        @PathVariable String provider,
        @RequestHeader("X-Provider-Signature") String signature,
        @RequestBody String payload
    ) {
        if (!signatureVerifier.verify(payload, signature, webhookSecret)) {
            return ResponseEntity.badRequest().build();
        }
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = requiredText(event, "eventId");
            String eventType = event.path("eventType").asText("payment.status");
            long paymentId = event.path("paymentId").asLong(0);
            if (paymentId == 0) {
                return ResponseEntity.badRequest().build();
            }

            PaymentStatus status = mapStatus(event.path("status").asText(
                event.path("event").asText("UNKNOWN")
            ));
            paymentService.processWebhook(provider, eventId, paymentId, eventType, payload, status);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        } catch (Exception exception) {
            return ResponseEntity.badRequest().build();
        }
    }

    private String requiredText(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }

    private PaymentStatus mapStatus(String status) {
        String normalized = status.toUpperCase();
        if (normalized.contains("SUCCESS") || normalized.contains("SUCCEEDED") || normalized.contains("PAID")) {
            return PaymentStatus.SUCCESS;
        }
        if (normalized.contains("FAIL") || normalized.contains("DECLIN")) {
            return PaymentStatus.FAILED;
        }
        throw new IllegalArgumentException("Unsupported webhook status: " + status);
    }
}
