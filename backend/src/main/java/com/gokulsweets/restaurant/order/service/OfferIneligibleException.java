package com.gokulsweets.restaurant.order.service;

/** Backend offer ineligible exception contract and implementation. */
public class OfferIneligibleException extends IllegalStateException {

    public OfferIneligibleException() {
        super("Your selected offer is no longer eligible for this order. Choose another offer.");
    }
}
