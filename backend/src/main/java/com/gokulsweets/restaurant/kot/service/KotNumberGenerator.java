package com.gokulsweets.restaurant.kot.service;

import com.gokulsweets.restaurant.observability.MethodTiming;

import org.springframework.stereotype.Component;

/** Backend kot number generator contract and implementation. */
@Component
public class KotNumberGenerator {

    private static final String PREFIX = AppConstant.KOT_NUMBER_GENERATOR_PREFIX;

    /**
     * Generates the operation.
     *
     * @param orderId the order id
     * @return the generate result
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
