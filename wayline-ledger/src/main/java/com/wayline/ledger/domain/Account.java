package com.wayline.ledger.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Ledger account (merchant, platform, provider, clearing).
 */
@Entity
@Table(name = "accounts", indexes = {
    @Index(name = "idx_accounts_owner_id", columnList = "owner_id"),
    @Index(name = "idx_accounts_status", columnList = "status")
}, uniqueConstraints = {
    @UniqueConstraint(columnNames = {"account_type", "owner_id", "currency"}, name = "uk_account_owner_currency")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String accountType; // MERCHANT, PLATFORM, PROVIDER, CLEARING

    @Column(nullable = false, length = 100)
    private String ownerId; // merchant_id, provider_name, or platform

    @Column(nullable = false, length = 3)
    private String currency; // INR, USD, EUR, etc

    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}
