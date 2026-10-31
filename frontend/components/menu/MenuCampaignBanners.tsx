/* eslint-disable @next/next/no-img-element -- Managed media uses the saved crop and public URL. */
"use client";
import {useCarouselAutoplay} from "@/hooks/useCarouselAutoplay";
import {useEffect, useLayoutEffect, useRef, useState} from "react";
import {useLanguage, translate} from "@/lib/language";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {campaignFrameStyle} from "@/lib/campaignFraming";
import type {MenuBanner} from "@/components/admin/WorkspaceAppearance";
import styles from "./BranchMenuAppearance.module.css";

type Category = {id: number; name: string};
type Props = {banners: MenuBanner[]; categories: Category[]; onSelect: (id: number) => void; compact?: boolean;position?:number;onPositionChange?:(index:number)=>void};
type Slide = {banner: MenuBanner; category: Category | undefined; generated: boolean; key: string};

/** Published artwork and copy remain authoritative; a single campaign can introduce live categories. */
export default function MenuCampaignBanners({banners, categories, onSelect, compact = false,position=0,onPositionChange}: Props) {
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
        {slide.banner.mediaUrl && (slide.banner.mediaType?.startsWith("video/") && !reduced && playing
            ? <CampaignVideo key={slide.banner.mediaUrl} banner={slide.banner} modern={!!modern} locale={locale} />
            : <div className={styles.frame}><img key={slide.banner.mediaUrl} src={slide.banner.mediaType?.startsWith("video/") ? (slide.banner.posterUrl ?? "") : slide.banner.mediaUrl} alt="" loading="lazy" draggable={false} style={campaignFrameStyle(slide.banner.frame)} onError={e => {e.currentTarget.hidden = true;}} /></div>)}
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
    return <CampaignCarousel key={slides.map(s => s.key).join("|")} position={position} onPositionChange={onPositionChange} slides={slides} reduced={reduced} locale={locale} compact={compact} render={content} />;
}

/** Background animation controls sit outside saved media transforms and the slide navigation. */
function CampaignVideo({banner, modern, locale}: {banner: MenuBanner; modern: boolean; locale: "en" | "hi"}) {
    const video = useRef<HTMLVideoElement>(null);
    const [paused, setPaused] = useState(true);
    const [failed, setFailed] = useState(false);
    const label = paused
        ? locale === "hi" ? "बैनर ऐनिमेशन चलाएँ" : "Play banner animation"
        : locale === "hi" ? "बैनर ऐनिमेशन रोकें" : "Pause banner animation";
    return <>
        <div className={styles.frame}><video ref={video} src={banner.mediaUrl!} poster={banner.posterUrl ?? undefined} muted autoPlay loop playsInline controls={!modern}
            onPlay={() => setPaused(false)} onPause={() => setPaused(true)} onError={() => setFailed(true)} style={campaignFrameStyle(banner.frame)} /></div>
        {modern && !failed && <button type="button" className={styles.videoToggle} aria-label={label} title={label} onClick={() => {
            const media = video.current;
            if (!media) return;
            if (media.paused) void media.play().catch(() => { /* A blocked play remains available for a later user gesture. */ });
            else media.pause();
        }}><svg viewBox="0 0 24 24" width="18" height="18" fill="currentColor" aria-hidden="true">{paused ? <path d="m8 5 11 7-11 7z" /> : <path d="M6 5h4v14H6zm8 0h4v14h-4z" />}</svg></button>}
    </>;
}

function CampaignCarousel({slides, reduced, locale, compact, render,position,onPositionChange}: {
    position:number;onPositionChange?:(index:number)=>void;
    slides: Slide[]; reduced: boolean; locale: "en" | "hi"; compact: boolean; render: (slide: Slide, playing: boolean) => React.ReactNode;
}) {
    const viewport = useRef<HTMLDivElement>(null);
    const [initialPosition]=useState(()=>Math.max(0,Math.min(slides.length-1,position)));
    const [active, setActive] = useState(initialPosition);
    const [paused,setPaused]=useState(false);
    // Category filtering can remount this discovery slot. Restore the branch/content position
    // before paint, without replaying an entrance scroll or resetting the selected slide.
    useLayoutEffect(()=>{
        const node=viewport.current,target=node?.children[initialPosition] as HTMLElement|undefined,first=node?.children[0] as HTMLElement|undefined;
        if(node&&target&&first)node.scrollTo({left:target.offsetLeft-first.offsetLeft,behavior:"instant"});
    },[initialPosition]);
    const pendingTarget = useRef<number | null>(null);
    const go = (index: number) => {
        index = Math.max(0, Math.min(slides.length - 1, index));
        const target = viewport.current?.children[index] as HTMLElement | undefined;
        pendingTarget.current = index;
        if (target && viewport.current) viewport.current.scrollTo({left: target.offsetLeft - (viewport.current.children[0] as HTMLElement).offsetLeft, behavior: reduced ? "instant" : "smooth"});
    };
    useCarouselAutoplay(viewport,slides.length,()=>go((active+1)%slides.length),paused||reduced);
    return <section className={`${styles.carousel} ${compact ? styles.compactCarousel : ""}`} aria-label="Published menu banners" aria-roledescription={locale === "hi" ? "कैरसेल" : "carousel"}>
        <div ref={viewport} className={styles.viewport} tabIndex={slides.length > 1 ? 0 : undefined} aria-label={locale === "hi" ? "श्रेणियाँ देखने के लिए स्वाइप करें" : "Swipe to explore categories"}
            onKeyDown={event => {if (event.target !== event.currentTarget) return; if (event.key === "ArrowRight") {event.preventDefault(); go(Math.min((pendingTarget.current ?? active) + 1, slides.length - 1));} if (event.key === "ArrowLeft") {event.preventDefault(); go(Math.max((pendingTarget.current ?? active) - 1, 0));}}}
            onPointerDown={() => {pendingTarget.current = null;}} onWheel={() => {pendingTarget.current = null;}}
            onScroll={event => {
                const node = event.currentTarget;
                if (pendingTarget.current !== null) {
                    const target = node.children[pendingTarget.current] as HTMLElement | undefined;
                    const first = node.children[0] as HTMLElement | undefined;
                    if (target && first && Math.abs(node.scrollLeft - (target.offsetLeft - first.offsetLeft)) >= 2) return;
                    pendingTarget.current = null;
                }
                const index = Math.round(node.scrollLeft / Math.max(node.clientWidth, 1));
                const next=Math.max(0, Math.min(slides.length - 1, index));setActive(next);onPositionChange?.(next);
            }}>
            {slides.map((slide, index) => <article key={slide.key} className={`${styles.banner} ${styles.slide}`} role="group" aria-roledescription={locale === "hi" ? "स्लाइड" : "slide"} aria-label={`${index + 1} / ${slides.length}`} aria-hidden={index !== active} inert={index !== active}>{render(slide, index === active)}</article>)}
        </div>
        {slides.length > 1 && <div className={styles.carouselNavigation}>
            <span className={styles.hint}>{locale === "hi" ? "हर खुशी का स्वाद" : "A little more to celebrate"}</span>
            <div className={styles.dots} aria-label={locale === "hi" ? "बैनर चुनें" : "Choose a banner"}>{slides.map((slide,index) => <button key={slide.key} type="button" aria-label={`${locale === "hi" ? "बैनर" : "Show banner"} ${index + 1}`} aria-current={index === active ? "true" : undefined} onClick={() => go(index)}><span /></button>)}</div>
            <button className={styles.autoToggle} type="button" aria-label={paused?(locale==="hi"?"बैनर अपने आप बदलें":"Resume banner carousel"):(locale==="hi"?"बैनर बदलना रोकें":"Pause banner carousel")} onClick={()=>setPaused(p=>!p)}>{paused?"▶":"Ⅱ"}</button>
            <div className={styles.arrows}><button type="button" aria-label={locale === "hi" ? "पिछला बैनर" : "Previous banner"} disabled={active === 0} onClick={() => go((pendingTarget.current ?? active) - 1)}>←</button><button type="button" aria-label={locale === "hi" ? "अगला बैनर" : "Next banner"} disabled={active === slides.length - 1} onClick={() => go((pendingTarget.current ?? active) + 1)}>→</button></div>
            <span className={styles.srOnly} aria-live={paused||reduced?"polite":"off"}>{active + 1} / {slides.length}</span>
        </div>}
    </section>;
}
