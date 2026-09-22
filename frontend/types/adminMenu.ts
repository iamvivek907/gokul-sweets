export interface AdminBranchProduct {
    branchProductId: number;

    branchId: number;

    productId: number;

    productCode: string;

    productName: string;

    productDescription: string | null;

    productActive: boolean;

    categoryId: number;

    categoryCode: string;

    categoryName: string;

    categoryActive: boolean;

    basePrice: number;

    priceOverride: number | null;

    effectivePrice: number;

    available: boolean;

    displayOrder: number;
}


export interface AdminBranchProductUpdateRequest {
    available?: boolean;

    priceOverride?: number;

    clearPriceOverride?: boolean;

    displayOrder?: number;
}