package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;

/** Immutable branch product response data contract. */
public record BranchProductResponse(
        Long id,
        Long branchId,
        Long productId,
        String productName,
        String productDescription,
        BigDecimal basePrice,
        BigDecimal priceOverride,
        BigDecimal sellingPrice,
        boolean available,
        Integer displayOrder) {

    /**
     * Froms the operation.
     *
     * @param branchProduct the branch product
     * @return the from result
     */
    public static BranchProductResponse from(BranchProduct branchProduct) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(BranchProductResponse.class, "from(BranchProduct)");
        try {
            BigDecimal basePrice = branchProduct.getProduct().getBasePrice();
            BigDecimal priceOverride = branchProduct.getPriceOverride();
            BigDecimal sellingPrice = priceOverride != null ? priceOverride : basePrice;
            return new BranchProductResponse(
                    branchProduct.getId(),
                    branchProduct.getBranch().getId(),
                    branchProduct.getProduct().getId(),
                    branchProduct.getProduct().getName(),
                    branchProduct.getProduct().getDescription(),
                    basePrice,
                    priceOverride,
                    sellingPrice,
                    branchProduct.isAvailable(),
                    branchProduct.getDisplayOrder());
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, BranchProductResponse.class, "from(BranchProduct)");
        }
    }
}
