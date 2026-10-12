package com.gokulsweets.restaurant.order.enums;

/** Defines the supported order status values. */
public enum OrderStatus {

    /** The pending payment value. */
    PENDING_PAYMENT,
    /** The confirmed value. */
    CONFIRMED,
    /** The preparing value. */
    PREPARING,
    /** The ready for pickup value. */
    READY_FOR_PICKUP,
    /** The picked up value. */
    PICKED_UP,
    /** The ready for delivery value. */
    READY_FOR_DELIVERY,
    /** The out for delivery value. */
    OUT_FOR_DELIVERY,
    /** The delivered value. */
    DELIVERED,
    /** The payment failed value. */
    PAYMENT_FAILED,
    /** The cancelled value. */
    CANCELLED,
    /** The no show value. */
    NO_SHOW,
    /** The pickup window expired value. */
    PICKUP_WINDOW_EXPIRED
}
