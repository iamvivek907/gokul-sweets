package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** Persistence operations for payroll staff lock records. */
public interface PayrollStaffLockRepository extends JpaRepository<StaffUser, Long> {

    /**
     * Finds by id for update.
     *
     * @param staffUserId the staff user id
     * @return the find by id for update result
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            """
            SELECT s
            FROM StaffUser s
            WHERE s.id = :staffUserId
            """)
    Optional<StaffUser> findByIdForUpdate(@Param("staffUserId") Long staffUserId);
}
