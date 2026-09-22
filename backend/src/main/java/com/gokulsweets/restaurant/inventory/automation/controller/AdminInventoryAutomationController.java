package com.gokulsweets.restaurant.inventory.automation.controller;

import com.gokulsweets.restaurant.inventory.automation.dto.*;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationWorkspaceService;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/inventory/branches/{branchId}/automation")
@RequiredArgsConstructor
public class AdminInventoryAutomationController {
    private final InventoryAutomationWorkspaceService workspaceService;
    private final InventoryAutomationGenerationService generationService;
    private final StaffAuthorizationService authorizationService;

    @GetMapping
    public ResponseEntity<AutomationWorkspaceResponse> getWorkspace(
            @PathVariable Long branchId
    ) {
        authorize(branchId, PermissionName.INVENTORY_VIEW);
        return ResponseEntity.ok(workspaceService.getWorkspace(branchId));
    }

    @PutMapping("/rules/{branchProductId}")
    public ResponseEntity<AutomationRuleResponse> updateRule(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @Valid @RequestBody AutomationRuleUpdateRequest request
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(workspaceService.updateRule(branchId, branchProductId, request));
    }

    @PostMapping("/generate")
    public ResponseEntity<AutomationRunResponse> generate(
            @PathVariable Long branchId,
            @Valid @RequestBody AutomationGenerationRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        return ResponseEntity.ok(generationService.generate(
                branchId,
                request.fromDate(),
                request.throughDate(),
                InventoryAutomationTrigger.MANUAL,
                authentication.getName()
        ));
    }

    private void authorize(Long branchId, PermissionName permission) {
        authorizationService.requirePermission(permission);
        authorizationService.requireBranchAccess(branchId);
    }
}
