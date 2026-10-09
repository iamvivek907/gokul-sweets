package com.gokulsweets.restaurant.kot.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Component;

/** Backend kot number generator contract and implementation. */
@Component
public class KotNumberGenerator {

    private static final String PREFIX = AppConstant.KOT_NUMBER_GENERATOR_PREFIX;

    /**
     * Generates kot number generator data and returns the {@code String} result.
     *
     * @param orderId the order id supplied to this method
     * @return the {@code String} result
     * @throws IllegalArgumentException when the method rejects the request with {@code Order ID is
     *     required to generate a KOT number.}; {@code Order ID must be positive.}
     */
    public String generate(Long orderId) {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(KotNumberGenerator.class, "generate(Long)");
        try {
            if (orderId == null) {
                throw new IllegalArgumentException(
                        "Order ID is required to generate a KOT number.");
            }
            if (orderId <= 0) {
                throw new IllegalArgumentException("Order ID must be positive.");
            }
            return PREFIX + String.format("%08d", orderId);
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, KotNumberGenerator.class, "generate(Long)");
        }
    }
}
