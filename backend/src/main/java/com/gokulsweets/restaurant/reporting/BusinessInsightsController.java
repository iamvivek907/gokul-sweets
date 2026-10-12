package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.BusinessInsightsResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for business insights operations. */
@RestController
@RequestMapping("/api/admin/reports/insights")
@RequiredArgsConstructor
public class BusinessInsightsController {

    private final BusinessInsightsService businessInsightsService;

    /**
     * Returns business insights.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get business insights result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<BusinessInsightsResponse> getBusinessInsights(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BusinessInsightsController.class,
                        "getBusinessInsights(LocalDate,LocalDate,Long)");
        try {
            return ResponseEntity.ok(
                    businessInsightsService.getInsights(fromDate, toDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BusinessInsightsController.class,
                    "getBusinessInsights(LocalDate,LocalDate,Long)");
        }
    }
}
