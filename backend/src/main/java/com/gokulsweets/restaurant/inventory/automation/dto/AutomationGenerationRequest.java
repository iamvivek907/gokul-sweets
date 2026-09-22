package com.gokulsweets.restaurant.inventory.automation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record AutomationGenerationRequest(
        @NotNull LocalDate fromDate,
        @NotNull LocalDate throughDate
) {
}
