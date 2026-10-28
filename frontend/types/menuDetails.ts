export interface MenuDetails {
  productId: number;
  servingInfo: string | null;
  ingredients: string | null;
  allergens: string | null;
  spiceLevel: "MILD" | "MEDIUM" | "HOT" | null;
  photoFocusX: number;
  photoFocusY: number;
  chefPick: boolean;
  version: number;
}
export const emptyMenuDetails = (productId: number): MenuDetails => ({
  productId,
  servingInfo: null,
  ingredients: null,
  allergens: null,
  spiceLevel: null,
  photoFocusX: 50,
  photoFocusY: 50,
  chefPick: false,
  version: 0,
});
export function dishPhotoPosition(details?: MenuDetails): string {
  const valid = (n: unknown) =>
    typeof n === "number" && Number.isFinite(n) && n >= 0 && n <= 100 ? n : 50;
  return `${valid(details?.photoFocusX)}% ${valid(details?.photoFocusY)}%`;
}
/** Optional metadata never supplies prices or orderability. */
export function isMenuDetails(value: unknown): value is MenuDetails {
  if (!value || typeof value !== "object") return false;
  const row = value as Record<string, unknown>;
  const text = (name: string, max: number) =>
    row[name] === null ||
    (typeof row[name] === "string" && (row[name] as string).length <= max);
  const point = (n: unknown) =>
    typeof n === "number" && Number.isInteger(n) && n >= 0 && n <= 100;
  return (
    typeof row.productId === "number" &&
    Number.isSafeInteger(row.productId) &&
    row.productId > 0 &&
    text("servingInfo", 120) &&
    text("ingredients", 600) &&
    text("allergens", 300) &&
    (row.spiceLevel === null ||
      (typeof row.spiceLevel === "string" &&
        ["MILD", "MEDIUM", "HOT"].includes(row.spiceLevel))) &&
    point(row.photoFocusX) &&
    point(row.photoFocusY) &&
    typeof row.chefPick === "boolean" &&
    typeof row.version === "number" &&
    Number.isSafeInteger(row.version) &&
    row.version >= 0
  );
}
