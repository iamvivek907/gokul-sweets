package com.gokulsweets.restaurant.inventory.exception;

import com.gokulsweets.restaurant.observability.MethodTiming;

/** Backend inventory not found exception contract and implementation. */
public class InventoryNotFoundException extends RuntimeException {

    private final String code;

    /**
     * Creates a inventory not found exception instance.
     *
     * @param code the code
     * @param message the message
     */
    public InventoryNotFoundException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Returns code.
     *
     * @return the get code result
     */
    public String getCode() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryNotFoundException.class, "getCode()");
        try {
            return code;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryNotFoundException.class, "getCode()");
        }
    }
}
