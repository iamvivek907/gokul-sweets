export type InventoryDisplayUnit = "PIECE" | "GRAM" | "CAPACITY_POINT";

export function apiQuantityToInput(
    quantity: number | null | undefined,
    unit: InventoryDisplayUnit | string | undefined
): string {
    if (quantity === null || quantity === undefined) return "";
    return unit === "GRAM" ? String(quantity / 1000) : String(quantity);
}

export function inputQuantityToApi(
    value: string,
    unit: InventoryDisplayUnit | string | undefined
): number {
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) return Number.NaN;
    return unit === "GRAM" ? Math.round(parsed * 1000) : Math.round(parsed);
}

export function inventoryInputUnit(
    unit: InventoryDisplayUnit | string | undefined
): string {
    if (unit === "GRAM") return "kg";
    if (unit === "CAPACITY_POINT") return "capacity";
    return "pieces";
}

export function formatInventoryQuantity(
    quantity: number | null | undefined,
    unit: InventoryDisplayUnit | string | undefined
): string {
    const value = quantity ?? 0;
    if (unit === "GRAM") {
        return `${new Intl.NumberFormat("en-IN", {
            maximumFractionDigits: 3
        }).format(value / 1000)} kg`;
    }
    if (unit === "CAPACITY_POINT") {
        return `${new Intl.NumberFormat("en-IN").format(value)} capacity`;
    }
    return `${new Intl.NumberFormat("en-IN").format(value)} pcs`;
}

export function hoursToMinutes(value: string): number {
    const hours = Number(value);
    return Number.isFinite(hours) ? Math.round(hours * 60) : Number.NaN;
}

export function daysToMinutes(value: string): number | null {
    if (value.trim() === "") return null;
    const days = Number(value);
    return Number.isFinite(days) ? Math.round(days * 24 * 60) : Number.NaN;
}
