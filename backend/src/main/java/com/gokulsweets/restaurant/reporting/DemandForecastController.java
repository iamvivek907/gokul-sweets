package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.DemandForecastResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/forecast")
@RequiredArgsConstructor
public class DemandForecastController {

    private final DemandForecastService
            demandForecastService;


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<DemandForecastResponse>
    getForecast(

            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate targetDate,

            @RequestParam
            Long branchId
    ) {

        return ResponseEntity.ok(
                demandForecastService
                        .forecast(
                                targetDate,
                                branchId
                        )
        );
    }
}
