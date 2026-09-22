package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdatePendingOrderRequest(

        @NotNull(message = "Pickup slot ID is required.")
        Long pickupSlotId,

        @NotNull(message = "Pickup type is required.")
        PickupType pickupType,

        @NotEmpty(message = "At least one order item is required.")
        List<@Valid CreateOrderItemRequest> items

) {
}
