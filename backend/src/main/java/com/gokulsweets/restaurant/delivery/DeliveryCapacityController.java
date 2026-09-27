package com.gokulsweets.restaurant.delivery;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class DeliveryCapacityController {
    private final DeliveryCapacityService capacity;

    @GetMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<List<DeliveryCapacityService.Window>> list(@PathVariable long branchId, @PathVariable long zoneId) {
        if (!capacity.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(capacity.list(branchId, zoneId));
    }

    @PutMapping("/api/admin/branches/{branchId}/delivery-zones/{zoneId}/windows")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryCapacityService.Window> configure(@PathVariable long branchId, @PathVariable long zoneId,
                                                                     @Valid @RequestBody DeliveryCapacityService.WindowConfiguration input) {
        if (!capacity.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(capacity.configure(branchId, zoneId, input));
    }

    @PostMapping("/api/storefront/delivery/quote")
    public ResponseEntity<DeliveryCapacityService.Quote> quote(@Valid @RequestBody DeliveryCapacityService.QuoteRequest request) {
        if (!capacity.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(capacity.quote(request));
    }
}
