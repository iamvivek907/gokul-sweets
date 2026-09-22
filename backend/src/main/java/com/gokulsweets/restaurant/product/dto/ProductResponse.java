package com.gokulsweets.restaurant.product.dto;

import com.gokulsweets.restaurant.product.Product;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        Long taxCategoryId,
        String taxCategoryName,
        String name,
        String description,
        BigDecimal basePrice,
        boolean active,
        String imageUrl
) {

    public static ProductResponse from(
            Product product
    ) {

        return new ProductResponse(
                product.getId(),

                product.getCategory().getId(),

                product.getCategory().getName(),

                product.getTaxCategory() != null
                        ? product.getTaxCategory().getId()
                        : null,

                product.getTaxCategory() != null
                        ? product.getTaxCategory().getName()
                        : null,

                product.getName(),

                product.getDescription(),

                product.getBasePrice(),

                product.isActive(),

                product.getImageUrl()
        );
    }
}