package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Immutable admin bulk readiness request data contract.
 *
 * @param serviceDate the service date
 * @param items the items
 */
public record AdminBulkReadinessRequest(
        @NotNull(message = "Service date is required.") LocalDate serviceDate,
        @NotEmpty(message = "Select at least one allocation.")
                List<@Valid AdminBulkReadinessItemRequest> items) {}
