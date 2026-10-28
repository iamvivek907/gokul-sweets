package com.gokulsweets.restaurant.config;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/** Permission-protected anonymous storefront performance aggregates. */
@RestController
@RequiredArgsConstructor
public class AdminStorefrontPerformanceController {
    private final StorefrontPerformance performance;
    private final StaffAuthorizationService staff;

    /** Requires menu-management permission before reading aggregates. */
    @GetMapping("/api/admin/storefront/performance")
    public StorefrontPerformance.Snapshot snapshot() {
        long started = MethodTiming.start(AdminStorefrontPerformanceController.class, "snapshot()");
        try {
            staff.requirePermission(PermissionName.MENU_MANAGE);
            return performance.snapshot();
        } finally {
            MethodTiming.finish(started, AdminStorefrontPerformanceController.class, "snapshot()");
        }
    }
}
