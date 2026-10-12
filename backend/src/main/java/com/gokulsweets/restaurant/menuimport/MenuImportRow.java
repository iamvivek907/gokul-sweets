package com.gokulsweets.restaurant.menuimport;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/**
 * Immutable menu import row data contract.
 *
 * @param excelRowNumber the excel row number
 * @param categoryCode the category code
 * @param categoryName the category name
 * @param categoryDescription the category description
 * @param categoryDisplayOrder the category display order
 * @param categoryActive the category active
 * @param productCode the product code
 * @param productName the product name
 * @param productDescription the product description
 * @param basePrice the base price
 * @param productActive the product active
 * @param saleMode the sale mode
 * @param minimumWeightGrams the minimum weight grams
 * @param weightStepGrams the weight step grams
 * @param taxCode the tax code
 * @param branchPriceOverride the branch price override
 * @param branchAvailable the branch available
 * @param branchDisplayOrder the branch display order
 */
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
        int branchDisplayOrder) {}
