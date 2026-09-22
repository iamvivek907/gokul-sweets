package com.gokulsweets.restaurant.order.dto.admin;

import com.gokulsweets.restaurant.order.enums.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record AdminOrderStatusUpdateRequest(

        @NotNull(
                message = "Order status is required."
        )
        OrderStatus status
) {
}