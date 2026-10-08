package com.gokulsweets.restaurant.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

/** Immutable customer inventory check request data contract. */
public record CustomerInventoryCheckRequest(
        @NotNull LocalDate serviceDate, @NotEmpty List<@Valid Item> items) {

    /** Immutable item data contract. */
    public record Item(
            @NotNull Long productId, @Min(1) Integer quantity, @Min(1) Integer weightGrams) {}
}
