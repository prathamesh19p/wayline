package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.Refund;
import com.wayline.payment.domain.RefundStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    Optional<Refund> findByMerchantIdAndIdempotencyKey(String merchantId, String idempotencyKey);

    Optional<Refund> findByProviderAndProviderRefundId(String provider, String providerRefundId);

    List<Refund> findByPaymentIdOrderByCreatedAtAsc(Long paymentId);

    /**
     * Total already committed against a payment. FAILED refunds are excluded because no money
     * moved; UNKNOWN ones are included because money may have moved, and over-refunding is the
     * more expensive mistake.
     */
    @Query("""
        select coalesce(sum(r.amount), 0) from Refund r
        where r.paymentId = :paymentId and r.status <> :excluded""")
    long sumReservedAmountForPayment(@Param("paymentId") Long paymentId,
                                     @Param("excluded") RefundStatus excluded);
}
