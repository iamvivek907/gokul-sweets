package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

record MenuImportRow(
        int excelRowNumber,

        String categoryCode,
        String categoryName,
        String categoryDescription,
        int categoryDisplayOrder,
        boolean categoryActive,

        String productCode,
        String productName,
        String productDescription,
        BigDecimal basePrice,
        boolean productActive,
        ProductSaleMode saleMode,
        Integer minimumWeightGrams,
        Integer weightStepGrams,
        String taxCode,

        BigDecimal branchPriceOverride,
        boolean branchAvailable,
        int branchDisplayOrder
) {
}
