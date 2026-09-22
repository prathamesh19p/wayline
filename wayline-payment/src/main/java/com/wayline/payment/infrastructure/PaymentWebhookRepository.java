package com.wayline.payment.infrastructure;

import com.wayline.payment.domain.PaymentWebhook;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for PaymentWebhook entity.
 */
@Repository
public interface PaymentWebhookRepository extends JpaRepository<PaymentWebhook, Long> {
    Optional<PaymentWebhook> findByProviderAndProviderEventId(String provider, String providerEventId);
}
