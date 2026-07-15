package com.paygoon.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.paygoon.model.AiUsage;

public interface AiUsageRepository extends JpaRepository<AiUsage, Long> {
    long countByUserIdAndOperationAndUsedAtAfter(Long userId, String operation, LocalDateTime after);

    boolean existsByUserIdAndOperationAndResourceIdAndUsedAtAfter(
            Long userId, String operation, Long resourceId, LocalDateTime after);

    Optional<AiUsage> findFirstByUserIdAndOperationAndUsedAtAfterOrderByUsedAtAsc(
            Long userId, String operation, LocalDateTime after);
}
