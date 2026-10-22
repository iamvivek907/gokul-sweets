package com.gokulsweets.restaurant.inventory.centre;

/** Transaction-local wake-up hint. PostgreSQL remains the durable cross-instance queue. */
public record InventoryWorkQueued() {}
