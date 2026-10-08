package com.gokulsweets.restaurant.delivery;

import com.gokulsweets.restaurant.observability.MethodTiming;

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

/** HTTP endpoints for delivery zone operations. */
@RestController
@RequiredArgsConstructor
@Validated
public class DeliveryZoneController {

    private final DeliveryZoneService service;

    /**
     * Lists the operation.
     *
     * @param branchId the branch id
     * @return the list result
     */
    @GetMapping("/api/admin/branches/{branchId}/delivery-zones")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<List<DeliveryZoneService.Zone>> list(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryZoneController.class, "list(long)");
        try {
            if (!service.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(service.list(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, DeliveryZoneController.class, "list(long)");
        }
    }

    /**
     * Configures the operation.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the configure result
     */
    @PutMapping("/api/admin/branches/{branchId}/delivery-zones")
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public ResponseEntity<DeliveryZoneService.Zone> configure(
            @PathVariable long branchId,
            @Valid @RequestBody DeliveryZoneService.Configuration request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        DeliveryZoneController.class,
                        "configure(long,DeliveryZoneService.Configuration)");
        try {
            if (!service.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(service.configure(branchId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryZoneController.class,
                    "configure(long,DeliveryZoneService.Configuration)");
        }
    }

    /**
     * Coverages the operation.
     *
     * @param locality the locality
     * @param postalCode the postal code
     * @return the coverage result
     */
    @GetMapping("/api/storefront/delivery/coverage")
    public ResponseEntity<DeliveryZoneService.Coverage> coverage(
            @RequestParam @NotBlank @Size(min = 2, max = 120) String locality,
            @RequestParam @NotBlank @Pattern(regexp = "[0-9]{6}") String postalCode) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(DeliveryZoneController.class, "coverage(String,String)");
        try {
            if (!service.enabled()) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(service.coverage(locality, postalCode));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    DeliveryZoneController.class,
                    "coverage(String,String)");
        }
    }
}
