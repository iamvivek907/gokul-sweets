package com.gokulsweets.restaurant.staff.leave;

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

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, Long> {

    @EntityGraph(
            attributePaths = {
                    "branch",
                    "approvalRequest"
            }
    )
    Page<LeaveRequest> findByStaffUserId(
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
        SELECT l
        FROM LeaveRequest l
        WHERE l.id = :leaveRequestId
          AND l.staffUser.id = :staffUserId
        """)
    Optional<LeaveRequest> findOwnDetailedById(
            @Param("leaveRequestId")
            Long leaveRequestId,
            @Param("staffUserId")
            Long staffUserId
    );

    @Query("""
        SELECT CASE
            WHEN COUNT(l) > 0 THEN true
            ELSE false
        END
        FROM LeaveRequest l
        WHERE l.staffUser.id = :staffUserId
          AND l.startDate <= :endDate
          AND l.endDate >= :startDate
          AND l.approvalRequest.status IN :blockingStatuses
          AND (:excludeLeaveRequestId IS NULL OR l.id <> :excludeLeaveRequestId)
        """)
    boolean existsBlockingOverlap(
            @Param("staffUserId")
            Long staffUserId,
            @Param("startDate")
            LocalDate startDate,
            @Param("endDate")
            LocalDate endDate,
            @Param("blockingStatuses")
            Collection<ApprovalRequestStatus> blockingStatuses,
            @Param("excludeLeaveRequestId")
            Long excludeLeaveRequestId
    );
}
