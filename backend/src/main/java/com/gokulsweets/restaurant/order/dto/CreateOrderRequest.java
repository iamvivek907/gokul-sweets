package com.gokulsweets.restaurant.order.dto;

import com.gokulsweets.restaurant.order.enums.PickupType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderRequest(

        @NotNull(message = "Branch ID is required.")
        Long branchId,

        @NotNull(message = "Pickup slot ID is required.")
        Long pickupSlotId,

        @NotBlank(message = "Customer name is required.")
        @Size(
                min = 2,
                max = 150,
                message = "Customer name must be between 2 and 150 characters."
        )
        String customerName,

        @NotBlank(message = "Customer phone is required.")
        @Pattern(
                regexp = "^[6-9][0-9]{9}$",
                message = "Please enter a valid 10-digit Indian mobile number."
        )
        String customerPhone,

        @NotNull(message = "Pickup type is required.")
        PickupType pickupType,

        @NotEmpty(message = "At least one order item is required.")
        List<@Valid CreateOrderItemRequest> items,
        String quoteToken
) {
    public CreateOrderRequest(Long branchId, Long pickupSlotId, String customerName,
                              String customerPhone, PickupType pickupType, List<CreateOrderItemRequest> items) {
        this(branchId, pickupSlotId, customerName, customerPhone, pickupType, items, null);
    }
}
