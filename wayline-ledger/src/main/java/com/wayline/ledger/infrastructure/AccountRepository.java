package com.wayline.ledger.infrastructure;

import com.wayline.ledger.domain.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for Account entity.
 */
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByAccountTypeAndOwnerIdAndCurrency(String accountType, String ownerId, String currency);
}
