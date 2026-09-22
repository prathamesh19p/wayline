package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payment webhook events from providers.
 */
@Entity
@Table(name = "payment_webhooks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "provider_event_id"}, name = "uk_webhook_event_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(nullable = false, length = 256)
    private String providerEventId;

    private Long paymentId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant receivedAt = Instant.now();

    private Instant processedAt;

    @Column(nullable = false, length = 20)
    @Default
    private String status = "RECEIVED";
}
