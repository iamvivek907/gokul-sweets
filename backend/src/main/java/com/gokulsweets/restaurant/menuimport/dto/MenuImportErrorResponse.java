package com.gokulsweets.restaurant.menuimport.dto;

/**
 * Immutable menu import error response data contract.
 *
 * @param row the row
 * @param column the column
 * @param message the message
 */
public record MenuImportErrorResponse(int row, String column, String message) {}
