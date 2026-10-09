package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for analytics admin operations. */
@RestController
@RequestMapping("/api/admin/reports/analytics")
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final AnalyticsRefreshService analyticsRefreshService;

    /**
     * Rebuilds analytics.
     *
     * @return the rebuild analytics result
     */
    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<AnalyticsRefreshResponse> rebuildAnalytics() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AnalyticsAdminController.class, "rebuildAnalytics()");
        try {
            return ResponseEntity.ok(analyticsRefreshService.rebuildAll());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AnalyticsAdminController.class,
                    "rebuildAnalytics()");
        }
    }
}
