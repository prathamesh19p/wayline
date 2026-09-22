package com.wayline.settlement.infrastructure;

import com.wayline.settlement.domain.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Settlement entity.
 */
@Repository
public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByProviderAndSettlementReference(String provider, String settlementReference);
    List<Settlement> findByProviderAndSettlementDate(String provider, LocalDate date);
    List<Settlement> findByStatus(String status);
}
