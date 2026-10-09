package com.gokulsweets.restaurant.branchproduct;

import com.gokulsweets.restaurant.branchproduct.dto.BranchProductResponse;
import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.web.bind.annotation.*;

import java.util.List;

/** HTTP endpoints for branch product operations. */
@RestController
@RequestMapping("/api/branches/{branchId}/products")
public class BranchProductController {

    private final BranchProductService branchProductService;

    /**
     * Creates a branch product controller instance.
     *
     * @param branchProductService the branch product service
     */
    public BranchProductController(BranchProductService branchProductService) {
        this.branchProductService = branchProductService;
    }

    /**
     * Returns available products.
     *
     * @param branchId the branch id
     * @return the get available products result
     */
    @GetMapping
    public List<BranchProductResponse> getAvailableProducts(@PathVariable Long branchId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchProductController.class, "getAvailableProducts(Long)");
        try {
            return branchProductService.getAvailableProducts(branchId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchProductController.class,
                    "getAvailableProducts(Long)");
        }
    }

    /**
     * Returns branch product.
     *
     * @param branchId the branch id
     * @param productId the product id
     * @return the get branch product result
     */
    @GetMapping("/{productId}")
    public BranchProductResponse getBranchProduct(
            @PathVariable Long branchId, @PathVariable Long productId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchProductController.class, "getBranchProduct(Long,Long)");
        try {
            return branchProductService.getBranchProduct(branchId, productId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    BranchProductController.class,
                    "getBranchProduct(Long,Long)");
        }
    }
}
