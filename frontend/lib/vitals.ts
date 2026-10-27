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
