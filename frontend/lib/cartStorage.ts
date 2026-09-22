import type {
    CartItem,
    CartState
} from "@/types/cart";


export const CART_STORAGE_KEY = "gokul-cart";
export const CART_CHANGE_EVENT = "gokul-cart-change";

export const EMPTY_CART: CartState = {
    branchId: null,
    items: []
};


export function getCartSnapshot(): string {
    if (typeof window === "undefined") {
        return "";
    }

    return window.localStorage.getItem(CART_STORAGE_KEY) ?? "";
}


export function getServerCartSnapshot(): string {
    return "";
}


export function subscribeToCart(
    callback: () => void
): () => void {

    if (typeof window === "undefined") {
        return () => {};
    }

    function handleStorage(event: StorageEvent): void {
        if (event.key === CART_STORAGE_KEY) {
            callback();
        }
    }

    window.addEventListener("storage", handleStorage);
    window.addEventListener(CART_CHANGE_EVENT, callback);

    return () => {
        window.removeEventListener("storage", handleStorage);
        window.removeEventListener(CART_CHANGE_EVENT, callback);
    };
}


export function parseCart(value: string): CartState {

    if (!value) {
        return EMPTY_CART;
    }

    try {
        const parsed = JSON.parse(value) as Partial<CartState>;

        if (!Array.isArray(parsed.items)) {
            return EMPTY_CART;
        }

        const items = parsed.items
            .filter(
                (item): item is CartItem =>
                    typeof item === "object"
                    && item !== null
                    && typeof item.product?.id === "number"
                    && typeof item.quantity === "number"
            )
            .map(item => {
                /*
                 * Compatibility for carts saved before saleMode existed.
                 * The V44 backfill converts categories containing SWEET to
                 * WEIGHT, so mirror that migration in the browser snapshot.
                 */
                const inferredWeighted =
                    item.product.saleMode === "WEIGHT"
                    || (
                        item.product.saleMode === undefined
                        && item.product.categoryName
                            .toUpperCase()
                            .includes("SWEET")
                    );

                const saleMode = inferredWeighted
                    ? "WEIGHT" as const
                    : "UNIT" as const;

                return {
                    ...item,
                    product: {
                        ...item.product,
                        saleMode,
                        minimumWeightGrams:
                            saleMode === "WEIGHT"
                                ? item.product.minimumWeightGrams ?? 250
                                : null,
                        weightStepGrams:
                            saleMode === "WEIGHT"
                                ? item.product.weightStepGrams ?? 50
                                : null
                    },
                    quantity:
                        saleMode === "WEIGHT"
                            ? 1
                            : item.quantity,
                    weightGrams:
                        saleMode === "WEIGHT"
                            ? typeof item.weightGrams === "number"
                                ? item.weightGrams
                                : 250
                            : null
                };
            });

        return {
            branchId:
                typeof parsed.branchId === "number"
                    ? parsed.branchId
                    : null,
            items
        };

    } catch {
        return EMPTY_CART;
    }
}


export function saveCart(cart: CartState): void {
    if (typeof window === "undefined") {
        return;
    }

    window.localStorage.setItem(CART_STORAGE_KEY, JSON.stringify(cart));
    window.dispatchEvent(new Event(CART_CHANGE_EVENT));
}


export function clearStoredCart(): void {
    if (typeof window === "undefined") {
        return;
    }

    window.localStorage.removeItem(CART_STORAGE_KEY);
    window.dispatchEvent(new Event(CART_CHANGE_EVENT));
}
