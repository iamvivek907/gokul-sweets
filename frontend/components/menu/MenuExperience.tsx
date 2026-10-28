"use client";
import {
  createContext,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { apiClient } from "@/services/apiClient";
import { progressiveBatches } from "@/lib/progressiveBatches";
import { useMenuFavourites } from "@/hooks/useMenuFavourites";
import { isMenuDetails, type MenuDetails } from "@/types/menuDetails";
import type { MenuProduct } from "@/types/menu";
import { T, useTranslation } from "@/lib/language";
import DishReviewSheet from "./DishReviewSheet";
type Experience = {
  branchId: number;
  details: Record<number, MenuDetails>;
  detailsFailedIds: number[];
  enabled: boolean;
  products: MenuProduct[];
  savedIds: number[];
  toggleSaved: (ids: number[]) => "saved" | "limit" | "unavailable";
  clearSaved: () => boolean;
};
const Context = createContext<Experience>({
  branchId: 0,
  details: {},
  detailsFailedIds: [],
  enabled: false,
  products: [],
  savedIds: [],
  toggleSaved: () => "unavailable",
  clearSaved: () => false,
});
export const useMenuExperience = () => useContext(Context);
export default function MenuExperience({
  branchId,
  products,
  enabled,
  children,
}: {
  branchId?: number;
  products: MenuProduct[];
  enabled: boolean;
  children: ReactNode;
}) {
  const saved = useMenuFavourites(branchId),
    ids = JSON.stringify(products.map((p) => p.id));
  const [snapshot, setSnapshot] = useState<{
    key: string;
    details: Record<number, MenuDetails>;
    failedIds: number[];
  } | null>(null);
  const key = `${branchId}:${ids}`;
  useEffect(() => {
    if (!enabled || !branchId) return;
    const controller = new AbortController(),
      all: number[] = JSON.parse(ids),
      gathered: Record<number, MenuDetails> = {},
      failedIds: number[] = [];
    const batches = Array.from(
      { length: Math.ceil(all.length / 100) },
      (_, i) => all.slice(i * 100, i * 100 + 100),
    );
    void progressiveBatches(
      batches,
      async (batch) => {
        try {
          const value = await apiClient<MenuDetails[]>(
            `/api/menu/details?branchId=${branchId}&productIds=${batch.join(",")}`,
            {
              signal: AbortSignal.any([
                controller.signal,
                AbortSignal.timeout(8000),
              ]),
            },
          );
          if (!Array.isArray(value) || !value.every(isMenuDetails))
            throw Error("Invalid dish details");
          return value;
        } catch (e) {
          failedIds.push(...batch);
          throw e;
        }
      },
      (values) => {
        for (const value of values)
          if (all.includes(value.productId)) gathered[value.productId] = value;
        setSnapshot({ key, details: { ...gathered }, failedIds: [...failedIds] });
      },
      controller.signal,
    ).then(() => {
      if (!controller.signal.aborted)
        setSnapshot({ key, details: gathered, failedIds });
    });
    return () => controller.abort();
  }, [branchId, ids, key, enabled]);
  const value = useMemo(
    () => ({
      branchId: branchId ?? 0,
      enabled,
      products,
      details: snapshot?.key === key ? snapshot.details : {},
      detailsFailedIds: snapshot?.key === key ? snapshot.failedIds : [],
      savedIds: saved.ids,
      toggleSaved: saved.toggle,
      clearSaved: saved.clear,
    }),
    [
      branchId,
      enabled,
      products,
      snapshot,
      key,
      saved.ids,
      saved.toggle,
      saved.clear,
    ],
  );
  return <Context.Provider value={value}>{children}</Context.Provider>;
}
export function SaveDish({
  productIds,
  name,
  inline = false,
}: {
  productIds: number[];
  name: string;
  inline?: boolean;
}) {
  const { branchId, enabled, savedIds, toggleSaved } = useMenuExperience(),
    t = useTranslation();
  const [message, setMessage] = useState("");
  if (!enabled || !branchId) return null;
  const selected = productIds.every((id) => savedIds.includes(id));
  return (
    <span className={`menu-save-dish${inline ? " menu-save-inline" : ""}`}>
      <button
        type="button"
        aria-pressed={selected}
        aria-label={`${t(selected ? "Unsave" : "Save")} ${name}${productIds.length > 1 ? ` · ${t("all sizes")}` : ""}`}
        onClick={() => {
          const result = toggleSaved(productIds);
          setMessage(
            result === "saved"
              ? ""
              : result === "limit"
                ? "You can save up to 50 dishes per branch."
                : "Favourites could not be saved on this device.",
          );
        }}
      >
        <svg
          viewBox="0 0 24 24"
          width="20"
          height="20"
          fill={selected ? "currentColor" : "none"}
          stroke="currentColor"
          strokeWidth="1.8"
          aria-hidden="true"
        >
          <path d="M20.8 4.6a5.4 5.4 0 0 0-7.6 0L12 5.8l-1.2-1.2a5.4 5.4 0 0 0-7.6 7.6L12 21l8.8-8.8a5.4 5.4 0 0 0 0-7.6Z" />
        </svg>
      </button>
      {message && (
        <small role="status">
          <T text={message} />
        </small>
      )}
    </span>
  );
}
export function DishReviewAction({
  productIds,
  name,
  children,
  summaryLabel,
}: {
  productIds: number[];
  name: string;
  children: ReactNode;
  summaryLabel?: string;
}) {
  const { branchId, enabled } = useMenuExperience(),
    t = useTranslation();
  const [open, setOpen] = useState(false);
  if (!enabled || !branchId) return children;
  return (
    <>
      <button
        type="button"
        className="menu-review-trigger"
        aria-label={`${t("Read reviews")} · ${name}${summaryLabel ? ` · ${summaryLabel}` : ""}`}
        onClick={() => setOpen(true)}
      >
        {children}
      </button>
      {open && (
        <DishReviewSheet
          key={branchId}
          branchId={branchId}
          productIds={productIds}
          name={name}
          onClose={() => setOpen(false)}
        />
      )}
    </>
  );
}
