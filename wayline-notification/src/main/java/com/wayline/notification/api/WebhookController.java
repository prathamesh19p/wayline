package com.wayline.notification.api;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.wayline.common.security.WebhookSignatureVerifier;
import com.wayline.payment.application.PaymentService;
import com.wayline.payment.application.RefundService;
import com.wayline.payment.domain.PaymentStatus;
import com.wayline.payment.domain.RefundStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Webhooks", description = "Inbound provider callbacks.")
public class WebhookController {
    private final WebhookSignatureVerifier signatureVerifier;
    private final PaymentService paymentService;
    private final RefundService refundService;
    private final ObjectMapper objectMapper;

    @Value("${wayline.webhooks.secret:}")
    private String webhookSecret;

    @Operation(
        summary = "Receive a provider status callback",
        description = """
            Authenticated by HMAC-SHA256 over the raw body rather than by a bearer token, since \
            the caller is the provider and not a merchant. Send the hex digest in \
            `X-Provider-Signature`.

            Delivery is idempotent: the provider's `eventId` is recorded, and a repeat of an \
            already-processed event is accepted and ignored. Callbacks that arrive out of order \
            cannot move a payment out of a terminal state.""")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Accepted, or already processed"),
        @ApiResponse(responseCode = "400", description = "Malformed JSON or missing paymentId"),
        @ApiResponse(responseCode = "401", description = "Signature did not match"),
        @ApiResponse(responseCode = "422",
            description = "Well-formed, but not applicable to the payment's current state")
    })
    @SecurityRequirements
    @PostMapping("/{provider}")
    public ResponseEntity<Void> receiveWebhook(
        @PathVariable String provider,
        @RequestHeader("X-Provider-Signature") String signature,
        @RequestBody String payload
    ) {
        if (!signatureVerifier.verify(payload, signature, webhookSecret)) {
            log.warn("Rejected webhook from {}: signature mismatch", provider);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            JsonNode event = objectMapper.readTree(payload);
            String eventId = requiredText(event, "eventId");
            String eventType = event.path("eventType").asText("payment.status");
            if (eventType.toLowerCase().startsWith("refund")) {
                processRefundWebhook(provider, eventId, payload, event);
                return ResponseEntity.accepted().build();
            }
            long paymentId = event.path("paymentId").asLong(0);
            if (paymentId == 0) {
                log.warn("Rejected webhook {} from {}: missing paymentId", eventId, provider);
                return ResponseEntity.badRequest().build();
            }

            PaymentStatus status = mapStatus(event.path("status").asText(
                event.path("event").asText("UNKNOWN")
            ));
            paymentService.processWebhook(provider, eventId, paymentId, eventType, payload, status);
            return ResponseEntity.accepted().build();
        } catch (JacksonException exception) {
            log.warn("Rejected webhook from {}: malformed JSON", provider, exception);
            return ResponseEntity.badRequest().build();
        } catch (IllegalArgumentException | IllegalStateException exception) {
            log.warn("Could not apply webhook from {}: {}", provider, exception.getMessage());
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).build();
        }
    }

    private void processRefundWebhook(String provider, String eventId, String payload, JsonNode event) {
        Long refundId = event.hasNonNull("refundId") ? event.get("refundId").asLong() : null;
        String providerRefundId = event.path("providerRefundId").asText(null);
        if (refundId == null && (providerRefundId == null || providerRefundId.isBlank())) {
            throw new IllegalArgumentException("refundId or providerRefundId is required");
        }
        RefundStatus status = mapRefundStatus(event.path("status").asText(
            event.path("event").asText("UNKNOWN")));
        refundService.resolveWebhook(provider, eventId, payload, refundId, providerRefundId, status);
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

    private RefundStatus mapRefundStatus(String status) {
        String normalized = status.toUpperCase();
        if (normalized.contains("SUCCESS") || normalized.contains("SUCCEEDED")
            || normalized.contains("REFUNDED")) {
            return RefundStatus.SUCCESS;
        }
        if (normalized.contains("FAIL") || normalized.contains("DECLIN")) {
            return RefundStatus.FAILED;
        }
        throw new IllegalArgumentException("Unsupported refund webhook status: " + status);
    }
}
