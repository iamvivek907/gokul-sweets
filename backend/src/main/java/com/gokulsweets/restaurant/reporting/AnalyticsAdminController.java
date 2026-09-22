package com.gokulsweets.restaurant.reporting;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports/analytics")
@RequiredArgsConstructor
public class AnalyticsAdminController {

    private final AnalyticsRefreshService
            analyticsRefreshService;


    @PostMapping("/rebuild")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<AnalyticsRefreshResponse>
    rebuildAnalytics() {

        return ResponseEntity.ok(
                analyticsRefreshService
                        .rebuildAll()
        );
    }
}
