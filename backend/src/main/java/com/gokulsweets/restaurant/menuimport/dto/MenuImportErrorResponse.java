package com.gokulsweets.restaurant.menuimport.dto;

/** Immutable menu import error response data contract. */
public record MenuImportErrorResponse(int row, String column, String message) {}
