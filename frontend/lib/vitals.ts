export type VitalPayload = {name: string; value: number; rating: string; page: string};

export function vitalPage(path: string): string {
    if (path === "/") return "home";
    if (path.startsWith("/menu")) return "menu";
    if (path.startsWith("/cart")) return "cart";
    if (path.startsWith("/checkout")) return "checkout";
    return "other";
}

export function safeVital(name: string, value: number, rating: string, path: string): VitalPayload | null {
    if (!["TTFB", "FCP", "LCP", "CLS", "INP", "FID"].includes(name) ||
        !["good", "needs-improvement", "poor"].includes(rating) || !Number.isFinite(value) || value < 0 || value > 120_000)
        return null;
    return {name, value: Math.round(value * 100) / 100, rating, page: vitalPage(path)};
}
