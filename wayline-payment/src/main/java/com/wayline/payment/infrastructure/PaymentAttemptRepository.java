package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.PaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for PaymentAttempt entity.
 */
@Repository
public interface PaymentAttemptRepository extends JpaRepository<PaymentAttempt, Long> {
    List<PaymentAttempt> findByPaymentIdOrderByAttemptNumberAsc(Long paymentId);
}
