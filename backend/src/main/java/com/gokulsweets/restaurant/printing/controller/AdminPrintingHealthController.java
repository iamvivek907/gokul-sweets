package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintingHealthResponse;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.service.AdminPrintingHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/printing")
@RequiredArgsConstructor
public class AdminPrintingHealthController {

    private final AdminPrintingHealthService
            printingHealthService;


    @GetMapping("/health")
    @PreAuthorize(
            "hasAuthority('ORDER_VIEW')"
    )
    public ResponseEntity<AdminPrintingHealthResponse> health(

            @RequestParam
            Long branchId,

            @RequestParam(
                    defaultValue = "KITCHEN"
            )
            PrinterStation station
    ) {

        return ResponseEntity.ok(
                printingHealthService
                        .getHealth(
                                branchId,
                                station
                        )
        );
    }
}