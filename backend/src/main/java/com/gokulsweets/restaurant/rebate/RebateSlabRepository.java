package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence operations for rebate slab records. */
public interface RebateSlabRepository extends JpaRepository<RebateSlab, Long> {

    /**
     * Performs the find by rebate id order by minimum order amount asc operation for rebate slab
     * repository.
     *
     * @param rebateId the rebate id
     * @return the find by rebate id order by minimum order amount asc result
     */
    List<RebateSlab> findByRebateIdOrderByMinimumOrderAmountAsc(Long rebateId);

    /**
     * Deletes by rebate id.
     *
     * @param rebateId the rebate id
     */
    void deleteByRebateId(Long rebateId);
}
