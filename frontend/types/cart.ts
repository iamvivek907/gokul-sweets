import type {
    MenuProduct
} from "@/types/menu";


export interface CartItem {
    product: MenuProduct;
    quantity: number;
    weightGrams: number | null;
}


export interface CartState {
    branchId: number | null;
    items: CartItem[];
}


export type AddToCartResult =
    | "added"
    | "branch-mismatch"
    | "weight-required";
