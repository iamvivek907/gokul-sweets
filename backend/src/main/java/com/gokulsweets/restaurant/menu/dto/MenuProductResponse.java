package com.gokulsweets.restaurant.menu.dto;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

public record MenuProductResponse(

        Long id,

        Long categoryId,

        String categoryName,

        String name,

        String description,

        BigDecimal price,

        String imageUrl,

        boolean available,

        ProductSaleMode saleMode,

        Integer minimumWeightGrams,

        Integer weightStepGrams
) {
}