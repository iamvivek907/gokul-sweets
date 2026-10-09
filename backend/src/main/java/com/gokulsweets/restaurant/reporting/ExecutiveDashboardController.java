package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.ExecutiveDashboardOptionsResponse;
import com.gokulsweets.restaurant.reporting.dto.ExecutiveDashboardResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for executive dashboard operations. */
@RestController
@RequestMapping("/api/admin/reports/dashboard")
@RequiredArgsConstructor
public class ExecutiveDashboardController {

    private final ExecutiveDashboardService executiveDashboardService;

    /**
     * Returns options.
     *
     * @return the get options result
     */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ExecutiveDashboardOptionsResponse> getOptions() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ExecutiveDashboardController.class, "getOptions()");
        try {
            return ResponseEntity.ok(executiveDashboardService.getOptions());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, ExecutiveDashboardController.class, "getOptions()");
        }
    }

    /**
     * Returns dashboard.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get dashboard result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ExecutiveDashboardResponse> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ExecutiveDashboardController.class,
                        "getDashboard(LocalDate,LocalDate,Long)");
        try {
            return ResponseEntity.ok(
                    executiveDashboardService.getDashboard(fromDate, toDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ExecutiveDashboardController.class,
                    "getDashboard(LocalDate,LocalDate,Long)");
        }
    }
}
