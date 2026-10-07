import { adminFetch } from "@/services/adminApi";
import type {
  InventoryAllocation,
  InventoryPolicy,
} from "@/types/adminInventory";
export type WorkspaceItem = {
  branchProductId: number;
  productId: number;
  code: string;
  name: string;
  description: string | null;
  categoryId: number;
  categoryName: string;
  basePrice: number;
  priceOverride: number | null;
  effectivePrice: number;
  available: boolean;
  active: boolean;
  saleMode: "UNIT" | "WEIGHT";
  minimumWeightGrams: number | null;
  weightStepGrams: number | null;
  taxCategoryId: number | null;
  imageUrl: string | null;
  productVersion: number;
  branchVersion: number;
  allocationVersion: number | null;
  policyVersion: number | null;
  policy: InventoryPolicy | null;
  allocation: InventoryAllocation | null;
};
export type WorkspacePage = {
  content: WorkspaceItem[];
  totalElements: number;
  page: number;
  totalPages: number;
  categories: { id: number; name: string }[];
  branchCategories: { id: number; name: string }[];
  taxes: { id: number; name: string }[];
};
export type Group = {
  key: string;
  title: string;
  choices: { productId: number; label: string }[];
};
export type GroupPage = {
  version: number;
  groups: Group[];
  total: number;
  page: number;
  totalPages: number;
};
export async function workspaceRequest<T>(
  branch: number,
  path: string,
  init: RequestInit = {},
  authorization = "staff-session",
): Promise<T> {
  const response = await adminFetch(
    `/api/admin/branches/${branch}/menu/workspace${path}`,
    authorization,
    init,
  );
  if (!response.ok) {
    let message =
      response.status === 409
        ? "This item changed. Close and reload before saving; your draft is retained."
        : "Unable to save or load. Please retry.";
    try {
      const data = await response.json();
      message = data.message || message;
    } catch {}
    throw new Error(message);
  }
  return response.status === 204 ||
    response.headers.get("content-length") === "0"
    ? (undefined as T)
    : await response.text().then((t) => (t ? JSON.parse(t) : undefined));
}
export const jsonRequest = (method: string, body: unknown): RequestInit => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: JSON.stringify(body),
});
export function stockQuantity(value: number | null | undefined, unit: string) {
  if (value == null) return "—";
  return unit === "GRAM"
    ? value >= 1000
      ? `${Number((value / 1000).toFixed(3))} kg`
      : `${value} g`
    : `${value} pieces`;
}
