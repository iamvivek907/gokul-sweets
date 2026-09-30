export function formatWeight(grams: number | null | undefined): string {
    if (grams == null || !Number.isFinite(grams) || grams < 0) return "Weight pending";
    const format = new Intl.NumberFormat("en-IN", {maximumFractionDigits: 3});
    return grams >= 1000 ? `${format.format(grams / 1000)} kg` : `${format.format(grams)} g`;
}
