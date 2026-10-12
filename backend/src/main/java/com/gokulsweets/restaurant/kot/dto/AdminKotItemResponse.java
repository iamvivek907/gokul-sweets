package com.gokulsweets.restaurant.kot.dto;

/**
 * Immutable admin kot item response data contract.
 *
 * @param id the id
 * @param productId the product id
 * @param productName the product name
 * @param quantity the quantity
 * @param displayOrder the display order
 * @param saleMode the sale mode
 * @param weightGrams the weight grams
 */
public record AdminKotItemResponse(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        Integer displayOrder,
        String saleMode,
        Integer weightGrams) {}
