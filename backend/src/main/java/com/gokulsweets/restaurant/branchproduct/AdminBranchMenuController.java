package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductResponse;
import com.gokulsweets.restaurant.branchproduct.dto.AdminBranchProductUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(
        "/api/admin/branches/{branchId}/menu"
)
@RequiredArgsConstructor
public class AdminBranchMenuController {

    private final AdminBranchMenuService
            adminBranchMenuService;


    /*
     * =========================================================
     * GET LIVE ADMIN MENU
     * =========================================================
     *
     * Example:
     *
     * GET /api/admin/branches/1/menu
     */
    @GetMapping
    public ResponseEntity<
            List<AdminBranchProductResponse>
            > getBranchMenu(
            @PathVariable
            Long branchId
    ) {

        List<AdminBranchProductResponse> menu =
                adminBranchMenuService
                        .getBranchMenu(
                                branchId
                        );


        return ResponseEntity
                .ok(
                        menu
                );
    }


    /*
     * =========================================================
     * UPDATE ONE BRANCH MENU ITEM
     * =========================================================
     *
     * Example:
     *
     * PATCH
     * /api/admin/branches/1/menu/products/4
     *
     * {
     *     "available": false
     * }
     *
     * or
     *
     * {
     *     "priceOverride": 399.00
     * }
     *
     * or
     *
     * {
     *     "clearPriceOverride": true
     * }
     *
     * or
     *
     * {
     *     "displayOrder": 2
     * }
     */
    @PatchMapping(
            "/products/{productId}"
    )
    public ResponseEntity<
            AdminBranchProductResponse
            > updateBranchProduct(
            @PathVariable
            Long branchId,

            @PathVariable
            Long productId,

            @Valid
            @RequestBody
            AdminBranchProductUpdateRequest request
    ) {

        AdminBranchProductResponse updated =
                adminBranchMenuService
                        .updateBranchProduct(
                                branchId,
                                productId,
                                request
                        );


        return ResponseEntity
                .ok(
                        updated
                );
    }
}