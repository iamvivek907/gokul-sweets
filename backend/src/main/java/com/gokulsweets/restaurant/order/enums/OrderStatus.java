package com.gokulsweets.restaurant.order.enums;

/** Defines the supported order status values. */
public enum OrderStatus {
    PENDING_PAYMENT,
    CONFIRMED,
    PREPARING,
    READY_FOR_PICKUP,
    PICKED_UP,
    READY_FOR_DELIVERY,
    OUT_FOR_DELIVERY,
    DELIVERED,
    PAYMENT_FAILED,
    CANCELLED,
    NO_SHOW,
    PICKUP_WINDOW_EXPIRED
}
