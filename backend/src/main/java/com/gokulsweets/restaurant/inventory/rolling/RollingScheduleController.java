package com.gokulsweets.restaurant.inventory.rolling;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Admin-only separate rolling automation; does not edit manual pickup or product policy settings.
 */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/rolling-automation")
@RequiredArgsConstructor
public class RollingScheduleController {

    private final RollingScheduleService service;

    private final StaffAuthorizationService staff;

    /** Reads configuration and run diagnostics for a permitted branch. */
    @GetMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE') and (hasAuthority('INVENTORY_VIEW') or"
                    + " hasAuthority('INVENTORY_MANAGE'))")
    public RollingScheduleService.Workspace get(@PathVariable long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(RollingScheduleController.class, "get(long)");
        try {
            staff.requireBranchAccess(branchId);
            return service.inspect(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, RollingScheduleController.class, "get(long)");
        }
    }

    /** Saves a plan only for staff allowed to manage slots and approve stock. */
    @PutMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE') and hasAuthority('INVENTORY_MANAGE') and"
                    + " hasAuthority('APPROVAL_MANAGE')")
    public RollingScheduleService.Workspace save(
            @PathVariable long branchId,
            @RequestBody RollingScheduleService.Plan plan,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RollingScheduleController.class,
                        "save(long,RollingScheduleService.Plan,Authentication)");
        try {
            staff.requireBranchAccess(branchId);
            service.save(branchId, plan, authentication.getName());
            return service.inspect(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RollingScheduleController.class,
                    "save(long,RollingScheduleService.Plan,Authentication)");
        }
    }

    /** Runs the enabled schedule immediately using the same reconciliation as the worker. */
    @PostMapping("/reconcile")
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE') and hasAuthority('INVENTORY_MANAGE') and"
                    + " hasAuthority('APPROVAL_MANAGE')")
    public RollingScheduleService.Workspace reconcile(
            @PathVariable long branchId,
            Authentication authentication,
            @RequestParam(required = false) Long expectedVersion) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        RollingScheduleController.class, "reconcile(long,Authentication,Long)");
        try {
            staff.requireBranchAccess(branchId);
            service.reconcile(branchId, "MANUAL", authentication.getName(), expectedVersion);
            return service.inspect(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    RollingScheduleController.class,
                    "reconcile(long,Authentication,Long)");
        }
    }
}
