package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for delivery boundary operations. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/boundary")
public class DeliveryBoundaryController {

    private final DeliveryBoundaryService boundaries;

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/delivery-zones/{zoneId}/boundary} for
     * delivery boundary.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @return the {@code ResponseEntity<DeliveryBoundaryService.Boundary>} result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryBoundaryService.Boundary> get(
            @PathVariable long branchId, @PathVariable long zoneId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryBoundaryController.class, "get(long,long)");
        try {
            if (!boundaries.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(boundaries.get(branchId, zoneId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryBoundaryController.class, "get(long,long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/delivery-zones/{zoneId}/boundary} for
     * delivery boundary.
     *
     * @param branchId the branch id supplied to this method
     * @param zoneId the zone id supplied to this method
     * @param input the input supplied to this method
     * @return the {@code ResponseEntity<DeliveryBoundaryService.Boundary>} result
     */
    @PutMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryBoundaryService.Boundary> configure(
            @PathVariable long branchId,
            @PathVariable long zoneId,
            @Valid @RequestBody DeliveryBoundaryService.Boundary input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryBoundaryController.class,
                        "configure(long,long,DeliveryBoundaryService.Boundary)");
        try {
            if (!boundaries.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(boundaries.configure(branchId, zoneId, input));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryBoundaryController.class,
                    "configure(long,long,DeliveryBoundaryService.Boundary)");
        }
    }
}
