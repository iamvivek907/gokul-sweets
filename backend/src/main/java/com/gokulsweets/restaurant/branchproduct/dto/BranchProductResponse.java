package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;

import java.math.BigDecimal;

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
        Integer displayOrder
) {

    public static BranchProductResponse from(
            BranchProduct branchProduct
    ) {

        BigDecimal basePrice =
                branchProduct.getProduct().getBasePrice();

        BigDecimal priceOverride =
                branchProduct.getPriceOverride();

        BigDecimal sellingPrice =
                priceOverride != null
                        ? priceOverride
                        : basePrice;

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
                branchProduct.getDisplayOrder()
        );
    }
}