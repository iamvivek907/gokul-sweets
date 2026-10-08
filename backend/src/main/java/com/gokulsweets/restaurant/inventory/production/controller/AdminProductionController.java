package com.gokulsweets.restaurant.inventory.production.controller;

import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
import com.gokulsweets.restaurant.inventory.production.dto.*;
import com.gokulsweets.restaurant.inventory.production.service.ProductionPlanningService;
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

/** HTTP endpoints for admin production operations. */
@RestController
@RequestMapping("/api/admin/inventory/branches/{branchId}/production")
@RequiredArgsConstructor
public class AdminProductionController {

    private final ProductionPlanningService productionPlanningService;

    private final StaffAuthorizationService authorizationService;

    private final AdminInventoryWorkspaceService inventoryWorkspaceService;

    /**
     * Returns plan.
     *
     * @param branchId the branch id
     * @param serviceDate the service date
     * @return the get plan result
     */
    @GetMapping
    public ResponseEntity<ProductionPlanResponse> getPlan(
            @PathVariable Long branchId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminProductionController.class, "getPlan(Long,LocalDate)");
        try {
            authorize(branchId, PermissionName.INVENTORY_VIEW);
            return ResponseEntity.ok(productionPlanningService.getPlan(branchId, serviceDate));
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "getPlan(Long,LocalDate)");
        }
    }

    /**
     * Records production.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param authentication the authentication
     * @return the record production result
     */
    @PostMapping("/{branchProductId}/{serviceDate}/produced")
    public ResponseEntity<ProductionPlanItemResponse> recordProduction(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @Valid @RequestBody RecordProductionRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminProductionController.class,
                        "recordProduction(Long,Long,LocalDate,RecordProductionRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            authorizeProduct(branchId, branchProductId);
            ProductionPlanItemResponse result =
                    productionPlanningService.recordProduction(
                            branchProductId, serviceDate, request, authentication.getName());
            return ResponseEntity.ok(result);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "recordProduction(Long,Long,LocalDate,RecordProductionRequest,Authentication)");
        }
    }

    /**
     * Records wastage.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param authentication the authentication
     * @return the record wastage result
     */
    @PostMapping("/{branchProductId}/{serviceDate}/wastage")
    public ResponseEntity<ProductionPlanItemResponse> recordWastage(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @Valid @RequestBody RecordWastageRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminProductionController.class,
                        "recordWastage(Long,Long,LocalDate,RecordWastageRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            authorizeProduct(branchId, branchProductId);
            ProductionPlanItemResponse result =
                    productionPlanningService.recordWastage(
                            branchProductId, serviceDate, request, authentication.getName());
            return ResponseEntity.ok(result);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "recordWastage(Long,Long,LocalDate,RecordWastageRequest,Authentication)");
        }
    }

    /**
     * Records adjustment.
     *
     * @param branchId the branch id
     * @param branchProductId the branch product id
     * @param serviceDate the service date
     * @param request the request
     * @param authentication the authentication
     * @return the record adjustment result
     */
    @PostMapping("/{branchProductId}/{serviceDate}/adjustment")
    public ResponseEntity<ProductionPlanItemResponse> recordAdjustment(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate serviceDate,
            @Valid @RequestBody RecordStockAdjustmentRequest request,
            Authentication authentication) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminProductionController.class,
                        "recordAdjustment(Long,Long,LocalDate,RecordStockAdjustmentRequest,Authentication)");
        try {
            authorize(branchId, PermissionName.INVENTORY_MANAGE);
            authorizeProduct(branchId, branchProductId);
            ProductionPlanItemResponse result =
                    productionPlanningService.recordAdjustment(
                            branchProductId, serviceDate, request, authentication.getName());
            return ResponseEntity.ok(result);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "recordAdjustment(Long,Long,LocalDate,RecordStockAdjustmentRequest,Authentication)");
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
                        AdminProductionController.class, "authorize(Long,PermissionName)");
        try {
            authorizationService.requirePermission(permission);
            authorizationService.requireBranchAccess(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "authorize(Long,PermissionName)");
        }
    }

    /**
     * Authorizes product.
     *
     * @param requestedBranchId the requested branch id
     * @param branchProductId the branch product id
     */
    private void authorizeProduct(Long requestedBranchId, Long branchProductId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminProductionController.class, "authorizeProduct(Long,Long)");
        try {
            Long actualBranchId =
                    inventoryWorkspaceService.getBranchIdForBranchProduct(branchProductId);
            if (!requestedBranchId.equals(actualBranchId)) {
                throw new InventoryConflictException(
                        "BRANCH_PRODUCT_MISMATCH",
                        "The selected product does not belong to this branch.");
            }
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminProductionController.class,
                    "authorizeProduct(Long,Long)");
        }
    }
}
