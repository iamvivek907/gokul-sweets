package com.gokulsweets.restaurant.staff.attendance;

import com.gokulsweets.restaurant.staff.approval.ApprovalRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;

public interface StaffAttendanceRepository
        extends JpaRepository<StaffAttendance, Long> {

    @EntityGraph(
            attributePaths = {
                    "branch",
                    "approvalRequest"
            }
    )
    Page<StaffAttendance> findByStaffUserId(
            Long staffUserId,
            Pageable pageable
    );

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
        WHERE a.id = :attendanceId
          AND a.staffUser.id = :staffUserId
        """)
    Optional<StaffAttendance> findOwnDetailedById(
            @Param("attendanceId")
            Long attendanceId,
            @Param("staffUserId")
            Long staffUserId
    );

    @Query("""
        SELECT CASE
            WHEN COUNT(a) > 0 THEN true
            ELSE false
        END
        FROM StaffAttendance a
        WHERE a.staffUser.id = :staffUserId
          AND a.branch.id = :branchId
          AND a.attendanceDate = :attendanceDate
          AND a.approvalRequest.status IN :blockingStatuses
          AND (:excludeAttendanceId IS NULL OR a.id <> :excludeAttendanceId)
        """)
    boolean existsBlockingAttendance(
            @Param("staffUserId")
            Long staffUserId,
            @Param("branchId")
            Long branchId,
            @Param("attendanceDate")
            LocalDate attendanceDate,
            @Param("blockingStatuses")
            Collection<ApprovalRequestStatus> blockingStatuses,
            @Param("excludeAttendanceId")
            Long excludeAttendanceId
    );
}
