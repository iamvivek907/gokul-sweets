package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.payroll.dto.PayrollPaymentApprovalHistoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/me/payroll/payment-requests")
@RequiredArgsConstructor
public class StaffPayrollPaymentHistoryController {

    private final StaffPayrollPaymentHistoryService
            staffPayrollPaymentHistoryService;


    @GetMapping("/{paymentRequestId}/history")
    public ResponseEntity<List<PayrollPaymentApprovalHistoryResponse>>
    getHistory(

            @PathVariable
            Long paymentRequestId
    ) {

        return ResponseEntity.ok(
                staffPayrollPaymentHistoryService
                        .getMyPaymentHistory(
                                paymentRequestId
                        )
        );
    }
}
