package com.gokulsweets.restaurant.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Immutable create inventory hold command data contract. */
public record CreateInventoryHoldCommand(
        String reservationKey,
        String orderNumber,
        Long branchProductId,
        LocalDate serviceDate,
        BigDecimal quantity) {}
