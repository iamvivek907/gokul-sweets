import type {CartItem, CartState} from "@/types/cart";

export type VitalPayload = {name: string; value: number; rating: string; page: string};

export function vitalPage(path: string): string {
    if (path === "/") return "home";
    if (path.startsWith("/menu")) return "menu";
    if (path.startsWith("/cart")) return "cart";
    if (path.startsWith("/checkout")) return "checkout";
    return "other";
}

export function safeVital(name: string, value: number, rating: string, path: string): VitalPayload | null {
    if (!["TTFB", "FCP", "LCP", "CLS", "INP", "FID", "MENU_NETWORK", "MENU_BODY", "MENU_PREVIEW", "MENU_RENDER", "MENU_IMAGE", "FUNNEL_MENU", "FUNNEL_ADD", "FUNNEL_CHECKOUT", "FUNNEL_PAYMENT", "FUNNEL_PAID", "FUNNEL_FAILED", "FUNNEL_EXPIRED"].includes(name) ||
        !(name.startsWith("MENU_") || name.startsWith("FUNNEL_") ? rating === "measured" : ["good", "needs-improvement", "poor"].includes(rating)) || !Number.isFinite(value) || value < 0 || value > 120_000)
        return null;
    if (name.startsWith("FUNNEL_") && value !== 1) return null;
    return {name, value: Math.round(value * 100) / 100, rating, page: vitalPage(path)};
}

function cartAmount(item: CartItem): number {
    const value = item.product.saleMode === "WEIGHT" ? item.weightGrams ?? 0 : item.quantity;
    return Number.isFinite(value) && value > 0 ? value : 0;
}

/** Compare cart amounts locally; identifiers and quantities never enter the telemetry payload. */
export function cartHasIncreased(previous: CartState, current: CartState): boolean {
    const before = new Map(previous.branchId === current.branchId
        ? previous.items.map(item => [item.product.id, item] as const) : []);
    return current.items.some(item => {
        const old = before.get(item.product.id);
        // A catalogue unit change is not evidence that the customer added anything.
        if (old && old.product.saleMode !== item.product.saleMode) return false;
        return cartAmount(item) > (old ? cartAmount(old) : 0);
    });
}
