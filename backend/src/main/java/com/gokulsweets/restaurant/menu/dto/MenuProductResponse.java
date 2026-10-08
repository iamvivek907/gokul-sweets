package com.gokulsweets.restaurant.menu.dto;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/** Immutable menu product response data contract. */
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
        Integer weightStepGrams,
        com.gokulsweets.restaurant.menu.MenuServiceWindows.Status serviceAvailability,
        boolean vegetarian) {

    public MenuProductResponse(
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
            Integer weightStepGrams) {
        this(
                id,
                categoryId,
                categoryName,
                name,
                description,
                price,
                imageUrl,
                available,
                saleMode,
                minimumWeightGrams,
                weightStepGrams,
                null,
                true);
    }
}
