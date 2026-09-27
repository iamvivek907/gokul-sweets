package com.gokulsweets.restaurant.delivery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/boundary")
public class DeliveryBoundaryController {
    private final DeliveryBoundaryService boundaries;

    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryBoundaryService.Boundary> get(@PathVariable long branchId, @PathVariable long zoneId) {
        if (!boundaries.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(boundaries.get(branchId, zoneId));
    }

    @PutMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryBoundaryService.Boundary> configure(@PathVariable long branchId, @PathVariable long zoneId,
                                                                        @Valid @RequestBody DeliveryBoundaryService.Boundary input) {
        if (!boundaries.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(boundaries.configure(branchId, zoneId, input));
    }
}
