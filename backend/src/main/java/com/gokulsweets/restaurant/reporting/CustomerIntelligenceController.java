package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.reporting.dto.CustomerIntelligenceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/reports/customers")
@RequiredArgsConstructor
public class CustomerIntelligenceController {

    private final CustomerIntelligenceService
            customerIntelligenceService;


    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<CustomerIntelligenceResponse>
    getCustomerIntelligence(

            @RequestParam(required = false)
            String search,

            @RequestParam(required = false)
            CustomerLifecycleState lifecycle,

            @RequestParam(required = false)
            CustomerValueSegment valueSegment,

            @RequestParam(defaultValue = "0")
            Integer page,

            @RequestParam(defaultValue = "25")
            Integer size
    ) {

        return ResponseEntity.ok(
                customerIntelligenceService
                        .getCustomerIntelligence(
                                search,
                                lifecycle,
                                valueSegment,
                                page,
                                size
                        )
        );
    }
}
