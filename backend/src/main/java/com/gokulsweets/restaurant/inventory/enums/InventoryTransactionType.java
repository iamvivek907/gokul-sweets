package com.gokulsweets.restaurant.inventory.enums;

/** Defines the supported inventory transaction type values. */
public enum InventoryTransactionType {
    ALLOCATION_APPROVED,
    READY_STOCK_RECORDED,
    TEMPORARY_HOLD,
    HOLD_RELEASED,
    COMMITMENT_CONFIRMED,
    COMMITMENT_CANCELLED,
    FULFILLED,
    WASTAGE,
    ADJUSTMENT,
    CARRY_FORWARD
}
