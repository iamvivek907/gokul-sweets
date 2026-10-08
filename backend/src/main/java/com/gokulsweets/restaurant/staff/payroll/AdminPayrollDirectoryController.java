package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.staff.payroll.dto.AdminPayrollStaffOptionResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for admin payroll directory operations. */
@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollDirectoryController {

    private final AdminPayrollDirectoryService adminPayrollDirectoryService;

    /**
     * Returns staff options.
     *
     * @return the get staff options result
     */
    @GetMapping("/staff-options")
    @PreAuthorize("hasAuthority('PAYROLL_VIEW')")
    public ResponseEntity<List<AdminPayrollStaffOptionResponse>> getStaffOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminPayrollDirectoryController.class, "getStaffOptions()");
        try {
            return ResponseEntity.ok(adminPayrollDirectoryService.getStaffOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPayrollDirectoryController.class,
                    "getStaffOptions()");
        }
    }
}
