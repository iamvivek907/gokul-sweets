package com.gokulsweets.restaurant.menu;

import com.gokulsweets.restaurant.observability.MethodTiming;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin menu service windows operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/menu-service-windows")
@RequiredArgsConstructor
public class AdminMenuServiceWindowsController {

    private final MenuServiceWindows windows;

    /**
     * Handles {@code GET
     * /api/admin/branches/{branchId}/menu-service-windows/{branchProductId}/hours} for admin menu
     * service windows.
     *
     * @param branchId the branch id supplied to this method
     * @param branchProductId the branch product id supplied to this method
     * @return the value of {@code windows.hours(branchId, branchProductId)}
     */
    @GetMapping("/{branchProductId}/hours")
    public MenuServiceWindows.ItemHours hours(
            @PathVariable long branchId, @PathVariable long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminMenuServiceWindowsController.class, "hours(long,long)");
        try {
            return windows.hours(branchId, branchProductId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminMenuServiceWindowsController.class,
                    "hours(long,long)");
        }
    }

    /**
     * Handles {@code PUT
     * /api/admin/branches/{branchId}/menu-service-windows/{branchProductId}/hours} for admin menu
     * service windows.
     *
     * @param branchId the branch id supplied to this method
     * @param branchProductId the branch product id supplied to this method
     * @param input the input supplied to this method
     * @return the value of {@code windows.saveHours(branchId, branchProductId, input)}
     */
    @PutMapping("/{branchProductId}/hours")
    public MenuServiceWindows.ItemHours hours(
            @PathVariable long branchId,
            @PathVariable long branchProductId,
            @jakarta.validation.Valid @RequestBody MenuServiceWindows.HoursEdit input) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminMenuServiceWindowsController.class,
                        "hours(long,long,MenuServiceWindows.HoursEdit)");
        try {
            return windows.saveHours(branchId, branchProductId, input);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminMenuServiceWindowsController.class,
                    "hours(long,long,MenuServiceWindows.HoursEdit)");
        }
    }

    /**
     * Handles {@code GET /api/admin/branches/{branchId}/menu-service-windows} for admin menu
     * service windows.
     *
     * @param branchId the branch id supplied to this method
     * @return the value of {@code windows.settings(branchId)}
     */
    @GetMapping
    public MenuServiceWindows.Settings get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminMenuServiceWindowsController.class, "get(long)");
        try {
            return windows.settings(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminMenuServiceWindowsController.class,
                    "get(long)");
        }
    }

    /**
     * Handles {@code PUT /api/admin/branches/{branchId}/menu-service-windows} for admin menu
     * service windows.
     *
     * @param branchId the branch id supplied to this method
     * @param settings the settings supplied to this method
     * @return the value of {@code windows.save(branchId, settings)}
     */
    @PutMapping
    public MenuServiceWindows.Settings save(
            @PathVariable long branchId, @RequestBody MenuServiceWindows.Settings settings) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminMenuServiceWindowsController.class,
                        "save(long,MenuServiceWindows.Settings)");
        try {
            return windows.save(branchId, settings);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminMenuServiceWindowsController.class,
                    "save(long,MenuServiceWindows.Settings)");
        }
    }
}
