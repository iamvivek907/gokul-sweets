package com.gokulsweets.restaurant.menuimport.dto;

public record MenuImportResultResponse(
        boolean success,
        int rowsProcessed,
        int categoriesCreated,
        int categoriesUpdated,
        int productsCreated,
        int productsUpdated,
        int branchProductsCreated,
        int branchProductsUpdated
) {
}
