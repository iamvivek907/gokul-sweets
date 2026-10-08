package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.payroll.dto.CompensationResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetCompensationRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for admin payroll operations. */
@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollController {

    private final AdminPayrollService adminPayrollService;

    /**
     * Returns summary.
     *
     * @param staffUserId the staff user id
     * @return the get summary result
     */
    @GetMapping("/staff/{staffUserId}/summary")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ResponseEntity<PayrollSummaryResponse> getSummary(@PathVariable Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollController.class, "getSummary(Long)");
        try {
            return ResponseEntity.ok(adminPayrollService.getSummary(staffUserId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, AdminPayrollController.class, "getSummary(Long)");
        }
    }

    /**
     * Returns compensation.
     *
     * @param staffUserId the staff user id
     * @return the get compensation result
     */
    @GetMapping("/staff/{staffUserId}/compensation")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ResponseEntity<List<CompensationResponse>> getCompensation(
            @PathVariable Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollController.class, "getCompensation(Long)");
        try {
            return ResponseEntity.ok(adminPayrollService.getCompensationHistory(staffUserId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollController.class,
                    "getCompensation(Long)");
        }
    }

    /**
     * Updates compensation.
     *
     * @param staffUserId the staff user id
     * @param request the request
     * @return the set compensation result
     */
    @PostMapping("/staff/{staffUserId}/compensation")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<CompensationResponse> setCompensation(
            @PathVariable Long staffUserId, @Valid @RequestBody SetCompensationRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollController.class,
                        "setCompensation(Long,SetCompensationRequest)");
        try {
            return ResponseEntity.ok(adminPayrollService.setCompensation(staffUserId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollController.class,
                    "setCompensation(Long,SetCompensationRequest)");
        }
    }
}
