package com.gokulsweets.restaurant.menuimport.dto;

import java.util.List;

/** Immutable menu import validation response data contract. */
public record MenuImportValidationResponse(
        boolean valid, int totalRows, List<MenuImportErrorResponse> errors) {}
