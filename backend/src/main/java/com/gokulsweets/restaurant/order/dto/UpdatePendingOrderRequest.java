package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Immutable update pending order request data contract.
 *
 * @param pickupSlotId the pickup slot id
 * @param pickupType the pickup type
 * @param items the items
 * @param quoteToken the quote token
 */
public record UpdatePendingOrderRequest(
        @NotNull(message = "Pickup slot ID is required.") Long pickupSlotId,
        @NotNull(message = "Pickup type is required.") PickupType pickupType,
        @NotEmpty(message = "At least one order item is required.")
                List<@Valid CreateOrderItemRequest> items,
        String quoteToken) {

    /**
     * Creates a update pending order request instance.
     *
     * @param pickupSlotId the pickup slot id
     * @param pickupType the pickup type
     * @param items the items
     */
    public UpdatePendingOrderRequest(
            Long pickupSlotId, PickupType pickupType, List<CreateOrderItemRequest> items) {
        this(pickupSlotId, pickupType, items, null);
    }
}
