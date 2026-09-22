package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.BusinessInsightsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/insights")
@RequiredArgsConstructor
public class BusinessInsightsController {

    private final BusinessInsightsService
            businessInsightsService;


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<BusinessInsightsResponse>
    getBusinessInsights(

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
                businessInsightsService
                        .getInsights(
                                fromDate,
                                toDate,
                                branchId
                        )
        );
    }
}
