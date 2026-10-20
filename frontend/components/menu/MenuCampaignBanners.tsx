/* eslint-disable @next/next/no-img-element -- Managed media uses the saved crop and public URL. */
"use client";
import {useEffect, useState} from "react";
import {useLanguage} from "@/lib/language";
import {campaignFrameStyle} from "@/lib/campaignFraming";
import type {MenuBanner} from "@/components/admin/WorkspaceAppearance";
import styles from "./BranchMenuAppearance.module.css";

/** Published admin content, including its order, schedule, crop and translated copy. */
export default function MenuCampaignBanners({banners, categories, onSelect, compact = false}: {
    banners: MenuBanner[];
    categories: {id: number; name: string}[];
    onSelect: (id: number) => void;
    compact?: boolean;
}) {
    const locale = useLanguage();
    const [reduced, setReduced] = useState(true);
    useEffect(() => {
        const media = window.matchMedia("(prefers-reduced-motion: reduce)");
        const update = () => setReduced(media.matches);
        update(); media.addEventListener("change", update);
        return () => media.removeEventListener("change", update);
    }, []);
    if (!banners.length) return null;
    return <div className={compact ? styles.strips : undefined} aria-label="Published menu banners">
        {banners.map(b => <article key={b.key} className={`${styles.banner} ${compact ? styles.strip : ""}`}>
            {b.mediaUrl && <div className={styles.frame}>
                {b.mediaType?.startsWith("video/") && !reduced
                    ? <video key={b.mediaUrl} src={b.mediaUrl} poster={b.posterUrl ?? undefined} muted autoPlay loop playsInline controls style={campaignFrameStyle(b.frame)} />
                    : <img key={b.mediaUrl} src={b.mediaType?.startsWith("video/") ? (b.posterUrl ?? "") : b.mediaUrl} alt="" loading="lazy" style={campaignFrameStyle(b.frame)} onError={e => {e.currentTarget.hidden = true;}} />}
            </div>}
            <div className={styles.copy}>
                <h2>{locale === "hi" && b.titleHi ? b.titleHi : b.titleEn}</h2>
                <p>{locale === "hi" && b.subtitleHi ? b.subtitleHi : b.subtitleEn}</p>
                {b.buttonLabel && b.categoryId && categories.some(c => c.id === b.categoryId) && <button type="button" onClick={() => onSelect(b.categoryId!)}>{b.buttonLabel} <span aria-hidden="true">→</span></button>}
            </div>
        </article>)}
    </div>;
}
