package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for rebate redemption records. */
public interface RebateRedemptionRepository extends JpaRepository<RebateRedemption, Long> {

    /**
     * Counts by rebate id.
     *
     * @param rebateId the rebate id
     * @return the count by rebate id result
     */
    long countByRebateId(Long rebateId);

    /**
     * Performs the count by rebate id and customer phone operation for rebate redemption
     * repository.
     *
     * @param rebateId the rebate id
     * @param customerPhone the customer phone
     * @return the count by rebate id and customer phone result
     */
    long countByRebateIdAndCustomerPhone(Long rebateId, String customerPhone);

    /**
     * Existses by order id.
     *
     * @param orderId the order id
     * @return the exists by order id result
     */
    boolean existsByOrderId(Long orderId);

    /**
     * Finds by order id.
     *
     * @param orderId the order id
     * @return the find by order id result
     */
    Optional<RebateRedemption> findByOrderId(Long orderId);
}
