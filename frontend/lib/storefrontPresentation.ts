import type {ItemAvailability} from "@/services/availabilityApi";

export function formatAvailableAmount(quantity: number | null, unit: "GRAM" | "PIECE"): string | null {
    if (quantity === null || quantity < 0) return null;
    if (unit === "GRAM") {
        return quantity >= 1000
            ? `${new Intl.NumberFormat("en-IN", {maximumFractionDigits: 3}).format(quantity / 1000)} kg`
            : `${quantity} g`;
    }
    return `${quantity} ${quantity === 1 ? "piece" : "pieces"}`;
}

export function describePickupAvailability(item: ItemAvailability | undefined, checking: boolean): string {
    if (!item) return checking ? "Checking this pickup date…" : "Choose a pickup date to check this item.";
    if (!item.available) {
        const remaining = item.code === "QUANTITY_TOO_LARGE"
            ? formatAvailableAmount(item.availableQuantity, item.unit) : null;
        return remaining ? `Not enough for this pickup date. Up to ${remaining} may be available.`
            : "Unavailable for this pickup date. Try another date.";
    }
    return "Available for the selected date. Your exact quantity and pickup slot are confirmed at checkout.";
}
