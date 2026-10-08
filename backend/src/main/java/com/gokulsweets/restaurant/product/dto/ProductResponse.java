package com.gokulsweets.restaurant.product.dto;

import com.gokulsweets.restaurant.observability.MethodTiming;
import com.gokulsweets.restaurant.product.Product;

import java.math.BigDecimal;

/**
 * Immutable product response data contract.
 *
 * @param id the id
 * @param categoryId the category id
 * @param categoryName the category name
 * @param taxCategoryId the tax category id
 * @param taxCategoryName the tax category name
 * @param name the name
 * @param description the description
 * @param basePrice the base price
 * @param active the active
 * @param imageUrl the image url
 * @param vegetarian the vegetarian
 */
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
        String imageUrl,
        boolean vegetarian) {

    /**
     * Froms the operation.
     *
     * @param product the product
     * @return the from result
     */
    public static ProductResponse from(Product product) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(ProductResponse.class, "from(Product)");
        try {
            return new ProductResponse(
                    product.getId(),
                    product.getCategory().getId(),
                    product.getCategory().getName(),
                    product.getTaxCategory() != null ? product.getTaxCategory().getId() : null,
                    product.getTaxCategory() != null ? product.getTaxCategory().getName() : null,
                    product.getName(),
                    product.getDescription(),
                    product.getBasePrice(),
                    product.isActive(),
                    product.getImageUrl(),
                    product.isVegetarian());
        } finally {
            MethodTiming.finish(__gokulMethodStartedNanos, ProductResponse.class, "from(Product)");
        }
    }
}
