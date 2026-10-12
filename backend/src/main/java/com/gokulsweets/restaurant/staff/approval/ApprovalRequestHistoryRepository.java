package com.gokulsweets.restaurant.staff.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Persistence operations for approval request history records. */
public interface ApprovalRequestHistoryRepository
        extends JpaRepository<ApprovalRequestHistory, Long> {

    /**
     * Performs the find by approval request id order by created at asc id asc operation for
     * approval request history repository.
     *
     * @param approvalRequestId the approval request id
     * @return the find by approval request id order by created at asc id asc result
     */
    List<ApprovalRequestHistory> findByApprovalRequestIdOrderByCreatedAtAscIdAsc(
            Long approvalRequestId);
}
