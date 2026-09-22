package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.ExecutiveDashboardOptionsResponse;
import com.gokulsweets.restaurant.reporting.dto.ExecutiveDashboardResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/dashboard")
@RequiredArgsConstructor
public class ExecutiveDashboardController {

    private final ExecutiveDashboardService
            executiveDashboardService;


    @GetMapping("/options")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ExecutiveDashboardOptionsResponse>
    getOptions() {

        return ResponseEntity.ok(
                executiveDashboardService
                        .getOptions()
        );
    }


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ExecutiveDashboardResponse>
    getDashboard(

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate fromDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate toDate,

            @RequestParam(required = false)
            Long branchId
    ) {

        return ResponseEntity.ok(
                executiveDashboardService
                        .getDashboard(
                                fromDate,
                                toDate,
                                branchId
                        )
        );
    }
}
