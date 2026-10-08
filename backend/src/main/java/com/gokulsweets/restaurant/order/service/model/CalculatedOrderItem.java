package com.gokulsweets.restaurant.order.service.model;

import com.gokulsweets.restaurant.product.Product;
import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/** Immutable calculated order item data contract. */
public record CalculatedOrderItem(
        Product product,
        ProductSaleMode saleMode,
        int quantity,
        Integer weightGrams,
        BigDecimal unitPrice,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal) {}
