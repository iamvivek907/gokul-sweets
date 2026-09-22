export interface MenuImportError {
    row: number;

    column: string;

    message: string;
}


export interface MenuImportValidationResponse {
    valid: boolean;

    totalRows: number;

    errors: MenuImportError[];
}


export interface MenuImportResultResponse {
    success: boolean;

    rowsProcessed: number;

    categoriesCreated: number;

    categoriesUpdated: number;

    productsCreated: number;

    productsUpdated: number;

    branchProductsCreated: number;

    branchProductsUpdated: number;
}