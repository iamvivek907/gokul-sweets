package com.gokulsweets.restaurant.staff.leave;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestHistoryRepository;
import com.gokulsweets.restaurant.staff.leave.dto.LeaveApprovalHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffLeaveHistoryService {

    private final LeaveRequestRepository
            leaveRequestRepository;

    private final ApprovalRequestHistoryRepository
            approvalRequestHistoryRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public List<LeaveApprovalHistoryResponse> getMyLeaveApprovalHistory(
            Long leaveRequestId
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();


        LeaveRequest leaveRequest =
                leaveRequestRepository
                        .findOwnDetailedById(
                                leaveRequestId,
                                staff.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Leave request does not exist."
                                        )
                        );


        Long approvalRequestId =
                leaveRequest
                        .getApprovalRequest()
                        .getId();


        return approvalRequestHistoryRepository
                .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(
                        approvalRequestId
                )
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
                                        history.getCreatedAt()
                                )
                )
                .toList();
    }
}
