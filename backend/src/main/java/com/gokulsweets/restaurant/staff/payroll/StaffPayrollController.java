package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.payroll.dto.CreatePaymentRequest;
import com.gokulsweets.restaurant.staff.payroll.dto.EarningResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PaymentRequestResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollActionRequest;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOptionsResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.UpdatePaymentRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/me/payroll")
@RequiredArgsConstructor
public class StaffPayrollController {

    private final StaffPayrollService
            staffPayrollService;


    @GetMapping("/options")
    public ResponseEntity<PayrollOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                staffPayrollService
                        .getOptions()
        );
    }


    @GetMapping("/summary")
    public ResponseEntity<PayrollSummaryResponse>
    getSummary() {

        return ResponseEntity.ok(
                staffPayrollService
                        .getMySummary()
        );
    }


    @GetMapping("/earnings")
    public ResponseEntity<Page<EarningResponse>>
    getEarnings(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ResponseEntity.ok(
                staffPayrollService
                        .getMyEarnings(
                                page,
                                size
                        )
        );
    }


    @GetMapping("/payment-requests")
    public ResponseEntity<Page<PaymentRequestResponse>>
    getPaymentRequests(

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "20")
            int size
    ) {

        return ResponseEntity.ok(
                staffPayrollService
                        .getMyPaymentRequests(
                                page,
                                size
                        )
        );
    }


    @PostMapping("/payment-requests")
    public ResponseEntity<PaymentRequestResponse>
    createPaymentRequest(

            @Valid
            @RequestBody
            CreatePaymentRequest request
    ) {

        return ResponseEntity
                .status(
                        HttpStatus.CREATED
                )
                .body(
                        staffPayrollService
                                .createPaymentRequest(
                                        request
                                )
                );
    }


    @PutMapping("/payment-requests/{paymentRequestId}")
    public ResponseEntity<PaymentRequestResponse>
    updatePaymentRequest(

            @PathVariable
            Long paymentRequestId,

            @Valid
            @RequestBody
            UpdatePaymentRequest request
    ) {

        return ResponseEntity.ok(
                staffPayrollService
                        .updateSentBackPayment(
                                paymentRequestId,
                                request
                        )
        );
    }


    @PostMapping("/payment-requests/{paymentRequestId}/resubmit")
    public ResponseEntity<PaymentRequestResponse>
    resubmit(

            @PathVariable
            Long paymentRequestId,

            @Valid
            @RequestBody
            PayrollActionRequest request
    ) {

        return ResponseEntity.ok(
                staffPayrollService
                        .resubmit(
                                paymentRequestId,
                                request
                        )
        );
    }


    @PostMapping("/payment-requests/{paymentRequestId}/cancel")
    public ResponseEntity<PaymentRequestResponse>
    cancel(

            @PathVariable
            Long paymentRequestId,

            @Valid
            @RequestBody
            PayrollActionRequest request
    ) {

        return ResponseEntity.ok(
                staffPayrollService
                        .cancel(
                                paymentRequestId,
                                request
                        )
        );
    }
}
