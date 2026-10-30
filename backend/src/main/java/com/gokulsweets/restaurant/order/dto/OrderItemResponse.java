package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/**
 * Immutable order item response data contract.
 *
 * @param id the id
 * @param productId the product id
 * @param productName the product name
 * @param saleMode the sale mode
 * @param quantity the quantity
 * @param weightGrams the weight grams
 * @param unitPrice the unit price
 * @param taxRate the tax rate
 * @param taxAmount the tax amount
 * @param lineTotal the line total
 */
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
        BigDecimal lineTotal,
        BigDecimal mealDiscount,
        String mealLabel) {

    /** Preserves ordinary item response construction. */
    public OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            ProductSaleMode saleMode,
            Integer quantity,
            Integer weightGrams,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
        this(
                id,
                productId,
                productName,
                saleMode,
                quantity,
                weightGrams,
                unitPrice,
                taxRate,
                taxAmount,
                lineTotal,
                BigDecimal.ZERO,
                null);
    }

    /**
     * Creates a order item response instance.
     *
     * @param id the id
     * @param productId the product id
     * @param productName the product name
     * @param quantity the quantity
     * @param unitPrice the unit price
     * @param taxRate the tax rate
     * @param taxAmount the tax amount
     * @param lineTotal the line total
     */
    public OrderItemResponse(
            Long id,
            Long productId,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal taxRate,
            BigDecimal taxAmount,
            BigDecimal lineTotal) {
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
                lineTotal);
    }
}
