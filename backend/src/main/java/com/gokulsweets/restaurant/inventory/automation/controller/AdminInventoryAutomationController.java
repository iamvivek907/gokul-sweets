package com.gokulsweets.restaurant.inventory.automation.controller;

import com.gokulsweets.restaurant.inventory.automation.dto.*;
import com.gokulsweets.restaurant.inventory.automation.enums.InventoryAutomationTrigger;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationGenerationService;
import com.gokulsweets.restaurant.inventory.automation.service.InventoryAutomationWorkspaceService;
import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.security.StaffAuthorizationService;
import com.gokulsweets.restaurant.staff.PermissionName;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/** HTTP endpoints for admin inventory automation operations. */
@RestController
@RequestMapping("/api/admin/inventory/branches/{branchId}/automation")
@RequiredArgsConstructor
public class AdminInventoryAutomationController {

    private final InventoryAutomationWorkspaceService workspaceService;

    private final InventoryAutomationGenerationService generationService;

    private final StaffAuthorizationService authorizationService;

    private final com.gokulsweets.restaurant.inventory.automation.repository
                    .InventoryAutomationRunItemRepository
            runItems;

    /**
     * Explains the operation.
     *
     * @param branchId the branch id
     * @param runId the run id
     * @return the explain result
     */
    @GetMapping("/runs/{runId}/explanation")
    public java.util.List<AutomationRunExplanation> explain(
            @PathVariable Long branchId, @PathVariable Long runId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryAutomationController.class, "explain(Long,Long)");
        try {
            authorize(branchId, PermissionName.INVENTORY_VIEW);
            return runItems.explain(branchId, runId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryAutomationController.class,
                    "explain(Long,Long)");
        }
    }

    /**
     * Returns workspace.
     *
     * @param branchId the branch id
     * @return the get workspace result
     */
    @GetMapping
    public ResponseEntity<AutomationWorkspaceResponse> getWorkspace(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminInventoryAutomationController.class, "getWorkspace(Long)");
        try {
            authorize(branchId, PermissionName.INVENTORY_VIEW);
            return ResponseEntity.ok(workspaceService.getWorkspace(branchId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryAutomationController.class,
                    "getWorkspace(Long)");
        }
    }

    /**
     * Updates rule.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param request the request
     * @return the update rule result
     */
    @PutMapping("/rules/{branchProductId}")
    public ResponseEntity<AutomationRuleResponse> updateRule(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @Valid @RequestBody AutomationRuleUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryAutomationController.class,
                        "updateRule(Long,Long,AutomationRuleUpdateRequest)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    workspaceService.updateRule(branchId, branchProductId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryAutomationController.class,
                    "updateRule(Long,Long,AutomationRuleUpdateRequest)");
        }
    }

    /**
     * Generates the operation.
     *
     * @param branchId the branch id
     * @param request the request
     * @param authentication the authentication
     * @return the generate result
     */
    @PostMapping("/generate")
    public ResponseEntity<AutomationRunResponse> generate(
            @PathVariable Long branchId,
            @Valid @RequestBody AutomationGenerationRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryAutomationController.class,
                        "generate(Long,AutomationGenerationRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    generationService.generate(
                            branchId,
                            request.fromDate(),
                            request.throughDate(),
                            InventoryAutomationTrigger.MANUAL,
                            authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryAutomationController.class,
                    "generate(Long,AutomationGenerationRequest,Authentication)");
        }
    }

    /**
     * Authorizes the operation.
     *
     * @param branchId the branch id
     * @param permission the permission
     */
    private void authorize(Long branchId, PermissionName permission) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryAutomationController.class, "authorize(Long,PermissionName)");
        try {
            authorizationService.requirePermission(permission);
            authorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryAutomationController.class,
                    "authorize(Long,PermissionName)");
        }
    }
}
