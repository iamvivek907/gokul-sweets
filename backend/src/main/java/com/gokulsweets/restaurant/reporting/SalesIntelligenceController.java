package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.SalesIntelligenceResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for sales intelligence operations. */
@RestController
@RequestMapping("/api/admin/reports/sales")
@RequiredArgsConstructor
public class SalesIntelligenceController {

    private final SalesIntelligenceService salesIntelligenceService;

    /**
     * Returns sales intelligence.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get sales intelligence result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<SalesIntelligenceResponse> getSalesIntelligence(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        SalesIntelligenceController.class,
                        "getSalesIntelligence(LocalDate,LocalDate,Long)");
        try {
            return ResponseEntity.ok(
                    salesIntelligenceService.getSalesIntelligence(fromDate, toDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    SalesIntelligenceController.class,
                    "getSalesIntelligence(LocalDate,LocalDate,Long)");
        }
    }
}
