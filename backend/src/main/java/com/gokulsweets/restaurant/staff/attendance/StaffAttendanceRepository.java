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

/** Persistence operations for staff attendance records. */
public interface StaffAttendanceRepository extends JpaRepository<StaffAttendance, Long> {

    /**
     * Finds by staff user id.
     *
     * @param staffUserId the staff user id
     * @param pageable the pageable
     * @return the find by staff user id result
     */
    @EntityGraph(attributePaths = {"branch", "approvalRequest"})
    Page<StaffAttendance> findByStaffUserId(Long staffUserId, Pageable pageable);

    /**
     * Finds own detailed by id.
     *
     * @param attendanceId the attendance id
     * @param staffUserId the staff user id
     * @return the find own detailed by id result
     */
    @EntityGraph(attributePaths = {"staffUser", "branch", "approvalRequest"})
    @Query(
            """
            SELECT a
            FROM StaffAttendance a
            WHERE a.id = :attendanceId
              AND a.staffUser.id = :staffUserId
            """)
    Optional<StaffAttendance> findOwnDetailedById(
            @Param("attendanceId") Long attendanceId, @Param("staffUserId") Long staffUserId);

    /**
     * Existses blocking attendance.
     *
     * @param staffUserId the staff user id
     * @param branchId the branch id
     * @param attendanceDate the attendance date
     * @param blockingStatuses the blocking statuses
     * @param excludeAttendanceId the exclude attendance id
     * @return the exists blocking attendance result
     */
    @Query(
            """
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
            @Param("staffUserId") Long staffUserId,
            @Param("branchId") Long branchId,
            @Param("attendanceDate") LocalDate attendanceDate,
            @Param("blockingStatuses") Collection<ApprovalRequestStatus> blockingStatuses,
            @Param("excludeAttendanceId") Long excludeAttendanceId);
}
