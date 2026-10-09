package com.gokulsweets.restaurant.printing.controller;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.printing.dto.admin.AdminPrintingHealthResponse;
import com.gokulsweets.restaurant.printing.enums.PrinterStation;
import com.gokulsweets.restaurant.printing.service.AdminPrintingHealthService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin printing health operations. */
@RestController
@RequestMapping("/api/admin/printing")
@RequiredArgsConstructor
public class AdminPrintingHealthController {

    private final AdminPrintingHealthService printingHealthService;

    /**
     * Handles {@code GET /api/admin/printing/health} for admin printing health.
     *
     * @param branchId the branch id supplied to this method
     * @param station the station supplied to this method
     * @return the value of {@code ResponseEntity.ok(printingHealthService.getHealth(branchId,
     *     station))}
     */
    @GetMapping("/health")
    @PreAuthorize("hasAuthority('ORDER_VIEW')")
    public ResponseEntity<AdminPrintingHealthResponse> health(
            @RequestParam Long branchId,
            @RequestParam(defaultValue = "KITCHEN") PrinterStation station) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminPrintingHealthController.class, "health(Long,PrinterStation)");
        try {
            return ResponseEntity.ok(printingHealthService.getHealth(branchId, station));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminPrintingHealthController.class,
                    "health(Long,PrinterStation)");
        }
    }
}
