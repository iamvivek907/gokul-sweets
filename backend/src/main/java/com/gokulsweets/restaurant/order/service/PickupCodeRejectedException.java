package com.gokulsweets.restaurant.order.service;

/** Rejection before handover writes: commit only the failed-attempt counter/cooldown. */
public final class PickupCodeRejectedException extends IllegalArgumentException {

    /**
     * Creates a pickup code rejected exception instance.
     *
     * @param message the message
     */
    public PickupCodeRejectedException(String message) {
        super(message);
    }
}
