import {ApiError} from "@/services/apiClient";

function displayQuantity(value: unknown, unit: unknown): string | null {
    if (typeof value !== "number") return null;
    if (unit === "GRAM") {
        return `${new Intl.NumberFormat("en-IN", {
            maximumFractionDigits: 3
        }).format(value / 1000)} kg`;
    }
    return `${new Intl.NumberFormat("en-IN").format(value)} pieces`;
}

export function checkoutErrorMessage(error: unknown): string {
    if (!(error instanceof ApiError)) {
        return error instanceof Error
            ? error.message
            : "Unable to prepare your order. Please try again.";
    }

    if (error.code !== "INSUFFICIENT_INVENTORY") return error.message;

    const product = typeof error.details.productName === "string"
        ? error.details.productName
        : "This item";
    const available = displayQuantity(
        error.details.availableQuantity,
        error.details.inventoryUnit
    );
    const date = typeof error.details.serviceDate === "string"
        ? error.details.serviceDate
        : null;

    if (available && date) {
        return `${product} has only ${available} available online for ${date}. Reduce the quantity or choose another pickup date.`;
    }
    return `${error.message} Reduce the quantity or choose another pickup date.`;
}
