package com.gokulsweets.restaurant.pickup;

import com.gokulsweets.restaurant.pickup.dto.BranchPickupSettingsResponse;
import com.gokulsweets.restaurant.pickup.dto.UpdateBranchPickupSettingsRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(
        "/api/admin/branches/{branchId}/pickup-settings"
)
@RequiredArgsConstructor
public class BranchPickupSettingsAdminController {

    private final BranchPickupSettingsService
            settingsService;


    @GetMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public BranchPickupSettingsResponse getSettings(
            @PathVariable
            Long branchId
    ) {

        return settingsService
                .getSettings(
                        branchId
                );
    }


    @PutMapping
    @PreAuthorize(
            "hasAuthority('BRANCH_MANAGE')"
    )
    public BranchPickupSettingsResponse updateSettings(

            @PathVariable
            Long branchId,

            @Valid
            @RequestBody
            UpdateBranchPickupSettingsRequest request
    ) {

        return settingsService
                .updateSettings(
                        branchId,
                        request
                );
    }
}