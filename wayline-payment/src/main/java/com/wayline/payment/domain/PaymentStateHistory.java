package com.wayline.payment.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payment state transition history for audit and investigation.
 */
@Entity
@Table(name = "payment_state_history")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentStateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long paymentId;

    @Column(length = 20)
    private String fromState;

    @Column(nullable = false, length = 20)
    private String toState;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(length = 50)
    private String source;

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();
}
