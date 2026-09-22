package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestHistoryRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollPaymentApprovalHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class StaffPayrollPaymentHistoryService {

    private final StaffPaymentRequestRepository
            paymentRequestRepository;

    private final ApprovalRequestHistoryRepository
            approvalRequestHistoryRepository;

    private final StaffAuthorizationService
            staffAuthorizationService;


    @Transactional(readOnly = true)
    public List<PayrollPaymentApprovalHistoryResponse> getMyPaymentHistory(
            Long paymentRequestId
    ) {

        StaffUser staff =
                staffAuthorizationService
                        .getCurrentStaff();


        StaffPaymentRequest paymentRequest =
                paymentRequestRepository
                        .findOwnDetailedById(
                                paymentRequestId,
                                staff.getId()
                        )
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "Payment request does not exist."
                                        )
                        );


        Long approvalRequestId =
                paymentRequest
                        .getApprovalRequest()
                        .getId();


        return approvalRequestHistoryRepository
                .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(
                        approvalRequestId
                )
                .stream()
                .map(
                        history ->
                                new PayrollPaymentApprovalHistoryResponse(
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
