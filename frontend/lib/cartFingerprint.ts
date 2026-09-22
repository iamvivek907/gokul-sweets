import type {
    CartItem
} from "@/types/cart";


/*
 * Stable client-side comparison only. The backend still validates and prices
 * every item. Weight must be included so changing 500 g to 600 g is detected.
 */
export function createCartFingerprint(
    items: CartItem[]
): string {
    return items
        .map(item => ({
            productId: item.product.id,
            saleMode: item.product.saleMode,
            quantity:
                item.product.saleMode === "UNIT"
                    ? item.quantity
                    : null,
            weightGrams:
                item.product.saleMode === "WEIGHT"
                    ? item.weightGrams
                    : null
        }))
        .sort((first, second) => first.productId - second.productId)
        .map(item =>
            `${item.productId}:${item.saleMode}:${item.quantity ?? "-"}:${item.weightGrams ?? "-"}`
        )
        .join("|");
}
