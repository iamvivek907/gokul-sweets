package com.gokulsweets.restaurant.menuimport.dto;

import java.util.List;

public record MenuImportValidationResponse(
        boolean valid,
        int totalRows,
        List<MenuImportErrorResponse> errors
) {
}
