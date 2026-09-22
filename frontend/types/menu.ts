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