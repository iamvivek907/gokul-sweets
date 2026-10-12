package com.gokulsweets.restaurant.inventory.controller;

import com.gokulsweets.restaurant.inventory.dto.*;
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

/** HTTP endpoints for admin inventory workspace operations. */
@RestController
@RequestMapping("/api/admin/inventory/branches/{branchId}")
@RequiredArgsConstructor
public class AdminInventoryWorkspaceController {

    private final AdminInventoryWorkspaceService workspaceService;

    private final StaffAuthorizationService authorizationService;

    /**
     * Returns catalogue.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @param search the search
     * @param categoryId the category id
     * @param filter the filter
     * @param page the page
     * @param size the size
     * @return the get catalogue result
     */
    @GetMapping("/catalogue")
    public ResponseEntity<InventoryCataloguePageResponse> getCatalogue(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "ALL") InventoryCatalogueFilter filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class,
                        "getCatalogue(Long,LocalDate,String,Long,InventoryCatalogueFilter,int,int)");
        try {
            authorize(branchId, PermissionName.INVENTORY_VIEW);
            return ResponseEntity.ok(
                    workspaceService.getCatalogue(
                            branchId, serviceDate, search, categoryId, filter, page, size));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "getCatalogue(Long,LocalDate,String,Long,InventoryCatalogueFilter,int,int)");
        }
    }

    /**
     * Updates policies.
     *
     * @param branchId the branch id
     * @param request the request
     * @return the update policies result
     */
    @PutMapping("/policies/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryPolicyResponse>> updatePolicies(
            @PathVariable Long branchId, @Valid @RequestBody AdminBulkPolicyRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class,
                        "updatePolicies(Long,AdminBulkPolicyRequest)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(workspaceService.updatePolicies(branchId, request));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "updatePolicies(Long,AdminBulkPolicyRequest)");
        }
    }

    /**
     * Approves allocations.
     *
     * @param branchId the branch id
     * @param request the request
     * @param authentication the authentication
     * @return the approve allocations result
     */
    @PutMapping("/allocations/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>>
            approveAllocations(
                    @PathVariable Long branchId,
                    @Valid @RequestBody AdminBulkAllocationRequest request,
                    Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class,
                        "approveAllocations(Long,AdminBulkAllocationRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    workspaceService.approveAllocations(
                            branchId, request, authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "approveAllocations(Long,AdminBulkAllocationRequest,Authentication)");
        }
    }

    /**
     * Updates readiness.
     *
     * @param branchId the branch id
     * @param request the request
     * @param authentication the authentication
     * @return the update readiness result
     */
    @PatchMapping("/readiness/bulk")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>> updateReadiness(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminBulkReadinessRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class,
                        "updateReadiness(Long,AdminBulkReadinessRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    workspaceService.updateReadiness(branchId, request, authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "updateReadiness(Long,AdminBulkReadinessRequest,Authentication)");
        }
    }

    /**
     * Completes setup.
     *
     * @param branchId the branch id
     * @param request the request
     * @param authentication the authentication
     * @return the complete setup result
     */
    @PutMapping("/setup/complete")
    public ResponseEntity<AdminBulkInventoryResponse<InventoryAllocationResponse>> completeSetup(
            @PathVariable Long branchId,
            @Valid @RequestBody AdminCompleteInventoryRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class,
                        "completeSetup(Long,AdminCompleteInventoryRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            return ResponseEntity.ok(
                    workspaceService.completeSetup(branchId, request, authentication.getName()));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "completeSetup(Long,AdminCompleteInventoryRequest,Authentication)");
        }
    }

    /**
     * Checks authorization for admin inventory workspace data.
     *
     * <p>Authorization checks include {@code permission}.
     *
     * <p>Delegates to {@code authorizationService.requirePermission(...)}, {@code
     * authorizationService.requireBranchAccess(...)}.
     *
     * @param branchId the branch id supplied to this method
     * @param permission the permission supplied to this method
     */
    private void authorize(Long branchId, PermissionName permission) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminInventoryWorkspaceController.class, "authorize(Long,PermissionName)");
        try {
            authorizationService.requirePermission(permission);
            authorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminInventoryWorkspaceController.class,
                    "authorize(Long,PermissionName)");
        }
    }
}
