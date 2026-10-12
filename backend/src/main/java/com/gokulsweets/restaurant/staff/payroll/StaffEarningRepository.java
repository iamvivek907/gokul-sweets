package com.gokulsweets.restaurant.staff.payroll;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

/** Persistence operations for staff earning records. */
public interface StaffEarningRepository extends JpaRepository<StaffEarning, Long> {

    /**
     * Existses by attendance id.
     *
     * @param attendanceId the attendance id
     * @return the exists by attendance id result
     */
    boolean existsByAttendanceId(Long attendanceId);

    /**
     * Finds by staff user id.
     *
     * @param staffUserId the staff user id
     * @param pageable the pageable
     * @return the find by staff user id result
     */
    Page<StaffEarning> findByStaffUserId(Long staffUserId, Pageable pageable);

    /**
     * Sums amount by staff user id.
     *
     * @param staffUserId the staff user id
     * @return the sum amount by staff user id result
     */
    @Query(
            """
            SELECT COALESCE(SUM(e.amount), 0)
            FROM StaffEarning e
            WHERE e.staffUser.id = :staffUserId
            """)
    BigDecimal sumAmountByStaffUserId(@Param("staffUserId") Long staffUserId);
}
