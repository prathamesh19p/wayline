package com.wayline.settlement.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Provider settlement record.
 * Represents provider's batch settlement for a day.
 */
@Entity
@Table(name = "settlements", indexes = {
    @Index(name = "idx_settlements_provider", columnList = "provider"),
    @Index(name = "idx_settlements_status", columnList = "status"),
    @Index(name = "idx_settlements_settlement_date", columnList = "settlement_date")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"provider", "settlement_reference"}, name = "uk_settlement_reference")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Settlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String provider; // ProviderA, ProviderB, etc

    @Column(nullable = false, length = 256)
    private String settlementReference; // Provider's reference number

    @Column(nullable = false)
    private LocalDate settlementDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private Long grossAmount; // Total amount before fees

    @Column(nullable = false)
    private Long fees; // Provider fees or platform fees

    @Column(nullable = false)
    private Long netAmount; // Amount after fees (grossAmount - fees)

    @Column(nullable = false, length = 20)
    @Default
    private String status = "PENDING"; // PENDING, PROCESSING, COMPLETED, FAILED

    @Column(nullable = false, updatable = false)
    @Default
    private Instant createdAt = Instant.now();

    private Instant processedAt;
}
