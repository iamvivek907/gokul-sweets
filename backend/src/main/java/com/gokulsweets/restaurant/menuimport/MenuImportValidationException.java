package com.gokulsweets.restaurant.menuimport;

/** Application-authored, actionable workbook validation details safe to show to staff. */
final class MenuImportValidationException extends IllegalArgumentException {

    /**
     * Creates a menu import validation exception instance.
     *
     * @param message the message
     */
    MenuImportValidationException(String message) {
        super(message);
    }

    /**
     * Creates a menu import validation exception instance.
     *
     * @param message the message
     * @param cause the cause
     */
    MenuImportValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
