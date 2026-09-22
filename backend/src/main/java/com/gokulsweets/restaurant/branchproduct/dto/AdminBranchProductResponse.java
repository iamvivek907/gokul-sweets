package com.gokulsweets.restaurant.branchproduct.dto;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;

import java.math.BigDecimal;

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

        Integer displayOrder

) {

    public static AdminBranchProductResponse from(
            BranchProduct branchProduct
    ) {

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

                branchProduct
                        .getProduct()
                        .getCategory()
                        .getId(),

                branchProduct
                        .getProduct()
                        .getCategory()
                        .getCode(),

                branchProduct
                        .getProduct()
                        .getCategory()
                        .getName(),

                branchProduct
                        .getProduct()
                        .getCategory()
                        .isActive(),

                branchProduct
                        .getProduct()
                        .getBasePrice(),

                branchProduct
                        .getPriceOverride(),

                effectivePrice,

                branchProduct
                        .isAvailable(),

                branchProduct
                        .getDisplayOrder()
        );
    }
}