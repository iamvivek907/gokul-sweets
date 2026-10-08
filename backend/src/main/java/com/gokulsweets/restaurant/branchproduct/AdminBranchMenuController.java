package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductResponse;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductUpdateRequest;
import com.gokulsweets.restaurant.observability.MethodTiming;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for admin branch menu operations. */
@RestController
@RequestMapping("/api/admin/branches/{branchId}/menu")
@RequiredArgsConstructor
public class AdminBranchMenuController {

    private final AdminBranchMenuService adminBranchMenuService;

    /**
     * Returns branch menu.
     *
     * @param branchId the branch id
     * @return the get branch menu result
     */
    @GetMapping
    public ResponseEntity<List<AdminBranchProductResponse>> getBranchMenu(
            @PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchMenuController.class, "getBranchMenu(Long)");
        try {
            List<AdminBranchProductResponse> menu = adminBranchMenuService.getBranchMenu(branchId);
            return ResponseEntity.ok(menu);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuController.class,
                    "getBranchMenu(Long)");
        }
    }

    /**
     * Updates branch product.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @param request the request
     * @return the update branch product result
     */
    @PatchMapping("/products/{productId}")
    public ResponseEntity<AdminBranchProductResponse> updateBranchProduct(
            @PathVariable Long branchId,
            @PathVariable Long productId,
            @Valid @RequestBody AdminBranchProductUpdateRequest request) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(
                        AdminBranchMenuController.class,
                        "updateBranchProduct(Long,Long,AdminBranchProductUpdateRequest)");
        try {
            AdminBranchProductResponse updated =
                    adminBranchMenuService.updateBranchProduct(branchId, productId, request);
            return ResponseEntity.ok(updated);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchMenuController.class,
                    "updateBranchProduct(Long,Long,AdminBranchProductUpdateRequest)");
        }
    }
}
