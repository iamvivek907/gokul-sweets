/* eslint-disable @next/next/no-img-element -- Managed media uses the saved crop and public URL. */
"use client";
import {useEffect, useRef, useState} from "react";
import {useLanguage, translate} from "@/lib/language";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {campaignFrameStyle} from "@/lib/campaignFraming";
import type {MenuBanner} from "@/components/admin/WorkspaceAppearance";
import styles from "./BranchMenuAppearance.module.css";

type Category = {id: number; name: string};
type Props = {banners: MenuBanner[]; categories: Category[]; onSelect: (id: number) => void; compact?: boolean};
type Slide = {banner: MenuBanner; category: Category | undefined; generated: boolean; key: string};

/** Published artwork and copy remain authoritative; a single campaign can introduce live categories. */
export default function MenuCampaignBanners({banners, categories, onSelect, compact = false}: Props) {
    const locale = useLanguage(), features = useStorefrontFeatures();
    const [reduced, setReduced] = useState(true);
    useEffect(() => {
        const media = window.matchMedia("(prefers-reduced-motion: reduce)");
        const update = () => setReduced(media.matches);
        update(); media.addEventListener("change", update);
        return () => media.removeEventListener("change", update);
    }, []);
    if (!banners.length) return null;
    const modern = features?.futuristicStorefrontV2 || features?.checkoutExperienceV2;
    const slides: Slide[] = banners.map(banner => ({banner, category: categories.find(c => c.id === banner.categoryId), generated: false, key: banner.key}));
    // Only expand a valid published category action. Missing/deleted destinations never create new ads.
    if (modern && banners.length === 1 && slides[0].category && banners[0].buttonLabel) {
        const unique = new Set([slides[0].category.id]);
        for (const category of categories) {
            if (unique.has(category.id) || slides.length >= 8) continue;
            unique.add(category.id);
            slides.push({banner: banners[0], category, generated: true, key: `${banners[0].key}:category:${category.id}`});
        }
    }
    const content = (slide: Slide, playing = true) => <>
        {slide.banner.mediaUrl && <div className={styles.frame}>
            {slide.banner.mediaType?.startsWith("video/") && !reduced && playing
                ? <video key={slide.banner.mediaUrl} src={slide.banner.mediaUrl} poster={slide.banner.posterUrl ?? undefined} muted autoPlay loop playsInline controls style={campaignFrameStyle(slide.banner.frame)} />
                : <img key={slide.banner.mediaUrl} src={slide.banner.mediaType?.startsWith("video/") ? (slide.banner.posterUrl ?? "") : slide.banner.mediaUrl} alt="" loading="lazy" draggable={false} style={campaignFrameStyle(slide.banner.frame)} onError={e => {e.currentTarget.hidden = true;}} />}
        </div>}
        <div className={styles.copy}>
            {modern && slide.category && <span className={styles.eyebrow}>{translate(slide.category.name, locale)}</span>}
            <h2>{locale === "hi" && slide.banner.titleHi ? slide.banner.titleHi : slide.banner.titleEn}</h2>
            <p>{locale === "hi" && slide.banner.subtitleHi ? slide.banner.subtitleHi : slide.banner.subtitleEn}</p>
            {slide.banner.buttonLabel && slide.category && <button type="button" onClick={() => onSelect(slide.category!.id)}>{slide.generated ? locale === "hi" ? `${translate(slide.category.name, locale)} देखें` : `Explore ${slide.category.name.toLocaleLowerCase("en-IN")}` : slide.banner.buttonLabel} <span aria-hidden="true">↗</span></button>}
        </div>
    </>;
    if (!modern) return <div className={compact ? styles.strips : undefined} aria-label="Published menu banners">
        {slides.map(slide => <article key={slide.key} className={`${styles.banner} ${compact ? styles.strip : ""}`}>{content(slide)}</article>)}
    </div>;
    return <CampaignCarousel key={slides.map(s => s.key).join("|")} slides={slides} reduced={reduced} locale={locale} compact={compact} render={content} />;
}

function CampaignCarousel({slides, reduced, locale, compact, render}: {
    slides: Slide[]; reduced: boolean; locale: "en" | "hi"; compact: boolean; render: (slide: Slide, playing: boolean) => React.ReactNode;
}) {
    const viewport = useRef<HTMLDivElement>(null);
    const [active, setActive] = useState(0);
    const go = (index: number) => {
        const target = viewport.current?.children[index] as HTMLElement | undefined;
        if (target && viewport.current) viewport.current.scrollTo({left: target.offsetLeft - (viewport.current.children[0] as HTMLElement).offsetLeft, behavior: reduced ? "instant" : "smooth"});
    };
    return <section className={`${styles.carousel} ${compact ? styles.compactCarousel : ""}`} aria-label="Published menu banners" aria-roledescription={locale === "hi" ? "कैरसेल" : "carousel"}>
        <div ref={viewport} className={styles.viewport} tabIndex={slides.length > 1 ? 0 : undefined} aria-label={locale === "hi" ? "श्रेणियाँ देखने के लिए स्वाइप करें" : "Swipe to explore categories"}
            onKeyDown={event => {if (event.target !== event.currentTarget) return; if (event.key === "ArrowRight") {event.preventDefault(); go(Math.min(active + 1, slides.length - 1));} if (event.key === "ArrowLeft") {event.preventDefault(); go(Math.max(active - 1, 0));}}}
            onScroll={event => {const node = event.currentTarget; const index = Math.round(node.scrollLeft / Math.max(node.clientWidth, 1)); setActive(Math.max(0, Math.min(slides.length - 1, index)));}}>
            {slides.map((slide, index) => <article key={slide.key} className={`${styles.banner} ${styles.slide}`} role="group" aria-roledescription={locale === "hi" ? "स्लाइड" : "slide"} aria-label={`${index + 1} / ${slides.length}`} aria-hidden={index !== active} inert={index !== active}>{render(slide, index === active)}</article>)}
        </div>
        {slides.length > 1 && <div className={styles.carouselNavigation}>
            <span className={styles.hint}>{locale === "hi" ? "हर खुशी का स्वाद" : "A little more to celebrate"}</span>
            <div className={styles.dots} aria-label={locale === "hi" ? "बैनर चुनें" : "Choose a banner"}>{slides.map((slide,index) => <button key={slide.key} type="button" aria-label={`${locale === "hi" ? "बैनर" : "Show banner"} ${index + 1}`} aria-current={index === active ? "true" : undefined} onClick={() => go(index)}><span /></button>)}</div>
            <div className={styles.arrows}><button type="button" aria-label={locale === "hi" ? "पिछला बैनर" : "Previous banner"} disabled={active === 0} onClick={() => go(active - 1)}>←</button><button type="button" aria-label={locale === "hi" ? "अगला बैनर" : "Next banner"} disabled={active === slides.length - 1} onClick={() => go(active + 1)}>→</button></div>
            <span className={styles.srOnly} aria-live="polite">{active + 1} / {slides.length}</span>
        </div>}
    </section>;
}
