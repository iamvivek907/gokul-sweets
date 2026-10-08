package com.gokulsweets.restaurant.kot.dto;

/** Immutable admin kot item response data contract. */
public record AdminKotItemResponse(
        Long id,
        Long productId,
        String productName,
        Integer quantity,
        Integer displayOrder,
        String saleMode,
        Integer weightGrams) {}
