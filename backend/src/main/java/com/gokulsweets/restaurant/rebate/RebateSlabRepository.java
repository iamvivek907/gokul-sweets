package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RebateSlabRepository
        extends JpaRepository<RebateSlab, Long> {

    List<RebateSlab>
    findByRebateIdOrderByMinimumOrderAmountAsc(
            Long rebateId
    );

    void deleteByRebateId(
            Long rebateId
    );
}