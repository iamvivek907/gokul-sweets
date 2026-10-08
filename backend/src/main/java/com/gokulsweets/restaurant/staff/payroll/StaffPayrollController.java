package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** HTTP endpoints for staff payroll operations. */
@RestController
@RequestMapping("/api/admin/me/payroll")
@RequiredArgsConstructor
public class StaffPayrollController {

    private final StaffPayrollService staffPayrollService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    public ResponseEntity<PayrollOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollController.class, "getOptions()");
        try {
            return ResponseEntity.ok(staffPayrollService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollController.class, "getOptions()");
        }
    }

    /**
     * Returns summary.
     *
     * @return the get summary result
     */
    @GetMapping("/summary")
    public ResponseEntity<PayrollSummaryResponse> getSummary() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollController.class, "getSummary()");
        try {
            return ResponseEntity.ok(staffPayrollService.getMySummary());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, StaffPayrollController.class, "getSummary()");
        }
    }

    /**
     * Returns earnings.
     *
     * @param page the page
     * @param size the size
     * @return the get earnings result
     */
    @GetMapping("/earnings")
    public ResponseEntity<Page<EarningResponse>> getEarnings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollController.class, "getEarnings(int,int)");
        try {
            return ResponseEntity.ok(staffPayrollService.getMyEarnings(page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "getEarnings(int,int)");
        }
    }

    /**
     * Returns payment requests.
     *
     * @param page the page
     * @param size the size
     * @return the get payment requests result
     */
    @GetMapping("/payment-requests")
    public ResponseEntity<Page<PaymentRequestResponse>> getPaymentRequests(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(StaffPayrollController.class, "getPaymentRequests(int,int)");
        try {
            return ResponseEntity.ok(staffPayrollService.getMyPaymentRequests(page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "getPaymentRequests(int,int)");
        }
    }

    /**
     * Creates payment request.
     *
     * @param request the request
     * @return the create payment request result
     */
    @PostMapping("/payment-requests")
    public ResponseEntity<PaymentRequestResponse> createPaymentRequest(
            @Valid @RequestBody CreatePaymentRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollController.class, "createPaymentRequest(CreatePaymentRequest)");
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(staffPayrollService.createPaymentRequest(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "createPaymentRequest(CreatePaymentRequest)");
        }
    }

    /**
     * Updates payment request.
     *
     * @param paymentRequestId the payment request id
     * @param request the request
     * @return the update payment request result
     */
    @PutMapping("/payment-requests/{paymentRequestId}")
    public ResponseEntity<PaymentRequestResponse> updatePaymentRequest(
            @PathVariable Long paymentRequestId, @Valid @RequestBody UpdatePaymentRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollController.class,
                        "updatePaymentRequest(Long,UpdatePaymentRequest)");
        try {
            return ResponseEntity.ok(
                    staffPayrollService.updateSentBackPayment(paymentRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "updatePaymentRequest(Long,UpdatePaymentRequest)");
        }
    }

    /**
     * Resubmits the operation.
     *
     * @param paymentRequestId the payment request id
     * @param request the request
     * @return the resubmit result
     */
    @PostMapping("/payment-requests/{paymentRequestId}/resubmit")
    public ResponseEntity<PaymentRequestResponse> resubmit(
            @PathVariable Long paymentRequestId, @Valid @RequestBody PayrollActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollController.class, "resubmit(Long,PayrollActionRequest)");
        try {
            return ResponseEntity.ok(staffPayrollService.resubmit(paymentRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "resubmit(Long,PayrollActionRequest)");
        }
    }

    /**
     * Cancels the operation.
     *
     * @param paymentRequestId the payment request id
     * @param request the request
     * @return the cancel result
     */
    @PostMapping("/payment-requests/{paymentRequestId}/cancel")
    public ResponseEntity<PaymentRequestResponse> cancel(
            @PathVariable Long paymentRequestId, @Valid @RequestBody PayrollActionRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        StaffPayrollController.class, "cancel(Long,PayrollActionRequest)");
        try {
            return ResponseEntity.ok(staffPayrollService.cancel(paymentRequestId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    StaffPayrollController.class,
                    "cancel(Long,PayrollActionRequest)");
        }
    }
}
