package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record AdminCompleteInventoryRequest(
        @NotNull(message = "Service date is required.")
        LocalDate serviceDate,

        @NotEmpty(message = "Select at least one inventory item.")
        List<@Valid AdminCompleteInventoryItemRequest> items
) {
}
