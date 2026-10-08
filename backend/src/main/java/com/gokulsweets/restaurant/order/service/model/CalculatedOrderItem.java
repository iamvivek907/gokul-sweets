package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/**
 * Immutable calculated order item data contract.
 *
 * @param product the product
 * @param saleMode the sale mode
 * @param quantity the quantity
 * @param weightGrams the weight grams
 * @param unitPrice the unit price
 * @param taxRate the tax rate
 * @param taxAmount the tax amount
 * @param lineTotal the line total
 */
public record CalculatedOrderItem(
        Product product,
        ProductSaleMode saleMode,
        int quantity,
        Integer weightGrams,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {}
