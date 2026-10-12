package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollPaymentApprovalHistoryResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for staff payroll payment history operations. */
@RestController
@RequestMapping("/api/admin/me/payroll/payment-requests")
@RequiredArgsConstructor
public class StaffPayrollPaymentHistoryController {

    private final StaffPayrollPaymentHistoryService staffPayrollPaymentHistoryService;

    /**
     * Returns history.
     *
     * @param paymentRequestId the payment request id
     * @return the get history result
     */
    @GetMapping("/{paymentRequestId}/history")
    public ResponseEntity<List<PayrollPaymentApprovalHistoryResponse>> getHistory(
            @PathVariable Long paymentRequestId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollPaymentHistoryController.class, "getHistory(Long)");
        try {
            return ResponseEntity.ok(
                    staffPayrollPaymentHistoryService.getMyPaymentHistory(paymentRequestId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollPaymentHistoryController.class,
                    "getHistory(Long)");
        }
    }
}
