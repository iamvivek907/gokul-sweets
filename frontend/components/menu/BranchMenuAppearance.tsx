/* eslint-disable @next/next/no-img-element -- Direct media rendering exposes storage failures and supports local crop previews. */
"use client";
import type { AppearanceConfig } from "@/components/admin/WorkspaceAppearance";
import MenuCampaignBanners from "./MenuCampaignBanners";
import styles from "./BranchMenuAppearance.module.css";
export default function BranchMenuAppearance({
  config,
  categories,
  onSelect,
}: {
  config: AppearanceConfig | null;
  categories: { id: number; name: string }[];
  onSelect: (id: number) => void;
}) {
  if (!config || (!config.banners.length && !config.categories.length)) return null;
  return (
    <section className={styles.appearance} aria-label="Branch menu highlights">
      <MenuCampaignBanners banners={config.banners} categories={categories} onSelect={onSelect} />
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
