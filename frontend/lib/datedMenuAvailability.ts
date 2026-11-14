import type {MenuCategory, MenuProduct} from "@/types/menu";

/** Only a clock-based closure may be reconsidered for a later pickup. Manual closures remain blocked. */
export function datedMenuAvailable(product: MenuProduct): boolean {
    if (product.catalogAvailable === false) return false;
    if (product.available) return true;
    const status = product.serviceAvailability;
    return product.catalogAvailable === true && (["OUTSIDE_SERVICE", "SERVICE_WINDOW"].includes(status?.code ?? "")
        || status?.code === "DEPENDENCY_UNAVAILABLE" && !!status.nextChangeAt);
}

/** Convert a server cutoff into the client's monotonic clock using the request's lower bound. */
export function menuServiceDeadline(product: MenuProduct): number | null {
    const status = product.serviceAvailability;
    const anchor = status?.requestStartedMonotonic ?? status?.receivedMonotonic;
    if (!status?.evaluatedAt || !status.nextChangeAt || anchor === undefined) return null;
    const deadline = anchor + Date.parse(status.nextChangeAt) - Date.parse(status.evaluatedAt);
    return Number.isFinite(deadline) ? deadline : null;
}

export function blockExpiredMenuService(
    categories: MenuCategory[], now: number
): MenuCategory[] {
    let changed = false;
    const result = categories.map(category => {
        let categoryChanged = false;
        const products = category.products.map(product => {
            const deadline = menuServiceDeadline(product);
            const status = product.serviceAvailability;
            if (!status || status.code === "CHECKING" || deadline === null || now < deadline) return product;
            categoryChanged = true;
            return {...product, available: false, serviceAvailability: {
                ...status, available: false, code: "CHECKING", message: "Checking current availability…"
            }};
        });
        if (!categoryChanged) return category;
        changed = true;
        return {...category, products};
    });
    return changed ? result : categories;
}
