package com.wayline.notification.domain;

import java.time.Instant;

public record Notification(
    String eventId,
    NotificationType type,
    String recipient,
    String subject,
    String payload,
    Instant createdAt
) {
    public Notification {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("eventId is required");
        }
        if (type == null) {
            throw new IllegalArgumentException("notification type is required");
        }
        if (recipient == null || recipient.isBlank()) {
            throw new IllegalArgumentException("recipient is required");
        }
        if (payload == null) {
            throw new IllegalArgumentException("payload is required");
        }
        if (createdAt == null) {
            throw new IllegalArgumentException("createdAt is required");
        }
    }
}
