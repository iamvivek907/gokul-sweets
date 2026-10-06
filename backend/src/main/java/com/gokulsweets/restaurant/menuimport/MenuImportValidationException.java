package com.gokulsweets.restaurant.menuimport;

/** Application-authored, actionable workbook validation details safe to show to staff. */
final class MenuImportValidationException extends IllegalArgumentException {
    MenuImportValidationException(String message) { super(message); }
    MenuImportValidationException(String message, Throwable cause) { super(message, cause); }
}
