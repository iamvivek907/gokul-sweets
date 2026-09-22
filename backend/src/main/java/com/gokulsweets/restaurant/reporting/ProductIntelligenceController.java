package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.ProductIntelligenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/reports/products")
@RequiredArgsConstructor
public class ProductIntelligenceController {

    private final ProductIntelligenceService
            productIntelligenceService;


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<ProductIntelligenceResponse>
    getProductIntelligence(

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
                productIntelligenceService
                        .getProductIntelligence(
                                fromDate,
                                toDate,
                                branchId
                        )
        );
    }
}
