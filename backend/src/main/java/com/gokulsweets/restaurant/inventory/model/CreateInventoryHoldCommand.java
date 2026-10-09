package com.gokulsweets.restaurant.inventory.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable create inventory hold command data contract.
 *
 * @param reservationKey the reservation key
 * @param orderNumber the order number
 * @param branchProductId the branch product id
 * @param serviceDate the service date
 * @param quantity the quantity
 */
public record CreateInventoryHoldCommand(
        String reservationKey,
        String orderNumber,
        Long branchProductId,
        LocalDate serviceDate,
        BigDecimal quantity) {}
