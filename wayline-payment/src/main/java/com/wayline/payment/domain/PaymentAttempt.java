package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Tracks payment attempts for idempotency and retry purposes.
 */
@Entity
@Table(name = "payment_attempts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(nullable = false, length = 100)
    private String provider;

    @Column(length = 256)
    private String providerPaymentId;

    @Column(nullable = false)
    private Integer attemptNumber;

    @Column(nullable = false, length = 20)
    private String status;

    private Instant requestTime;
    private Instant responseTime;

    @Column(length = 50)
    private String failureCode;

    @Column(columnDefinition = "TEXT")
    private String failureMessage;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    @Default
    private Instant updatedAt = Instant.now();
}
