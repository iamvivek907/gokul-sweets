package com.gokulsweets.restaurant.inventory.production.controller;

import com.gokulsweets.restaurant.inventory.production.dto.*;
import com.gokulsweets.restaurant.inventory.production.service.ProductionPlanningService;
import com.gokulsweets.restaurant.inventory.service.AdminInventoryWorkspaceService;
import com.gokulsweets.restaurant.inventory.exception.InventoryConflictException;
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
@RequestMapping("/api/admin/inventory/branches/{branchId}/production")
@RequiredArgsConstructor
public class AdminProductionController {

    private final ProductionPlanningService productionPlanningService;
    private final StaffAuthorizationService authorizationService;
    private final AdminInventoryWorkspaceService inventoryWorkspaceService;

    @GetMapping
    public ResponseEntity<ProductionPlanResponse> getPlan(
            @PathVariable Long branchId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate
    ) {
        authorize(branchId, PermissionName.INVENTORY_VIEW);
        return ResponseEntity.ok(
                productionPlanningService.getPlan(branchId, serviceDate)
        );
    }

    @PostMapping("/{branchProductId}/{serviceDate}/produced")
    public ResponseEntity<ProductionPlanItemResponse> recordProduction(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @Valid @RequestBody RecordProductionRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        authorizeProduct(branchId, branchProductId);
        ProductionPlanItemResponse result = productionPlanningService.recordProduction(
                branchProductId,
                serviceDate,
                request,
                authentication.getName()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{branchProductId}/{serviceDate}/wastage")
    public ResponseEntity<ProductionPlanItemResponse> recordWastage(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @Valid @RequestBody RecordWastageRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        authorizeProduct(branchId, branchProductId);
        ProductionPlanItemResponse result = productionPlanningService.recordWastage(
                branchProductId,
                serviceDate,
                request,
                authentication.getName()
        );
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{branchProductId}/{serviceDate}/adjustment")
    public ResponseEntity<ProductionPlanItemResponse> recordAdjustment(
            @PathVariable Long branchId,
            @PathVariable Long branchProductId,
            @PathVariable
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate serviceDate,
            @Valid @RequestBody RecordStockAdjustmentRequest request,
            Authentication authentication
    ) {
        authorize(branchId, PermissionName.INVENTORY_MANAGE);
        authorizeProduct(branchId, branchProductId);
        ProductionPlanItemResponse result = productionPlanningService.recordAdjustment(
                branchProductId,
                serviceDate,
                request,
                authentication.getName()
        );
        return ResponseEntity.ok(result);
    }

    private void authorize(Long branchId, PermissionName permission) {
        authorizationService.requirePermission(permission);
        authorizationService.requireBranchAccess(branchId);
    }

    private void authorizeProduct(Long requestedBranchId, Long branchProductId) {
        Long actualBranchId = inventoryWorkspaceService
                .getBranchIdForBranchProduct(branchProductId);
        if (!requestedBranchId.equals(actualBranchId)) {
            throw new InventoryConflictException(
                    "BRANCH_PRODUCT_MISMATCH",
                    "The selected product does not belong to this branch."
            );
        }
    }
}
