package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.payroll.dto.CompensationResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.PayrollSummaryResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetCompensationRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollController {

    private final AdminPayrollService
            adminPayrollService;


    @GetMapping("/staff/{staffUserId}/summary")
    @PreAuthorize(
            "hasAuthority('PAYROLL_VIEW')"
    )
    public ResponseEntity<PayrollSummaryResponse>
    getSummary(

            @PathVariable
            Long staffUserId
    ) {

        return ResponseEntity.ok(
                adminPayrollService
                        .getSummary(
                                staffUserId
                        )
        );
    }


    @GetMapping("/staff/{staffUserId}/compensation")
    @PreAuthorize(
            "hasAuthority('PAYROLL_VIEW')"
    )
    public ResponseEntity<List<CompensationResponse>>
    getCompensation(

            @PathVariable
            Long staffUserId
    ) {

        return ResponseEntity.ok(
                adminPayrollService
                        .getCompensationHistory(
                                staffUserId
                        )
        );
    }


    @PostMapping("/staff/{staffUserId}/compensation")
    @PreAuthorize(
            "hasAuthority('PAYROLL_MANAGE')"
    )
    public ResponseEntity<CompensationResponse>
    setCompensation(

            @PathVariable
            Long staffUserId,

            @Valid
            @RequestBody
            SetCompensationRequest request
    ) {

        return ResponseEntity.ok(
                adminPayrollService
                        .setCompensation(
                                staffUserId,
                                request
                        )
        );
    }
}
