package com.gokulsweets.restaurant.staff.payroll;

import com.gokulsweets.restaurant.staff.payroll.dto.PayrollOpeningBalanceResponse;
import com.gokulsweets.restaurant.staff.payroll.dto.SetPayrollOpeningBalanceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/payroll")
@RequiredArgsConstructor
public class AdminPayrollOpeningBalanceController {

    private final AdminPayrollOpeningBalanceService
            openingBalanceService;


    @GetMapping("/staff/{staffUserId}/opening-balance")
    @PreAuthorize(
            "hasAuthority('PAYROLL_VIEW')"
    )
    public ResponseEntity<PayrollOpeningBalanceResponse>
    getOpeningBalance(

            @PathVariable
            Long staffUserId
    ) {

        PayrollOpeningBalanceResponse response =
                openingBalanceService
                        .getOpeningBalance(
                                staffUserId
                        );

        if (
                response == null
        ) {

            return ResponseEntity
                    .noContent()
                    .build();
        }

        return ResponseEntity.ok(
                response
        );
    }


    @PostMapping("/staff/{staffUserId}/opening-balance")
    @PreAuthorize(
            "hasAuthority('PAYROLL_MANAGE')"
    )
    public ResponseEntity<PayrollOpeningBalanceResponse>
    createOpeningBalance(

            @PathVariable
            Long staffUserId,

            @Valid
            @RequestBody
            SetPayrollOpeningBalanceRequest request
    ) {

        return ResponseEntity.ok(
                openingBalanceService
                        .createOpeningBalance(
                                staffUserId,
                                request
                        )
        );
    }
}
