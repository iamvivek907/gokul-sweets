package com.gokulsweets.restaurant.menuimport.dto;

/** Immutable menu import result response data contract. */
public record MenuImportResultResponse(
        boolean success,
        int rowsProcessed,
        int categoriesCreated,
        int categoriesUpdated,
        int productsCreated,
        int productsUpdated,
        int branchProductsCreated,
        int branchProductsUpdated) {}
