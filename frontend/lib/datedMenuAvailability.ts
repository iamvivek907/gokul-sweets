import type {MenuProduct} from "@/types/menu";

/** Only a clock-based closure may be reconsidered for a later pickup. Manual closures remain blocked. */
export function datedMenuAvailable(product: MenuProduct): boolean {
    if (product.catalogAvailable === false) return false;
    if (product.available) return true;
    const status = product.serviceAvailability;
    return product.catalogAvailable === true && (["OUTSIDE_SERVICE", "SERVICE_WINDOW"].includes(status?.code ?? "")
        || status?.code === "DEPENDENCY_UNAVAILABLE" && !!status.nextChangeAt);
}
