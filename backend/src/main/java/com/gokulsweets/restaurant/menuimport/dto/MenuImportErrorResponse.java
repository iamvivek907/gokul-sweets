package com.gokulsweets.restaurant.menuimport.dto;

public record MenuImportErrorResponse(
        int row,
        String column,
        String message
) {
}
