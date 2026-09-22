package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.payroll.dto.AdminPayrollStaffOptionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollDirectoryController {

    private final AdminPayrollDirectoryService
            adminPayrollDirectoryService;


    @GetMapping("/staff-options")
    @PreAuthorize(
            "hasAuthority('PAYROLL_VIEW')"
    )
    public ResponseEntity<List<AdminPayrollStaffOptionResponse>>
    getStaffOptions() {

        return ResponseEntity.ok(
                adminPayrollDirectoryService
                        .getStaffOptions()
        );
    }
}
