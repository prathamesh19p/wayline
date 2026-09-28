package com.wayline.notification.infrastructure;

import com.wayline.notification.domain.NotificationDelivery;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {
    Optional<NotificationDelivery> findByEventId(String eventId);

    List<NotificationDelivery> findByStatusOrderByCreatedAtAsc(String status);

    List<NotificationDelivery> findByStatusInAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
        List<String> statuses,
        Instant dueAt,
        Pageable pageable
    );

    @Modifying
    @Query("update NotificationDelivery delivery set delivery.status = 'IN_PROGRESS', "
        + "delivery.attemptCount = delivery.attemptCount + 1, "
        + "delivery.nextAttemptAt = :leaseUntil "
        + "where delivery.id = :id and delivery.status in ('PENDING', 'RETRY', 'IN_PROGRESS') "
        + "and delivery.nextAttemptAt <= :now")
    @Transactional
    int claim(@Param("id") Long id, @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);
}
