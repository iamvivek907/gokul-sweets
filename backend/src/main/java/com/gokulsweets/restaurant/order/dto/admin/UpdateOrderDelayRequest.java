package com.gokulsweets.restaurant.order.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/** Immutable update order delay request data contract. */
public record UpdateOrderDelayRequest(
        @NotNull LocalDateTime estimatedReadyAt,
        @NotBlank @Size(min = 10, max = 300) String reason) {}
