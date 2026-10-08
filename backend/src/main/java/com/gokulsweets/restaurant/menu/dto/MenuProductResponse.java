package com.gokulsweets.restaurant.menu.dto;

import com.gokulsweets.restaurant.product.ProductSaleMode;

import java.math.BigDecimal;

/**
 * Immutable menu product response data contract.
 *
 * @param id the id
 * @param categoryId the category id
 * @param categoryName the category name
 * @param name the name
 * @param description the description
 * @param price the price
 * @param imageUrl the image url
 * @param available the available
 * @param saleMode the sale mode
 * @param minimumWeightGrams the minimum weight grams
 * @param weightStepGrams the weight step grams
 * @param serviceAvailability the service availability
 * @param vegetarian the vegetarian
 */
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

    /**
     * Creates a menu product response instance.
     *
     * @param id the id
     * @param categoryId the category id
     * @param categoryName the category name
     * @param name the name
     * @param description the description
     * @param price the price
     * @param imageUrl the image url
     * @param available the available
     * @param saleMode the sale mode
     * @param minimumWeightGrams the minimum weight grams
     * @param weightStepGrams the weight step grams
     */
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
