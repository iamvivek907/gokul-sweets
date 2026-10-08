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
     * Hourses the operation.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @return the hours result
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
     * Hourses the operation.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param input the input
     * @return the hours result
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
     * Returns the operation.
     *
     * @param branchId the branch id
     * @return the get result
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
     * Saves the operation.
     *
     * @param branchId the branch id
     * @param settings the settings
     * @return the save result
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
