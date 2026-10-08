package com.gokulsweets.restaurant.inventory.automation.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Immutable automation generation request data contract.
 *
 * @param fromDate the from date
 * @param throughDate the through date
 */
public record AutomationGenerationRequest(
        @NotNull LocalDate fromDate, @NotNull LocalDate throughDate) {}
