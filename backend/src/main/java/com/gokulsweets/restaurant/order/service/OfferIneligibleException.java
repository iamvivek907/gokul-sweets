package com.gokulsweets.restaurant.order.service;

public class OfferIneligibleException extends IllegalStateException {
    public OfferIneligibleException() {
        super("Your selected offer is no longer eligible for this order. Choose another offer.");
    }
}
