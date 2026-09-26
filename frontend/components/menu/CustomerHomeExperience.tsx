"use client";

import Image from "next/image";
import Link from "next/link";
import {useEffect, useState, type ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import FloatingCartButton from "@/components/menu/FloatingCartButton";
import CampaignMedia from "@/components/menu/CampaignMedia";
import {useStorefrontConfiguration, type StorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useCart} from "@/hooks/useCart";
import {getMenu} from "@/services/menuApi";
import {apiClient} from "@/services/apiClient";
import {visibleCampaigns} from "@/lib/campaigns";
import type {MenuCategory} from "@/types/menu";
import type {HomepageCampaign} from "@/types/campaign";
import styles from "./CustomerHomeExperience.module.css";

interface Highlights {trendingProductIds: number[]; newProductIds: number[]}

export default function CustomerHomeExperience({fallback}: {fallback: ReactNode}) {
    const {features, error, retry} = useStorefrontConfiguration();
    if (!features) return <AppShell showSocialPopup={false}>{error ? <div role="alert">
        <p>{error}</p><button className="min-h-11 p-3 underline" onClick={retry}>Retry</button>
        <Link href="/menu" className="min-h-11 p-3 underline">Open menu</Link></div>
        : <p role="status">Loading storefront...</p>}</AppShell>;
    return features.customerHomeV2 ? <Storefront features={features} /> : fallback;
}

function Storefront({features}: {features: StorefrontFeatures}) {
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const [menu, setMenu] = useState<{branchId: number; categories: MenuCategory[]} | null>(null);
    const [highlights, setHighlights] = useState<{branchId: number; data: Highlights} | null>(null);
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [failedMedia, setFailedMedia] = useState<string[]>([]);
    const [failedPhotos, setFailedPhotos] = useState<string[]>([]);
    const [now, setNow] = useState(0);
    const [error, setError] = useState<{branchId: number; message: string} | null>(null);
    const [reload, setReload] = useState(0);
    const categories = menu?.branchId === branch?.id ? menu?.categories ?? [] : [];
    const products = categories.flatMap(category => category.products).filter(product => product.available);
    const ranked = highlights?.branchId === branch?.id ? highlights?.data : null;
    const candidates = features.homepageCampaigns ? visibleCampaigns(campaigns, now)
        .filter(campaign => !failedMedia.includes(`${campaign.id}:${campaign.updatedAt}`)) : [];
    const hero = candidates.find(campaign => campaign.type === "HERO");
    const special = candidates.find(campaign => campaign.type === "FEATURE");
    const photographed = products.filter(product => product.imageUrl && !failedPhotos.includes(product.imageUrl));
    const heroProduct = photographed[0];
    const featuredIds = [...new Set([...(ranked?.trendingProductIds ?? []), ...(ranked?.newProductIds ?? [])])].slice(0, 3);
    const menuError = error?.branchId === branch?.id ? error?.message : null;

    useEffect(() => {
        if (!branch) return;
        const controller = new AbortController();
        getMenu(branch.id, controller.signal).then(categories => {
            if (!controller.signal.aborted) {setMenu({branchId: branch.id, categories}); setError(null);}
        }).catch(error => {if (!controller.signal.aborted) setError({branchId: branch.id, message: error.message});});
        // Optional merchandising never controls whether the live menu loads.
        apiClient<Highlights>(`/api/branches/${branch.id}/storefront-highlights`, {
            signal: AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)])
        }).then(data => {if (!controller.signal.aborted) setHighlights({branchId: branch.id, data});})
            .catch(error => {if (!controller.signal.aborted) console.warn("Optional menu highlights unavailable.", error);});
        return () => controller.abort();
    }, [branch, reload]);

    useEffect(() => {
        if (!features.homepageCampaigns) return;
        const controller = new AbortController();
        async function load() {
            try {
                const data = await apiClient<HomepageCampaign[]>("/api/storefront/campaigns", {
                    signal: AbortSignal.any([controller.signal, AbortSignal.timeout(5000)])
                });
                if (!controller.signal.aborted) {setCampaigns(data); setNow(Date.now());}
            } catch (error) {
                if (!controller.signal.aborted) {
                    console.warn("Optional campaigns unavailable; showing default storefront.", error);
                    setCampaigns([]);
                }
            }
        }
        void load();
        const fetchTimer = window.setInterval(load, 30_000);
        const clockTimer = window.setInterval(() => setNow(Date.now()), 1000);
        return () => {controller.abort(); window.clearInterval(fetchTimer); window.clearInterval(clockTimer);};
    }, [features.homepageCampaigns]);


    function mediaFailure(campaign: HomepageCampaign) {
        console.warn("Campaign media unavailable; selecting the next campaign.", campaign.id);
        setFailedMedia(current => [...current, `${campaign.id}:${campaign.updatedAt}`]);
    }

    return <AppShell showSocialPopup={false}>
        <div className={styles.home}>
        <div className={styles.location}><span className={styles.eyebrow}>Good taste starts here</span><BranchSelector /></div>
        <section className={styles.hero}>
            <div className={styles.heroCopy}>
                <p className={styles.eyebrow}>Gokul Sweets &nbsp; / &nbsp; Made for your cravings</p>
                <h1>{hero?.title ?? <>Sweet moments.<br /><em>Savour every bite.</em></>}</h1>
                <p className={styles.intro}>{hero?.subtitle ?? "From something sweet to a savoury treat. Find your favourites, choose a pickup time, and make a little room for joy."}</p>
                <div className={styles.actions}>
                    <Link href="/menu" className={styles.primary}>Order Now <span aria-hidden="true">&rarr;</span></Link>
                    <Link href={hero?.ctaTarget ?? "/menu"} className={styles.secondary}>{hero?.ctaLabel ?? "Explore Menu"}</Link>
                </div>
                <p className={styles.pickupNote}><span aria-hidden="true" /> {features.contextualStorefrontV2
                    ? `Browsing ${branch?.name ?? "your chosen branch"} · Branch prices and pickup choices confirmed before payment`
                    : `Pickup at ${branch?.name ?? "your chosen branch"} · Order now or plan ahead`}</p>
            </div>
            {hero ? <div className={styles.heroMedia}><CampaignMedia key={`${hero.id}:${hero.updatedAt}`} campaign={hero} hero onUnavailable={() => mediaFailure(hero)} /></div>
                : heroProduct?.imageUrl ? <div className={styles.heroMedia}>
                    <div className={styles.foodPhoto}>
                        <Image src={heroProduct.imageUrl} alt={heroProduct.name} fill sizes="(max-width: 768px) 100vw, 50vw" loading="eager" className="object-cover"
                            onError={() => setFailedPhotos(current => [...current, heroProduct.imageUrl!])} />
                        <div className={styles.photoCaption}><span>On the menu</span><strong>{heroProduct.name}</strong>
                            <Link href={`/menu?category=${heroProduct.categoryId}`}>Take a look &rarr;</Link></div>
                    </div>
                    {photographed[1]?.imageUrl && <div className={styles.insetPhoto}>
                        <Image src={photographed[1].imageUrl} alt={photographed[1].name} fill sizes="150px" className="object-cover"
                            onError={() => setFailedPhotos(current => [...current, photographed[1].imageUrl!])} />
                    </div>}
                </div> : <div className={styles.wordmark} aria-hidden="true"><span>A little</span><strong>sweet.<br />A little<br /><em>savoury.</em></strong><span>A whole lot of Gokul.</span></div>}
        </section>
        <nav aria-label="Ordering steps" className={styles.steps}>
            <span><b>01</b> Find your favourites</span><span><b>02</b> Pick your time</span><span><b>03</b> Collect & enjoy</span>
        </nav>
        <section className={styles.categorySection}>
            <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>Something for every mood</p><h2>What are you craving?</h2></div><Link href="/menu">View menu &rarr;</Link></div>
            {!branch && <p>Choose a branch to see its menu and prices.</p>}
            {branch && menu?.branchId !== branch.id && !menuError && <p role="status">Loading your branch menu...</p>}
            {menuError && <div role="alert"><p>{menuError}</p><button onClick={() => setReload(value => value + 1)} className="min-h-11 underline">Try again</button> · <Link href="/menu" className="underline">Open menu</Link></div>}
            <div className={styles.categories}>{categories.map((category, index) => {
                const photo = photographed.find(product => product.categoryId === category.id);
                return <Link key={category.id} href={`/menu?category=${category.id}`} className={styles.category}>
                    <div className={styles.categoryPhoto}>{photo?.imageUrl
                        ? <Image src={photo.imageUrl} alt={photo.name} fill sizes="(max-width: 640px) 40vw, 200px" className="object-cover"
                            onError={() => setFailedPhotos(current => [...current, photo.imageUrl!])} />
                        : <span aria-hidden="true">{String(index + 1).padStart(2, "0")}</span>}</div>
                    <span>{category.name} <span aria-hidden="true">&rarr;</span></span>
                </Link>;
            })}</div>
            {menu?.branchId === branch?.id && !products.length && <p>No items are currently available at this branch.</p>}
        </section>
        {featuredIds.length > 0 && <section className={styles.categorySection}>
            <div className={styles.sectionHeading}><div><p className={styles.eyebrow}>A little inspiration</p><h2>Worth a taste</h2></div></div>
            <div className={styles.highlights}>{featuredIds.flatMap(id => products.filter(product => product.id === id)).map(product =>
                <Link key={product.id} href={`/menu?category=${product.categoryId}`} className={styles.highlight}>
                    {product.imageUrl && !failedPhotos.includes(product.imageUrl) && <div className={styles.highlightImage}><Image src={product.imageUrl} alt={product.name} fill sizes="(max-width: 767px) 96px, 33vw" className="object-cover"
                        onError={() => setFailedPhotos(current => [...current, product.imageUrl!])} /></div>}
                    <div className="p-5"><p className={styles.eyebrow}>{ranked?.trendingProductIds.includes(product.id) ? "Branch favourite" : "New to the menu"}</p>
                        <h3 className="mt-2 text-xl font-bold">{product.name}</h3><span className="mt-3 inline-block text-sm">Discover on the menu &rarr;</span></div>
                </Link>)}</div>
        </section>}
        <section className={styles.later}>
            <div><p className={styles.eyebrow}>Good things are worth planning</p><h2>Your next sweet occasion,<br /><em>sorted.</em></h2><p>Choose your favourites. We&apos;ll show pickup dates that fit your whole cart.</p></div>
            <Link href={cart.isEmpty ? "/menu" : "/checkout/pickup"} className={styles.primary}>Order for later <span aria-hidden="true">&rarr;</span></Link>
        </section>
        {special && <section className="mb-8 grid items-center gap-5 rounded-3xl border border-[#eadfd6] bg-white p-5 md:grid-cols-2">
            <CampaignMedia key={`${special.id}:${special.updatedAt}`} campaign={special} onUnavailable={() => mediaFailure(special)} />
            <div><p className="text-xs font-bold uppercase text-[#c88a20]">Gokul specials</p><h2 className="mt-2 text-2xl font-bold">{special.title}</h2>
                {special.subtitle && <p className="mt-2 text-sm text-[#756763]">{special.subtitle}</p>}
                {special.ctaTarget && <Link href={special.ctaTarget} className="mt-4 inline-flex min-h-12 items-center rounded-xl bg-[#7a1625] px-5 font-bold text-white!">{special.ctaLabel}</Link>}
            </div>
        </section>}
        </div>
        <div className={cart.itemCount ? "h-20" : ""} />
        <FloatingCartButton itemCount={cart.itemCount} total={cart.subtotal} />
    </AppShell>;
}
