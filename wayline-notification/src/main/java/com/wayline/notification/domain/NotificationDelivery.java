package com.wayline.notification.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "notification_deliveries", indexes = {
    @Index(name = "idx_notification_delivery_due", columnList = "status,next_attempt_at"),
    @Index(name = "idx_notification_delivery_created", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 256)
    private String eventId;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false, length = 2048)
    private String recipient;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    @Default
    private Integer attemptCount = 0;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    @Column(columnDefinition = "TEXT")
    private String lastFailure;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    private Instant deliveredAt;

    @Version
    private Long version;
}
