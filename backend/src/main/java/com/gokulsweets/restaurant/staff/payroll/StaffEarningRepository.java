package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface StaffEarningRepository
        extends JpaRepository<StaffEarning, Long> {

    boolean existsByAttendanceId(
            Long attendanceId
    );

    Page<StaffEarning> findByStaffUserId(
            Long staffUserId,
            Pageable pageable
    );

    @Query("""
        SELECT COALESCE(SUM(e.amount), 0)
        FROM StaffEarning e
        WHERE e.staffUser.id = :staffUserId
        """)
    BigDecimal sumAmountByStaffUserId(
            @Param("staffUserId")
            Long staffUserId
    );
}
