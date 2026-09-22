package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.PaymentStateHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PaymentStateHistory entity.
 */
@Repository
public interface PaymentStateHistoryRepository extends JpaRepository<PaymentStateHistory, Long> {
    List<PaymentStateHistory> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);
}
