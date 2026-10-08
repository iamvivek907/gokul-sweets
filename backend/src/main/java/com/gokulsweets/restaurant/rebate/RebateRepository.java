package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/** Persistence operations for rebate records. */
public interface RebateRepository extends JpaRepository<Rebate, Long> {

    /**
     * Existses by code ignore case.
     *
     * @param code the code
     * @return the exists by code ignore case result
     */
    boolean existsByCodeIgnoreCase(String code);

    /**
     * Existses by code ignore case and id not.
     *
     * @param code the code
     * @param id the id
     * @return the exists by code ignore case and id not result
     */
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    /**
     * Finds by code ignore case.
     *
     * @param code the code
     * @return the find by code ignore case result
     */
    Optional<Rebate> findByCodeIgnoreCase(String code);

    /**
     * Finds all by order by created at desc.
     *
     * @return the find all by order by created at desc result
     */
    List<Rebate> findAllByOrderByCreatedAtDesc();

    /**
     * Finds active public candidates.
     *
     * @param branchId the branch id
     * @param now the now
     * @return the find active public candidates result
     */
    @Query(
            """
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
            @Param("branchId") Long branchId, @Param("now") LocalDateTime now);
}
