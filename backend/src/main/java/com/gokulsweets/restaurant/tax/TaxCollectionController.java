package com.gokulsweets.restaurant.tax;

import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.AccessDeniedException;

@RestController
@RequestMapping("/api/admin/tax-collection")
@RequiredArgsConstructor
public class TaxCollectionController {
    private final TaxCollectionSettings settings;
    private final StaffAuthorizationService staff;
    public record Setting(boolean enabled) {}
    public record Input(@NotNull Boolean enabled) {}
    private com.gokulsweets.restaurant.staff.StaffUser requireOwner() {
        staff.requirePermission(PermissionName.MENU_MANAGE);
        var current=staff.getCurrentStaff();
        if (!"OWNER_ADMIN".equals(current.getRole().getName())) throw new AccessDeniedException("Only an owner can change tax collection for all branches.");
        return current;
    }
    @GetMapping public Setting get() {
        requireOwner();
        return new Setting(settings.enabled());
    }
    @PutMapping public Setting save(@Valid @RequestBody Input input) {
        var current=requireOwner();
        return new Setting(settings.save(input.enabled(),current.getId()));
    }
}
