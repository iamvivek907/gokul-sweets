package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StaffPayrollOpeningBalanceRepository
        extends JpaRepository<StaffPayrollOpeningBalance, Long> {

    boolean existsByStaffUserId(
            Long staffUserId
    );

    @EntityGraph(
            attributePaths = {
                    "staffUser",
                    "createdByStaffUser"
            }
    )
    Optional<StaffPayrollOpeningBalance>
    findByStaffUserId(
            Long staffUserId
    );
}
