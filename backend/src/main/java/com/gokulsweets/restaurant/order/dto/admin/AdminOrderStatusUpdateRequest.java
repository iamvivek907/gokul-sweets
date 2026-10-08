package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

/**
 * Immutable admin order status update request data contract.
 *
 * @param status the status
 * @param pickupCode the pickup code
 */
public record AdminOrderStatusUpdateRequest(
        @NotNull(message = "Order status is required.") OrderStatus status, String pickupCode) {

    /**
     * Creates a admin order status update request instance.
     *
     * @param status the status
     */
    public AdminOrderStatusUpdateRequest(OrderStatus status) {
        this(status, null);
    }
}
