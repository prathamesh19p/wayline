package com.wayline.common.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.Instant;
import java.util.UUID;

/**
 * Base class for all domain events.
 * Events are immutable records of things that happened in the system.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DomainEvent {
    private String eventId;
    private String eventType;
    private String aggregateId;
    private Instant occurredAt;
    private Integer schemaVersion;

    public DomainEvent(String aggregateId, Instant occurredAt) {
        this.eventId = UUID.randomUUID().toString();
        this.aggregateId = aggregateId;
        this.occurredAt = occurredAt;
        this.schemaVersion = 1;
    }
}
