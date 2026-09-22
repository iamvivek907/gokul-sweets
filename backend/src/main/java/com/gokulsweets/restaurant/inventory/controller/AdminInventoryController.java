package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryService;
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
import java.util.List;

@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final AdminInventoryService adminInventoryService;
    private final AdminInventoryWorkspaceService workspaceService;
    private final StaffAuthorizationService authorizationService;

    @PutMapping("/policies/{branchProductId}")
    public ResponseEntity<InventoryPolicyResponse> upsertPolicy(
            @PathVariable Long branchProductId,
            @Valid @RequestBody AdminInventoryPolicyRequest request
    ) {
        authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(
                adminInventoryService.upsertPolicy(branchProductId, request)
        );
    }

    @PutMapping("/allocations/{branchProductId}/{serviceDate}")
    public ResponseEntity<InventoryAllocationResponse> approveAllocation(
            @PathVariable Long branchProductId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @Valid @RequestBody AdminAllocationApprovalRequest request,
            Authentication authentication
    ) {
        authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(adminInventoryService.approveAllocation(
                branchProductId,
                serviceDate,
                request,
                authentication.getName()
        ));
    }

    @PatchMapping("/allocations/{branchProductId}/{serviceDate}/readiness")
    public ResponseEntity<InventoryAllocationResponse> updateReadiness(
            @PathVariable Long branchProductId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @Valid @RequestBody AdminReadinessUpdateRequest request,
            Authentication authentication
    ) {
        authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(adminInventoryService.updateReadiness(
                branchProductId,
                serviceDate,
                request,
                authentication.getName()
        ));
    }

    @GetMapping("/allocations")
    public ResponseEntity<List<InventoryAllocationResponse>> getAllocations(
            @RequestParam Long branchId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate
    ) {
        authorizationService.requirePermission(PermissionName.INVENTORY_VIEW);
        authorizationService.requireBranchAccess(branchId);
        return ResponseEntity.ok(
                adminInventoryService.getAllocations(branchId, serviceDate)
        );
    }

    private void authorizeBranchProduct(
            Long branchProductId,
            PermissionName permission
    ) {
        authorizationService.requirePermission(permission);
        authorizationService.requireBranchAccess(
                workspaceService.getBranchIdForBranchProduct(branchProductId)
        );
    }
}
