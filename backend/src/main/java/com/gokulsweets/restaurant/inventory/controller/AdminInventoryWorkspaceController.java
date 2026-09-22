package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryWorkspaceService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin/inventory/branches/{branchId}")
@RequiredArgsConstructor
public class AdminInventoryWorkspaceController {

    private final AdminInventoryWorkspaceService workspaceService;
    private final StaffAuthorizationService authorizationService;

    @GetMapping("/catalogue")
    public ResponseEntity<InventoryCataloguePageResponse> getCatalogue(
            @PathVariable Long branchId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "ALL") InventoryCatalogueFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size
    ) {
        authorize(branchId, PermissionName.INVENTORY_VIEW);
        return ResponseEntity.ok(workspaceService.getCatalogue(
                branchId,
                serviceDate,
                search,
                categoryId,
                filter,
                page,
                size
        ));
    }

    @PutMapping("/policies/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryPolicyResponse>>
    updatePolicies(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminBulkPolicyRequest request
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(
                workspaceService.updatePolicies(branchId, request)
        );
    }

    @PutMapping("/allocations/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>>
    approveAllocations(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminBulkAllocationRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(workspaceService.approveAllocations(
                branchId,
                request,
                authentication.getName()
        ));
    }

    @PatchMapping("/readiness/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>>
    updateReadiness(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminBulkReadinessRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(workspaceService.updateReadiness(
                branchId,
                request,
                authentication.getName()
        ));
    }

    @PutMapping("/setup/complete")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>>
    completeSetup(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminCompleteInventoryRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(workspaceService.completeSetup(
                branchId,
                request,
                authentication.getName()
        ));
    }

    private void authorize(Long branchId, PermissionName permission) {
        authorizationService.requirePermission(permission);
        authorizationService.requireBranchAccess(branchId);
    }
}
