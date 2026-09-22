package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.StaffUser;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PayrollStaffLockRepository
        extends JpaRepository<StaffUser, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT s
        FROM StaffUser s
        WHERE s.id = :staffUserId
        """)
    Optional<StaffUser> findByIdForUpdate(
            @Param("staffUserId")
            Long staffUserId
    );
}
