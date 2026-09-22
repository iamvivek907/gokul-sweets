package com.gokulsweets.restaurant.rebate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RebateCustomerRepository
        extends JpaRepository<
        RebateCustomer,
        RebateCustomerId> {

    boolean existsByRebateIdAndCustomerPhone(
            Long rebateId,
            String customerPhone
    );

    List<RebateCustomer> findByRebateId(
            Long rebateId
    );

    void deleteByRebateId(
            Long rebateId
    );
}