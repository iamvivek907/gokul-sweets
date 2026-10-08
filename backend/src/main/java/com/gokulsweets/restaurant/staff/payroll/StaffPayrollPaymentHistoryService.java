package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.StaffUser;
import com.gokulsweets.restaurant.staff.approval.ApprovalRequestHistoryRepository;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollPaymentApprovalHistoryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Coordinates staff payroll payment history operations. */
@Service
@RequiredArgsConstructor
public class StaffPayrollPaymentHistoryService {

    private final StaffPaymentRequestRepository paymentRequestRepository;

    private final ApprovalRequestHistoryRepository approvalRequestHistoryRepository;

    private final StaffAuthorizationService staffAuthorizationService;

    /**
     * Returns my payment history.
     *
     * @param paymentRequestId the payment request id
     * @return the get my payment history result
     */
    @Transactional(readOnly = true)
    public List<PayrollPaymentApprovalHistoryResponse> getMyPaymentHistory(Long paymentRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollPaymentHistoryService.class, "getMyPaymentHistory(Long)");
        try {
            StaffUser staff = staffAuthorizationService.getCurrentStaff();
            StaffPaymentRequest paymentRequest =
                    paymentRequestRepository
                            .findOwnDetailedById(paymentRequestId, staff.getId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "Payment request does not exist."));
            Long approvalRequestId = paymentRequest.getApprovalRequest().getId();
            return approvalRequestHistoryRepository
                    .findByApprovalRequestIdOrderByCreatedAtAscIdAsc(approvalRequestId)
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
                                            history.getCreatedAt()))
                    .toList();
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollPaymentHistoryService.class,
                    "getMyPaymentHistory(Long)");
        }
    }
}
