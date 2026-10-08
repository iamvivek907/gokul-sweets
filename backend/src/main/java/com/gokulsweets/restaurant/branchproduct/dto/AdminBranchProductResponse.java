package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.observability.MethodTiming;

import java.math.BigDecimal;

/**
 * Immutable admin branch product response data contract.
 *
 * @param branchProductId the branch product id
 * @param branchId the branch id
 * @param productId the product id
 * @param productCode the product code
 * @param productName the product name
 * @param productDescription the product description
 * @param productActive the product active
 * @param categoryId the category id
 * @param categoryCode the category code
 * @param categoryName the category name
 * @param categoryActive the category active
 * @param basePrice the base price
 * @param priceOverride the price override
 * @param effectivePrice the effective price
 * @param available the available
 * @param displayOrder the display order
 */
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
