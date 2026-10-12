package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for tax collection operations. */
@RestController
@RequestMapping("/api/admin/tax-collection")
@RequiredArgsConstructor
public class TaxCollectionController {

    private final TaxCollectionSettings settings;

    private final StaffAuthorizationService staff;

    /**
     * Immutable setting data contract.
     *
     * @param enabled the enabled
     */
    public record Setting(boolean enabled) {}

    /**
     * Immutable input data contract.
     *
     * @param enabled the enabled
     */
    public record Input(@NotNull Boolean enabled) {}

    /**
     * Requires owner.
     *
     * @return the require owner result
     */
    private com.gokulsweets.restaurant.staff.StaffUser requireOwner() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionController.class, "requireOwner()");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            var current = staff.getCurrentStaff();
            if (!"OWNER_ADMIN".equals(current.getRole().getName()))
                throw new AccessDeniedException(
                        "Only an owner can change tax collection for all branches.");
            return current;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCollectionController.class, "requireOwner()");
        }
    }

    /**
     * Handles {@code GET /api/admin/tax-collection} for tax collection.
     *
     * @return the {@code Setting} result
     */
    @GetMapping
    public Setting get() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionController.class, "get()");
        try {
            requireOwner();
            return new Setting(settings.enabled());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, TaxCollectionController.class, "get()");
        }
    }

    /**
     * Handles {@code PUT /api/admin/tax-collection} for tax collection.
     *
     * @param input the input supplied to this method
     * @return the {@code Setting} result
     */
    @PutMapping
    public Setting save(@Valid @RequestBody Input input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(TaxCollectionController.class, "save(Input)");
        try {
            var current = requireOwner();
            return new Setting(settings.save(input.enabled(), current.getId()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, TaxCollectionController.class, "save(Input)");
        }
    }
}
