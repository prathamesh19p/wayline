package com.wayline.common.kafka;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for DlqMessage entity.
 * Provides access to failed messages for investigation and replay.
 */
@Repository
public interface DlqMessageRepository extends JpaRepository<DlqMessage, Long> {

    /**
     * Find all failed messages.
     */
    List<DlqMessage> findByStatus(String status);

    /**
     * Find failed messages for specific topic.
     */
    List<DlqMessage> findByTopicAndStatus(String topic, String status);
}
