package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

/**
 * Immutable validated order item data contract.
 *
 * @param product the product
 * @param branchProduct the branch product
 * @param saleMode the sale mode
 * @param quantity the quantity
 * @param weightGrams the weight grams
 */
public record ValidatedOrderItem(
        Product product,
        BranchProduct branchProduct,
        ProductSaleMode saleMode,
        int quantity,
        Integer weightGrams,
        java.math.BigDecimal mealDiscount,
        String mealLabel,
        String mealDetails) {
    /** Preserves ordinary item validation without a meal allocation. */
    public ValidatedOrderItem(
            Product product,
            BranchProduct branchProduct,
            ProductSaleMode saleMode,
            int quantity,
            Integer weightGrams) {
        this(
                product,
                branchProduct,
                saleMode,
                quantity,
                weightGrams,
                java.math.BigDecimal.ZERO,
                null,
                null);
    }
}
