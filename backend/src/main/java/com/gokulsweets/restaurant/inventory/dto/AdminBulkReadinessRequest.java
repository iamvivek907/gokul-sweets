package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record AdminBulkReadinessRequest(
        @NotNull(message = "Service date is required.")
        LocalDate serviceDate,

        @NotEmpty(message = "Select at least one allocation.")
        List<@Valid AdminBulkReadinessItemRequest> items
) {
}
