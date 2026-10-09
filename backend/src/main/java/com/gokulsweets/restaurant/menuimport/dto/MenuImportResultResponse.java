package com.gokulsweets.restaurant.menuimport.dto;

/**
 * Immutable menu import result response data contract.
 *
 * @param success the success
 * @param rowsProcessed the rows processed
 * @param categoriesCreated the categories created
 * @param categoriesUpdated the categories updated
 * @param productsCreated the products created
 * @param productsUpdated the products updated
 * @param branchProductsCreated the branch products created
 * @param branchProductsUpdated the branch products updated
 */
public record MenuImportResultResponse(
        boolean success,
        int rowsProcessed,
        int categoriesCreated,
        int categoriesUpdated,
        int productsCreated,
        int productsUpdated,
        int branchProductsCreated,
        int branchProductsUpdated) {}
