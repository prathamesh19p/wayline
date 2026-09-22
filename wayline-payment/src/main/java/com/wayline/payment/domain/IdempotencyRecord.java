package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Idempotency record to prevent duplicate payment requests.
 */
@Entity
@Table(name = "idempotency_records", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"merchant_id", "idempotency_key"}, name = "uk_idempotency")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IdempotencyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String merchantId;

    @Column(nullable = false, length = 256)
    private String idempotencyKey;

    @Column(nullable = false, length = 256)
    private String requestHash;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 20)
    private String responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant expiresAt;
}
