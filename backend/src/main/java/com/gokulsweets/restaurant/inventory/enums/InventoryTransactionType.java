package com.gokulsweets.restaurant.inventory.enums;

/** Defines the supported inventory transaction type values. */
public enum InventoryTransactionType {

    /** The allocation approved value. */
    ALLOCATION_APPROVED,
    /** The ready stock recorded value. */
    READY_STOCK_RECORDED,
    /** The temporary hold value. */
    TEMPORARY_HOLD,
    /** The hold released value. */
    HOLD_RELEASED,
    /** The commitment confirmed value. */
    COMMITMENT_CONFIRMED,
    /** The commitment cancelled value. */
    COMMITMENT_CANCELLED,
    /** The fulfilled value. */
    FULFILLED,
    /** The wastage value. */
    WASTAGE,
    /** The adjustment value. */
    ADJUSTMENT,
    /** The carry forward value. */
    CARRY_FORWARD
}
