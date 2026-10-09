package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;

/**
 * Immutable branch product response data contract.
 *
 * @param id the id
 * @param branchId the branch id
 * @param productId the product id
 * @param productName the product name
 * @param productDescription the product description
 * @param basePrice the base price
 * @param priceOverride the price override
 * @param sellingPrice the selling price
 * @param available the available
 * @param displayOrder the display order
 */
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
     * Maps the supplied data into a {@code BranchProductResponse} representation.
     *
     * @param branchProduct the branch product supplied to this method
     * @return the {@code BranchProductResponse} result
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
