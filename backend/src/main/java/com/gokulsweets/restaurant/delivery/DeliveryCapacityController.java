package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for delivery capacity operations. */
@RestController
@RequiredArgsConstructor
public class DeliveryCapacityController {

    private final DeliveryCapacityService capacity;

    /**
     * Lists the operation.
     *
     * @param branchId the branch id
     * @param zoneId the zone id
     * @return the list result
     */
    @GetMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<List<DeliveryCapacityService.Window>> list(
            @PathVariable long branchId, @PathVariable long zoneId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryCapacityController.class, "list(long,long)");
        try {
            if (!capacity.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(capacity.list(branchId, zoneId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryCapacityController.class, "list(long,long)");
        }
    }

    /**
     * Configures the operation.
     *
     * @param branchId the branch id
     * @param zoneId the zone id
     * @param input the input
     * @return the configure result
     */
    @PutMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryCapacityService.Window> configure(
            @PathVariable long branchId,
            @PathVariable long zoneId,
            @Valid @RequestBody DeliveryCapacityService.WindowConfiguration input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryCapacityController.class,
                        "configure(long,long,DeliveryCapacityService.WindowConfiguration)");
        try {
            if (!capacity.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(capacity.configure(branchId, zoneId, input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryCapacityController.class,
                    "configure(long,long,DeliveryCapacityService.WindowConfiguration)");
        }
    }

    /**
     * Quotes the operation.
     *
     * @param request the request
     * @return the quote result
     */
    @PostMapping("/api/storefront/delivery/quote")
    public ResponseEntity<DeliveryCapacityService.Quote> quote(
            @Valid @RequestBody DeliveryCapacityService.QuoteRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryCapacityController.class,
                        "quote(DeliveryCapacityService.QuoteRequest)");
        try {
            if (!capacity.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(capacity.quote(request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryCapacityController.class,
                    "quote(DeliveryCapacityService.QuoteRequest)");
        }
    }
}
