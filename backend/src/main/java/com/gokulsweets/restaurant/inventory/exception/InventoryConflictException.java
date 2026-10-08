package com.gokulsweets.restaurant.inventory.exception;

import com.gokulsweets.restaurant.observability.MethodTiming;

import java.util.Map;

/** Backend inventory conflict exception contract and implementation. */
public class InventoryConflictException extends RuntimeException {

    private final String code;

    private final Map<String, Object> details;

    public InventoryConflictException(String code, String message) {
        this(code, message, Map.of());
    }

    public InventoryConflictException(String code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    /**
     * Returns code.
     *
     * @return the get code result
     */
    public String getCode() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryConflictException.class, "getCode()");
        try {
            return code;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryConflictException.class, "getCode()");
        }
    }

    /**
     * Returns details.
     *
     * @return the get details result
     */
    public Map<String, Object> getDetails() {
        final long __gokulMethodStartedNanos =
                MethodTiming.start(InventoryConflictException.class, "getDetails()");
        try {
            return details;
        } finally {
            MethodTiming.finish(
                    __gokulMethodStartedNanos, InventoryConflictException.class, "getDetails()");
        }
    }
}
