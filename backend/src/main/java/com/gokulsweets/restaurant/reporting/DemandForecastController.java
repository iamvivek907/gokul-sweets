package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.DemandForecastResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for demand forecast operations. */
@RestController
@RequestMapping("/api/admin/reports/forecast")
@RequiredArgsConstructor
public class DemandForecastController {

    private final DemandForecastService demandForecastService;

    /**
     * Returns forecast.
     *
     * @param targetDate the target date
     * @param branchId the branch id
     * @return the get forecast result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<DemandForecastResponse> getForecast(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DemandForecastController.class, "getForecast(LocalDate,Long)");
        try {
            return ResponseEntity.ok(demandForecastService.forecast(targetDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DemandForecastController.class,
                    "getForecast(LocalDate,Long)");
        }
    }
}
