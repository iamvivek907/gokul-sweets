/* eslint-disable @next/next/no-img-element -- Direct media rendering exposes storage failures and supports local crop previews. */
"use client";
import { useEffect, useState } from "react";
import { apiClient } from "@/services/apiClient";
import { useLanguage } from "@/lib/language";
import { campaignFrameStyle } from "@/lib/campaignFraming";
import type { AppearanceConfig } from "@/components/admin/WorkspaceAppearance";
import styles from "./BranchMenuAppearance.module.css";
export default function BranchMenuAppearance({
  branchId,
  categories,
  onSelect,
  onOrder,
}: {
  branchId: number;
  categories: { id: number; name: string }[];
  onSelect: (id: number) => void;
  onOrder: (orders: Record<number, number>) => void;
}) {
  const [config, setConfig] = useState<AppearanceConfig | null>(null);
  const locale = useLanguage();
  const [reduced, setReduced] = useState(true);
  useEffect(() => {
    const media = window.matchMedia("(prefers-reduced-motion: reduce)");
    const update = () => setReduced(media.matches);
    update();
    media.addEventListener("change", update);
    return () => media.removeEventListener("change", update);
  }, []);
  useEffect(() => {
    const c = new AbortController();
    apiClient<AppearanceConfig>(`/api/menu/appearance?branchId=${branchId}`, {
      signal: c.signal,
      cacheMode: "no-store",
    })
      .then((data) => {
        if (!c.signal.aborted && data && Array.isArray(data.banners) && Array.isArray(data.categories)) {
          setConfig(data);
          onOrder(
            Object.fromEntries(data.categories.map((x) => [x.id, x.order])),
          );
        }
      })
      .catch(() => {
        /* Keep existing menu and imagery when appearance is unavailable. */
      });
    return () => c.abort();
  }, [branchId, onOrder]);
  if (!config || (!config.banners.length && !config.categories.length)) return null;
  return (
    <section className={styles.appearance} aria-label="Branch menu highlights">
      {config.banners.map((b) => (
        <article className={styles.banner} key={b.key}>
          {b.mediaUrl && (
            <div className={styles.frame}>
              {b.mediaType?.startsWith("video/") && !reduced ? (
                <video
                  src={b.mediaUrl}
                  poster={b.posterUrl ?? undefined}
                  muted
                  autoPlay
                  loop
                  playsInline
                  controls
                  style={campaignFrameStyle(b.frame)}
                />
              ) : (
                <img
                  src={
                    b.mediaType?.startsWith("video/")
                      ? (b.posterUrl ?? "")
                      : b.mediaUrl
                  }
                  alt=""
                  loading="lazy"
                  style={campaignFrameStyle(b.frame)}
                  onError={(e) => {
                    e.currentTarget.hidden = true;
                  }}
                />
              )}
            </div>
          )}
          <div className={styles.copy}>
            <h2>{locale === "hi" && b.titleHi ? b.titleHi : b.titleEn}</h2>
            <p>
              {locale === "hi" && b.subtitleHi ? b.subtitleHi : b.subtitleEn}
            </p>
            {b.buttonLabel &&
              b.categoryId &&
              categories.some((c) => c.id === b.categoryId) && (
                <button onClick={() => onSelect(b.categoryId!)}>
                  {b.buttonLabel} →
                </button>
              )}
          </div>
        </article>
      ))}
      {config.categories.length > 0 && (
        <div className={styles.categories}>
          {[...config.categories]
            .sort((a, b) => a.order - b.order)
            .filter((c) => categories.some((x) => x.id === c.id))
            .map((c) => (
              <button key={c.id} onClick={() => onSelect(c.id)}>
                {c.imageUrl && (
                  <img
                    src={c.imageUrl}
                    alt=""
                    loading="lazy"
                    onError={(e) => {
                      e.currentTarget.hidden = true;
                    }}
                  />
                )}
                <span>{categories.find((x) => x.id === c.id)?.name}</span>
              </button>
            ))}
        </div>
      )}
    </section>
  );
}
