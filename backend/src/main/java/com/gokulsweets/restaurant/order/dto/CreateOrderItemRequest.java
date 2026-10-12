package com.gokulsweets.restaurant.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Immutable create order item request data contract.
 *
 * @param productId the product id
 * @param quantity the quantity
 * @param weightGrams the weight grams
 */
public record CreateOrderItemRequest(
        @NotNull(message = "Product ID is required.") Long productId,
        @Min(value = 1, message = "Quantity must be at least 1.") Integer quantity,
        @Min(value = 250, message = "Weight must be at least 250 grams.") Integer weightGrams) {}
