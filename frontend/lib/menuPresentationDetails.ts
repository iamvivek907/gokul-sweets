import type {MenuProduct} from "@/types/menu";
import type {ProductRatingSummary} from "@/types/review";

/** Group scores count each size's item ratings, independently of its photo or pickup availability. */
export function groupedRatingSummary(
    products: ReadonlyArray<Pick<MenuProduct, "id">>,
    ratings: Readonly<Record<number, ProductRatingSummary>>,
): Pick<ProductRatingSummary, "averageRating" | "ratingCount"> | null {
    let ratingCount = 0;
    let ratingTotal = 0;
    for (const id of new Set(products.map(product => product.id))) {
        const summary = ratings[id];
        if (!summary || summary.productId !== id || !Number.isSafeInteger(summary.ratingCount) || summary.ratingCount <= 0
            || !Number.isFinite(summary.averageRating) || summary.averageRating < 1 || summary.averageRating > 5) continue;
        ratingCount += summary.ratingCount;
        ratingTotal += summary.averageRating * summary.ratingCount;
    }
    return ratingCount > 0 ? {averageRating: ratingTotal / ratingCount, ratingCount} : null;
}

/** Public menu amounts retain paise without adding .00 to every whole price. */
export function menuMoney(amount: number): string {
    return new Intl.NumberFormat("en-IN", {style: "currency", currency: "INR", minimumFractionDigits: 0, maximumFractionDigits: 2}).format(amount);
}

export function comparisonPrice(product: Pick<MenuProduct, "price" | "comparisonPrice">): number | null {
    return typeof product.comparisonPrice === "number" && Number.isFinite(product.comparisonPrice) && Number.isFinite(product.price)
        && product.price > 0 && product.comparisonPrice > product.price ? product.comparisonPrice : null;
}

/** Keep configured food information; don't fill missing details with invented claims. */
export function menuDescription(product: Pick<MenuProduct, "name" | "description">): string | null {
    const description = product.description?.trim();
    if (!description) return null;
    const name = product.name.trim().toLowerCase();
    const text = description.toLowerCase().replace(/[.!]+$/, "");
    if (text === name || text === `${name}. served per piece` || text === `${name}. sold by weight`) return null;
    return description;
}
