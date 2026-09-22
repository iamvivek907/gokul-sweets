package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RebateRepository
        extends JpaRepository<Rebate, Long> {

    boolean existsByCodeIgnoreCase(
            String code
    );

    boolean existsByCodeIgnoreCaseAndIdNot(
            String code,
            Long id
    );

    Optional<Rebate> findByCodeIgnoreCase(
            String code
    );

    List<Rebate> findAllByOrderByCreatedAtDesc();

    @Query("""
        SELECT r
        FROM Rebate r
        WHERE r.active = true
          AND r.visibility = com.gokulsweets.restaurant.rebate.RebateVisibility.PUBLIC
          AND r.validFrom <= :now
          AND r.validUntil >= :now
          AND (
                r.branch IS NULL
                OR r.branch.id = :branchId
          )
        ORDER BY r.createdAt DESC
        """)
    List<Rebate> findActivePublicCandidates(
            @Param("branchId") Long branchId,
            @Param("now") LocalDateTime now
    );
}
