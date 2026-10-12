package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.pickup.dto.BranchPickupSettingsResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdateBranchPickupSettingsRequest;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for branch pickup settings admin operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/pickup-settings")
@RequiredArgsConstructor
public class BranchPickupSettingsAdminController {

    private final BranchPickupSettingsService settingsService;

    /**
     * Returns settings.
     *
     * @param branchId the branch id
     * @return the get settings result
     */
    @GetMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchPickupSettingsResponse getSettings(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchPickupSettingsAdminController.class, "getSettings(Long)");
        try {
            return settingsService.getSettings(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsAdminController.class,
                    "getSettings(Long)");
        }
    }

    /**
     * Updates settings.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update settings result
     */
    @PutMapping
    @PreAuthorize("hasAuthority('BRANCH_MANAGE')")
    public BranchPickupSettingsResponse updateSettings(
            @PathVariable Long branchId,
            @Valid @RequestBody UpdateBranchPickupSettingsRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        BranchPickupSettingsAdminController.class,
                        "updateSettings(Long,UpdateBranchPickupSettingsRequest)");
        try {
            return settingsService.updateSettings(branchId, request);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchPickupSettingsAdminController.class,
                    "updateSettings(Long,UpdateBranchPickupSettingsRequest)");
        }
    }
}
