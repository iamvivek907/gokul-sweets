package com.gokulsweets.restaurant.order.service;

/** Rejection before handover writes: commit only the failed-attempt counter/cooldown. */
public final class PickupCodeRejectedException extends IllegalArgumentException {

    public PickupCodeRejectedException(String message) {
        super(message);
    }
}
