package com.gokulsweets.restaurant.delivery;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Validated
public class DeliveryZoneController {
    private final DeliveryZoneService service;

    @GetMapping("/api/admin/branches/{branchId}/delivery-zones")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<List<DeliveryZoneService.Zone>> list(@PathVariable long branchId) {
        if (!service.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.list(branchId));
    }

    @PutMapping("/api/admin/branches/{branchId}/delivery-zones")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryZoneService.Zone> configure(@PathVariable long branchId,
                                                                @Valid @RequestBody DeliveryZoneService.Configuration request) {
        if (!service.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.configure(branchId, request));
    }

    @GetMapping("/api/storefront/delivery/coverage")
    public ResponseEntity<DeliveryZoneService.Coverage> coverage(
            @RequestParam @NotBlank @Size(min = 2, max = 120) String locality,
            @RequestParam @NotBlank @Pattern(regexp = "[0-9]{6}") String postalCode) {
        if (!service.enabled()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(service.coverage(locality, postalCode));
    }
}
