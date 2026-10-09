package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOpeningBalanceResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetPayrollOpeningBalanceRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin payroll opening balance operations. */
@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollOpeningBalanceController {

    private final AdminPayrollOpeningBalanceService openingBalanceService;

    /**
     * Returns opening balance.
     *
     * @param staffUserId the staff user id
     * @return the get opening balance result
     */
    @GetMapping("/staff/{staffUserId}/opening-balance")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ResponseEntity<PayrollOpeningBalanceResponse> getOpeningBalance(
            @PathVariable Long staffUserId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceController.class, "getOpeningBalance(Long)");
        try {
            PayrollOpeningBalanceResponse response =
                    openingBalanceService.getOpeningBalance(staffUserId);
            if (response == null) {
                return ResponseEntity.noContent().build();
            }
            return ResponseEntity.ok(response);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceController.class,
                    "getOpeningBalance(Long)");
        }
    }

    /**
     * Creates opening balance.
     *
     * @param staffUserId the staff user id
     * @param request the request
     * @return the create opening balance result
     */
    @PostMapping("/staff/{staffUserId}/opening-balance")
    @PreAuthorize("hasAuthority('PAYROLL_MANAGE')")
    public ResponseEntity<PayrollOpeningBalanceResponse> createOpeningBalance(
            @PathVariable Long staffUserId,
            @Valid @RequestBody SetPayrollOpeningBalanceRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPayrollOpeningBalanceController.class,
                        "createOpeningBalance(Long,SetPayrollOpeningBalanceRequest)");
        try {
            return ResponseEntity.ok(
                    openingBalanceService.createOpeningBalance(staffUserId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollOpeningBalanceController.class,
                    "createOpeningBalance(Long,SetPayrollOpeningBalanceRequest)");
        }
    }
}
