export type ProductSaleMode =
    | "UNIT"
    | "WEIGHT";


export interface MenuProduct {

    id: number;

    categoryId: number;

    categoryName: string;

    name: string;

    description: string | null;

    price: number;

    comparisonPrice?: number | null;

    imageUrl: string | null;

    available: boolean;

    /** Branch catalog toggle, independent of the current service hour. */
    catalogAvailable?: boolean;

    /** Absent only for older API responses; never infer a dietary label. */
    vegetarian?: boolean;

    /** Live menu revision used to invalidate dated service-hour previews. */
    availabilityRevision?: string;

    serviceAvailability?: {available: boolean; code: string; message: string | null; nextChangeAt: string | null; evaluatedAt?: string | null; receivedMonotonic?: number} | null;

    saleMode: ProductSaleMode;

    minimumWeightGrams: number | null;

    weightStepGrams: number | null;
}


export interface MenuCategory {

    id: number;

    name: string;

    description: string | null;

    displayOrder: number;

    products: MenuProduct[];
}
