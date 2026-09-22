package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

/**
 * Repository for Payment entity.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    /**
     * Find payment by merchant ID and idempotency key.
     * Used for idempotency checks.
     *
     * @param merchantId Merchant identifier
     * @param idempotencyKey Unique request key
     * @return Payment if found
     */
    Optional<Payment> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    List<Payment> findByStatusAndCurrency(PaymentStatus status, String currency);
}
