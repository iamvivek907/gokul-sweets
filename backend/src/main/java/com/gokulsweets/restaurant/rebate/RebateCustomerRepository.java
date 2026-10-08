package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence operations for rebate customer records. */
public interface RebateCustomerRepository extends JpaRepository<RebateCustomer, RebateCustomerId> {

    /**
     * Existses by rebate id and customer phone.
     *
     * @param rebateId the rebate id
     * @param customerPhone the customer phone
     * @return the exists by rebate id and customer phone result
     */
    boolean existsByRebateIdAndCustomerPhone(Long rebateId, String customerPhone);

    /**
     * Finds by rebate id.
     *
     * @param rebateId the rebate id
     * @return the find by rebate id result
     */
    List<RebateCustomer> findByRebateId(Long rebateId);

    /**
     * Deletes by rebate id.
     *
     * @param rebateId the rebate id
     */
    void deleteByRebateId(Long rebateId);
}
