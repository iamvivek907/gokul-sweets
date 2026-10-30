import type {
    MenuProduct
} from "@/types/menu";


export interface CartItem {
    mealIdentity?: string;
    product: MenuProduct;
    quantity: number;
    weightGrams: number | null;
}


export interface CartState {
    meals?: import("./meal").CartMeal[];
    mealNotice?: string;
    branchId: number | null;
    items: CartItem[];
}


export type AddToCartResult =
    | "added"
    | "branch-mismatch"
    | "weight-required";
