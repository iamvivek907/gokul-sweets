package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

public record OrderItemResponse(
        Long id,
        Long productId,
        String productName,
        ProductSaleMode saleMode,
        Integer quantity,
        Integer weightGrams,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {

    /*
     * Compatibility constructor for unrelated callers while they are
     * migrated. Customer and admin order queries in this delivery use
     * the complete constructor above.
     */
    public OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal
    ) {
        this(
                id,
                productId,
                productName,
                ProductSaleMode.UNIT,
                quantity,
                null,
                unitPrice,
                taxRate,
                taxAmount,
                lineTotal
        );
    }
}
