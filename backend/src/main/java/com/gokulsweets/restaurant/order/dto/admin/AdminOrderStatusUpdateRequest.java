package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.OrderStatus;

import jakarta.validation.constraints.NotNull;

/** Immutable admin order status update request data contract. */
public record AdminOrderStatusUpdateRequest(
        @NotNull(message = "Order status is required.") OrderStatus status, String pickupCode) {

    public AdminOrderStatusUpdateRequest(OrderStatus status) {
        this(status, null);
    }
}
