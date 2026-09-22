package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RebateRedemptionRepository
        extends JpaRepository<RebateRedemption, Long> {

    long countByRebateId(
            Long rebateId
    );

    long countByRebateIdAndCustomerPhone(
            Long rebateId,
            String customerPhone
    );

    boolean existsByOrderId(
            Long orderId
    );

    Optional<RebateRedemption> findByOrderId(
            Long orderId
    );
}
