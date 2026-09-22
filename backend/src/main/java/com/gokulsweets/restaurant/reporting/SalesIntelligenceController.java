package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.SalesIntelligenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/sales")
@RequiredArgsConstructor
public class SalesIntelligenceController {

    private final SalesIntelligenceService
            salesIntelligenceService;


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<SalesIntelligenceResponse>
    getSalesIntelligence(

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
                salesIntelligenceService
                        .getSalesIntelligence(
                                fromDate,
                                toDate,
                                branchId
                        )
        );
    }
}
