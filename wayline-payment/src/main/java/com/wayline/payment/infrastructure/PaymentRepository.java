package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.Payment;
import com.wayline.payment.domain.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Takes a row lock so concurrent refunds against one payment are serialised. Without it two
     * requests can both read the same refunded total and each pass the remaining-balance check,
     * refunding more than was charged.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") Long id);
}
