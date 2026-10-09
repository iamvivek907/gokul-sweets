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
     * Handles {@code GET /api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows} for
     * delivery capacity.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @return the {@code ResponseEntity<List<DeliveryCapacityService.Window>>} result
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
     * Handles {@code PUT /api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows} for
     * delivery capacity.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @param input the input supplied to this method
     * @return the {@code ResponseEntity<DeliveryCapacityService.Window>} result
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
     * Handles {@code POST /api/storefront/delivery/quote} for delivery capacity.
     *
     * @param request the request supplied to this method
     * @return the {@code ResponseEntity<DeliveryCapacityService.Quote>} result
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
