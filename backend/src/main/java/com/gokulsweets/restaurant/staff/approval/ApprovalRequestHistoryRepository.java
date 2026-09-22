package com.gokulsweets.restaurant.staff.approval;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalRequestHistoryRepository
        extends JpaRepository<ApprovalRequestHistory, Long> {

    List<ApprovalRequestHistory>
    findByApprovalRequestIdOrderByCreatedAtAscIdAsc(
            Long approvalRequestId
    );
}
