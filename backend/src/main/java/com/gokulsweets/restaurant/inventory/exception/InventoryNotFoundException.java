package com.gokulsweets.restaurant.inventory.exception;

public class InventoryNotFoundException
        extends RuntimeException {

    private final String code;

    public InventoryNotFoundException(
            String code,
            String message
    ) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}

