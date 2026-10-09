package com.gokulsweets.restaurant.inventory.automation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Immutable availability window request data contract.
 *
 * @param name the name
 * @param startDate the start date
 * @param endDate the end date
 * @param active the active
 */
public record AvailabilityWindowRequest(
        @NotBlank @Size(max = 120) String name,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        boolean active) {}
