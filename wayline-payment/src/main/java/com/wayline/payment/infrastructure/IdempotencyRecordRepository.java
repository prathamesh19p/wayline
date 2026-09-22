package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for IdempotencyRecord entity.
 */
@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, Long> {
    Optional<IdempotencyRecord> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);
}
