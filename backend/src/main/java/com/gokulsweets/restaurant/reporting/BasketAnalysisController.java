package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.BasketAnalysisResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for basket analysis operations. */
@RestController
@RequestMapping("/api/admin/reports/basket")
@RequiredArgsConstructor
public class BasketAnalysisController {

    private final BasketAnalysisService basketAnalysisService;

    /**
     * Returns basket analysis.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get basket analysis result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<BasketAnalysisResponse> getBasketAnalysis(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BasketAnalysisController.class,
                        "getBasketAnalysis(LocalDate,LocalDate,Long)");
        try {
            return ResponseEntity.ok(
                    basketAnalysisService.getBasketAnalysis(fromDate, toDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BasketAnalysisController.class,
                    "getBasketAnalysis(LocalDate,LocalDate,Long)");
        }
    }
}
