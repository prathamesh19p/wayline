package com.wayline.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for OutboxEvent entity.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Find all pending events to publish.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status IN ('PENDING', 'FAILED') ORDER BY e.createdAt ASC")
    List<OutboxEvent> findPendingEvents();

    /**
     * Find events that need retry.
     */
    @Query("SELECT e FROM OutboxEvent e WHERE e.status = 'FAILED' AND e.retryCount < 4 ORDER BY e.createdAt ASC")
    List<OutboxEvent> findEventsNeedingRetry();
}
