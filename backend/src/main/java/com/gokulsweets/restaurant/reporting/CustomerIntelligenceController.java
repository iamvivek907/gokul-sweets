package com.gokulsweets.restaurant.reporting;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.reporting.dto.CustomerIntelligenceResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for customer intelligence operations. */
@RestController
@RequestMapping("/api/admin/reports/customers")
@RequiredArgsConstructor
public class CustomerIntelligenceController {

    private final CustomerIntelligenceService customerIntelligenceService;

    /**
     * Returns customer intelligence.
     *
     * @param search the search
     * @param lifecycle the lifecycle
     * @param valueSegment the value segment
     * @param page the page
     * @param size the size
     * @return the get customer intelligence result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    public ResponseEntity<CustomerIntelligenceResponse> getCustomerIntelligence(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) CustomerLifecycleState lifecycle,
            @RequestParam(required = false) CustomerValueSegment valueSegment,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "25") Integer size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        CustomerIntelligenceController.class,
                        "getCustomerIntelligence(String,CustomerLifecycleState,CustomerValueSegment,Integer,Integer)");
        try {
            return ResponseEntity.ok(
                    customerIntelligenceService.getCustomerIntelligence(
                            search, lifecycle, valueSegment, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    CustomerIntelligenceController.class,
                    "getCustomerIntelligence(String,CustomerLifecycleState,CustomerValueSegment,Integer,Integer)");
        }
    }
}
