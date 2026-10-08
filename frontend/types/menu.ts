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

    imageUrl: string | null;

    available: boolean;

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