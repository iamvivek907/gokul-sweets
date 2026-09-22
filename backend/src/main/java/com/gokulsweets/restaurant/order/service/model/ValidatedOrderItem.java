package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.branchproduct.BranchProduct;
import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

public record ValidatedOrderItem(
        Product product,
        BranchProduct branchProduct,
        ProductSaleMode saleMode,
        int quantity,
        Integer weightGrams
) {
}
