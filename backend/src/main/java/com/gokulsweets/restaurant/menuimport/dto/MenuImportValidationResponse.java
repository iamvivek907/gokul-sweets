package com.gokulsweets.restaurant.menuimport.dto;

import java.util.List;

/**
 * Immutable menu import validation response data contract.
 *
 * @param valid the valid
 * @param totalRows the total rows
 * @param errors the errors
 */
public record MenuImportValidationResponse(
        boolean valid, int totalRows, List<MenuImportErrorResponse> errors) {}
