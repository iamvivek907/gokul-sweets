package com.gokulsweets.restaurant.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CreateInventoryHoldCommand(
        String reservationKey,
        String orderNumber,
        Long branchProductId,
        LocalDate serviceDate,
        BigDecimal quantity
) {
}

