package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;

/** Immutable admin branch product response data contract. */
public record AdminBranchProductResponse(
        Long branchProductId,
        Long branchId,
        Long productId,
        String productCode,
        String productName,
        String productDescription,
        boolean productActive,
        Long categoryId,
        String categoryCode,
        String categoryName,
        boolean categoryActive,
        BigDecimal basePrice,
        BigDecimal priceOverride,
        BigDecimal effectivePrice,
        boolean available,
        Integer displayOrder) {

    /**
     * Froms the operation.
     *
     * @param branchProduct the branch product
     * @return the from result
     */
    public static AdminBranchProductResponse from(BranchProduct branchProduct) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(AdminBranchProductResponse.class, "from(BranchProduct)");
        try {
            BigDecimal effectivePrice =
                    branchProduct.getPriceOverride() != null
                            ? branchProduct.getPriceOverride()
                            : branchProduct.getProduct().getBasePrice();
            return new AdminBranchProductResponse(
                    branchProduct.getId(),
                    branchProduct.getBranch().getId(),
                    branchProduct.getProduct().getId(),
                    branchProduct.getProduct().getCode(),
                    branchProduct.getProduct().getName(),
                    branchProduct.getProduct().getDescription(),
                    branchProduct.getProduct().isActive(),
                    branchProduct.getProduct().getCategory().getId(),
                    branchProduct.getProduct().getCategory().getCode(),
                    branchProduct.getProduct().getCategory().getName(),
                    branchProduct.getProduct().getCategory().isActive(),
                    branchProduct.getProduct().getBasePrice(),
                    branchProduct.getPriceOverride(),
                    effectivePrice,
                    branchProduct.isAvailable(),
                    branchProduct.getDisplayOrder());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos,
                    AdminBranchProductResponse.class,
                    "from(BranchProduct)");
        }
    }
}
