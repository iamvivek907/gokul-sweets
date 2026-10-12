package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.ProductIntelligenceResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** HTTP endpoints for product intelligence operations. */
@RestController
@RequestMapping("/api/admin/reports/products")
@RequiredArgsConstructor
public class ProductIntelligenceController {

    private final ProductIntelligenceService productIntelligenceService;

    /**
     * Returns product intelligence.
     *
     * @param fromDate the from date
     * @param toDate the to date
     * @param branchId the branch id
     * @return the get product intelligence result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ProductIntelligenceResponse> getProductIntelligence(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                    LocalDate toDate,
            @RequestParam(required = false) Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        ProductIntelligenceController.class,
                        "getProductIntelligence(LocalDate,LocalDate,Long)");
        try {
            return ResponseEntity.ok(
                    productIntelligenceService.getProductIntelligence(fromDate, toDate, branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    ProductIntelligenceController.class,
                    "getProductIntelligence(LocalDate,LocalDate,Long)");
        }
    }
}
