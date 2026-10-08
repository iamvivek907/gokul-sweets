package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** Persistence operations for staff payroll opening balance records. */
public interface StaffPayrollOpeningBalanceRepository
        extends JpaRepository<StaffPayrollOpeningBalance, Long> {

    /**
     * Existses by staff user id.
     *
     * @param staffUserId the staff user id
     * @return the exists by staff user id result
     */
    boolean existsByStaffUserId(Long staffUserId);

    /**
     * Finds by staff user id.
     *
     * @param staffUserId the staff user id
     * @return the find by staff user id result
     */
    @EntityGraph(attributePaths = {"staffUser", "createdByStaffUser"})
    Optional<StaffPayrollOpeningBalance> findByStaffUserId(Long staffUserId);
}
