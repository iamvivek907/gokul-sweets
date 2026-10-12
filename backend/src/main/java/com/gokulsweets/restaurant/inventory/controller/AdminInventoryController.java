package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.*;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryService;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryWorkspaceService;
import com.gokulsweets.restaurant.observability.MethodTiming;
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

/** HTTP endpoints for admin inventory operations. */
@RestController
@RequestMapping("/api/admin/inventory")
@RequiredArgsConstructor
public class AdminInventoryController {

    private final AdminInventoryService adminInventoryService;

    private final AdminInventoryWorkspaceService workspaceService;

    private final StaffAuthorizationService authorizationService;

    /**
     * Upserts policy.
     *
     * @param branchProductId the branch product id
     * @param request the request
     * @return the upsert policy result
     */
    @PutMapping("/policies/{branchProductId}")
    public ResponseEntity<InventoryPolicyResponse> upsertPolicy(
            @PathVariable Long branchProductId,
            @Valid @RequestBody AdminInventoryPolicyRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class,
                        "upsertPolicy(Long,AdminInventoryPolicyRequest)");
        try {
            authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(adminInventoryService.upsertPolicy(branchProductId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "upsertPolicy(Long,AdminInventoryPolicyRequest)");
        }
    }

    /**
     * Approves allocation.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param authentication the authentication
     * @return the approve allocation result
     */
    @PutMapping("/allocations/{branchProductId}/{serviceDate}")
    public ResponseEntity<InventoryAllocationResponse> approveAllocation(
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @Valid @RequestBody AdminAllocationApprovalRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class,
                        "approveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,Authentication)");
        try {
            authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    adminInventoryService.approveAllocation(
                            branchProductId, serviceDate, request, authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "approveAllocation(Long,LocalDate,AdminAllocationApprovalRequest,Authentication)");
        }
    }

    /**
     * Updates readiness.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param authentication the authentication
     * @return the update readiness result
     */
    @PatchMapping("/allocations/{branchProductId}/{serviceDate}/readiness")
    public ResponseEntity<InventoryAllocationResponse> updateReadiness(
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @Valid @RequestBody AdminReadinessUpdateRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class,
                        "updateReadiness(Long,LocalDate,AdminReadinessUpdateRequest,Authentication)");
        try {
            authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    adminInventoryService.updateReadiness(
                            branchProductId, serviceDate, request, authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "updateReadiness(Long,LocalDate,AdminReadinessUpdateRequest,Authentication)");
        }
    }

    /**
     * Returns allocations.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @return the get allocations result
     */
    @GetMapping("/allocations")
    public ResponseEntity<List<InventoryAllocationResponse>> getAllocations(
            @RequestParam Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class, "getAllocations(Long,LocalDate)");
        try {
            authorizationService.requirePermission(PermissionName.INVENTORY_VIEW);
            authorizationService.requireBranchAccess(branchId);
            return ResponseEntity.ok(adminInventoryService.getAllocations(branchId, serviceDate));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "getAllocations(Long,LocalDate)");
        }
    }

    /**
     * Returns plan history.
     *
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @return the get plan history result
     */
    @GetMapping("/allocations/{branchProductId}/{serviceDate}/history")
    public ResponseEntity<List<AdminInventoryService.PlanChange>> getPlanHistory(
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class, "getPlanHistory(Long,LocalDate)");
        try {
            authorizeBranchProduct(branchProductId, PermissionName.INVENTORY_VIEW);
            return ResponseEntity.ok(
                    adminInventoryService.getPlanHistory(branchProductId, serviceDate));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "getPlanHistory(Long,LocalDate)");
        }
    }

    /**
     * Authorizes branch product.
     *
     * @param branchProductId the branch product id
     * @param permission the permission
     */
    private void authorizeBranchProduct(Long branchProductId, PermissionName permission) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryController.class,
                        "authorizeBranchProduct(Long,PermissionName)");
        try {
            authorizationService.requirePermission(permission);
            authorizationService.requireBranchAccess(
                    workspaceService.getBranchIdForBranchProduct(branchProductId));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryController.class,
                    "authorizeBranchProduct(Long,PermissionName)");
        }
    }
}
