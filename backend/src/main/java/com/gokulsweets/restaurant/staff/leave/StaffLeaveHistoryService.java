package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestHistoryRepository;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveApprovalHistoryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates staff leave history operations. */
@Service
@RequiredArgsConstructor
public class StaffLeaveHistoryService {

    private final LeaveRequestRepository leaveRequestRepository;

    private final ApprovalRequestHistoryRepository approvalRequestHistoryRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns my leave approval history.
     *
     * @param leaveRequestId the leave request id
     * @return the get my leave approval history result
     */
    @Transactional(readOnly = true)
    public List<LeaveApprovalHistoryResponse> getMyLeaveApprovalHistory(Long leaveRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffLeaveHistoryService.class, "getMyLeaveApprovalHistory(Long)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            LeaveRequest leaveRequest =
                    leaveRequestRepository
                            .findOwnDetailedById(leaveRequestId, staff.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Leave request does not exist."));
            Long approvalRequestId = leaveRequest.getApprovalRequest().getId();
            return approvalRequestHistoryRepository
                    .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(approvalRequestId)
                    .stream()
                    .map(
                            history ->
                                    new LeaveApprovalHistoryResponse(
                                            history.getId(),
                                            history.getAction(),
                                            history.getFromStatus(),
                                            history.getToStatus(),
                                            history.getActorName(),
                                            history.getComment(),
                                            history.getCreatedAt()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffLeaveHistoryService.class,
                    "getMyLeaveApprovalHistory(Long)");
        }
    }
}
