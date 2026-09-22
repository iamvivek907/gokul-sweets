package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import com.gokulsweets.restaurant.staff.attendance.StaffAttendance;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollAttendanceRepository
        extends JpaRepository<StaffAttendance, Long> {

    @EntityGraph(
            attributePaths = {
                    "staffUser",
                    "branch",
                    "approvalRequest"
            }
    )
    @Query("""
        SELECT a
        FROM StaffAttendance a
        WHERE a.staffUser.id = :staffUserId
          AND a.approvalRequest.status = :status
          AND NOT EXISTS (
              SELECT e.id
              FROM StaffEarning e
              WHERE e.attendance.id = a.id
          )
        ORDER BY a.attendanceDate ASC, a.id ASC
        """)
    List<StaffAttendance> findUnsyncedApprovedAttendance(
            @Param("staffUserId")
            Long staffUserId,
            @Param("status")
            ApprovalRequestStatus status
    );
}
