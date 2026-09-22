"use client";

import Image from "next/image";
import Link from "next/link";
import {useEffect, useState, type ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import ProductGrid from "@/components/menu/ProductGrid";
import WeightSelectorSheet from "@/components/menu/WeightSelectorSheet";
import FloatingCartButton from "@/components/menu/FloatingCartButton";
import CampaignMedia from "@/components/menu/CampaignMedia";
import {useStorefrontFeatures, type StorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useCart} from "@/hooks/useCart";
import {getMenu} from "@/services/menuApi";
import {apiClient} from "@/services/apiClient";
import {visibleCampaigns} from "@/lib/campaigns";
import type {MenuCategory, MenuProduct} from "@/types/menu";
import type {HomepageCampaign} from "@/types/campaign";
import styles from "./CustomerHomeExperience.module.css";

interface Highlights {trendingProductIds: number[]; newProductIds: number[]}

export default function CustomerHomeExperience({fallback}: {fallback: ReactNode}) {
    const features = useStorefrontFeatures();
    return features?.customerHomeV2 ? <Storefront features={features} /> : fallback;
}

function Storefront({features}: {features: StorefrontFeatures}) {
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const [menu, setMenu] = useState<{branchId: number; categories: MenuCategory[]} | null>(null);
    const [highlights, setHighlights] = useState<{branchId: number; data: Highlights} | null>(null);
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [failedMedia, setFailedMedia] = useState<string[]>([]);
    const [now, setNow] = useState(0);
    const [error, setError] = useState<{branchId: number; message: string} | null>(null);
    const [reload, setReload] = useState(0);
    const [categoryId, setCategoryId] = useState<number | null>(null);
    const [weightProduct, setWeightProduct] = useState<MenuProduct | null>(null);
    const [notice, setNotice] = useState("");
    const categories = menu?.branchId === branch?.id ? menu?.categories ?? [] : [];
    const products = categories.flatMap(category => category.products).filter(product => product.available);
    const ranked = highlights?.branchId === branch?.id ? highlights?.data : null;
    const candidates = features.homepageCampaigns ? visibleCampaigns(campaigns, now)
        .filter(campaign => !failedMedia.includes(`${campaign.id}:${campaign.updatedAt}`)) : [];
    const hero = candidates.find(campaign => campaign.type === "HERO");
    const special = candidates.find(campaign => campaign.type === "FEATURE");
    const heroProduct = products.find(product => product.imageUrl);
    const activeCategoryId = categories.some(category => category.id === categoryId) ? categoryId : null;
    const chosenProducts = activeCategoryId == null ? products.slice(0, 6) : products.filter(product => product.categoryId === activeCategoryId);
    const quantities = Object.fromEntries(cart.items.map(item => [item.product.id, item.quantity]));
    const weights = Object.fromEntries(cart.items.filter(item => item.weightGrams != null).map(item => [item.product.id, item.weightGrams!]));
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

    function add(product: MenuProduct, weight?: number) {
        if (!branch || !product.available) return;
        if (cart.branchId !== null && cart.branchId !== branch.id && cart.items.length) {
            setNotice("Your cart is saved for another branch. Review it before adding items here.");
            return;
        }
        if (product.saleMode === "WEIGHT" && weight == null) {setWeightProduct(product); return;}
        const result = cart.addItem(product, branch.id, weight);
        if (result === "added") {setWeightProduct(null); setNotice(`${product.name} added to your cart.`);}
        else setNotice("Please review the branch and quantity before adding this item.");
    }

    function grid(items: MenuProduct[]) {
        return <div className={styles.products}><ProductGrid products={items} ratingSummaries={{}} ratingsLoading={false}
            quantities={cart.branchId === branch?.id ? quantities : {}} weights={cart.branchId === branch?.id ? weights : {}}
            onAdd={add} onIncrease={cart.increaseQuantity} onDecrease={cart.decreaseQuantity} /></div>;
    }

    function mediaFailure(campaign: HomepageCampaign) {
        console.warn("Campaign media unavailable; selecting the next campaign.", campaign.id);
        setFailedMedia(current => [...current, `${campaign.id}:${campaign.updatedAt}`]);
    }

    return <AppShell showSocialPopup={false}>
        <BranchSelector />
        <section className="mt-5 grid items-center gap-6 rounded-3xl bg-[#5d0f1b] p-6 text-white sm:p-8 md:grid-cols-2">
            <div>
                <p className="text-xs font-bold uppercase tracking-widest text-[#f6dfad]">Fresh favourites. Easy pickup.</p>
                <h1 className="mt-3 text-4xl font-bold leading-tight">{hero?.title ?? "A little sweetness, ready for you."}</h1>
                <p className="mt-3 text-sm text-white/80">{hero?.subtitle ?? "Choose your branch, add your favourites and pick a time."}</p>
                <div className="mt-5 flex flex-wrap gap-3">
                    <a href="#shop-menu" className="inline-flex min-h-12 items-center rounded-xl bg-[#f6dfad] px-5 font-bold text-[#5d0f1b]!">Order Now</a>
                    <Link href={hero?.ctaTarget ?? "/menu"} className="inline-flex min-h-12 items-center rounded-xl border border-white/40 px-5 font-bold text-white!">{hero?.ctaLabel ?? "Explore Menu"}</Link>
                </div>
            </div>
            {hero ? <CampaignMedia key={`${hero.id}:${hero.updatedAt}`} campaign={hero} hero onUnavailable={() => mediaFailure(hero)} />
                : heroProduct?.imageUrl ? <div className="relative aspect-[4/3] overflow-hidden rounded-2xl">
                    <Image src={heroProduct.imageUrl} alt={heroProduct.name} fill sizes="(max-width: 768px) 100vw, 50vw" loading="eager" className="object-cover" />
                </div> : <div className="rounded-2xl border border-white/20 p-8 text-2xl font-semibold text-[#f6dfad]">Made for your moments.</div>}
        </section>
        <nav aria-label="Ordering steps" className="my-5 grid grid-cols-3 gap-2 text-center text-xs font-semibold text-[#756763]">
            <span>1. Branch & favourites</span><span>2. Pickup time</span><span>3. Review & pay</span>
        </nav>
        <section id="shop-menu" className="scroll-mt-20 space-y-4">
            <div className="flex items-center justify-between gap-3"><h2 className="text-2xl font-bold text-[#7a1625]">Explore the menu</h2><Link href="/menu" className="min-h-11 py-3 text-sm underline">Full menu</Link></div>
            {!branch && <p>Choose a branch to see its menu and prices.</p>}
            {branch && menu?.branchId !== branch.id && !menuError && <p role="status">Loading your branch menu...</p>}
            {menuError && <div role="alert"><p>{menuError}</p><button onClick={() => setReload(value => value + 1)} className="min-h-11 underline">Try again</button> · <Link href="/menu" className="underline">Open menu</Link></div>}
            {categories.length > 0 && <>
                <div className="flex gap-2 overflow-x-auto pb-2" aria-label="Menu categories">
                    <button onClick={() => setCategoryId(null)} aria-pressed={activeCategoryId === null} className="min-h-12 shrink-0 rounded-xl border bg-white px-4 font-semibold">All favourites</button>
                    {categories.map(category => <button key={category.id} onClick={() => setCategoryId(category.id)} aria-pressed={activeCategoryId === category.id}
                        className={`min-h-12 shrink-0 rounded-xl border px-4 font-semibold ${activeCategoryId === category.id ? "border-[#7a1625] bg-[#fff1e9]" : "bg-white"}`}>{category.name}</button>)}
                </div>
                {grid(chosenProducts)}
            </>}
            {menu?.branchId === branch?.id && !products.length && <p>No items are currently available at this branch.</p>}
        </section>
        {ranked && ranked.trendingProductIds.some(id => products.some(product => product.id === id)) && <section className="mt-8 space-y-4">
            <h2 className="text-2xl font-bold text-[#7a1625]">Trending now</h2>
            <p className="text-sm text-[#756763]">Popular in completed orders at this branch. Pickup options depend on your final cart.</p>
            {grid(ranked.trendingProductIds.flatMap(id => products.filter(product => product.id === id)))}
        </section>}
        {ranked && ranked.newProductIds.some(id => products.some(product => product.id === id)) && <section className="mt-8 space-y-4">
            <h2 className="text-2xl font-bold text-[#7a1625]">New to the menu</h2>
            {grid(ranked.newProductIds.flatMap(id => products.filter(product => product.id === id)))}
        </section>}
        <section className="my-8 flex flex-wrap items-center justify-between gap-4 rounded-2xl bg-[#fff0dc] p-6">
            <div><h2 className="text-xl font-bold text-[#7a1625]">Planning for later?</h2><p className="mt-1 text-sm">Build your cart, then choose a future pickup date that works for your branch.</p></div>
            <Link href={cart.isEmpty ? "/menu" : "/checkout/pickup"} className="inline-flex min-h-12 items-center rounded-xl bg-[#7a1625] px-5 font-bold text-white!">Order for later</Link>
        </section>
        {special && <section className="mb-8 grid items-center gap-5 rounded-3xl border border-[#eadfd6] bg-white p-5 md:grid-cols-2">
            <CampaignMedia key={`${special.id}:${special.updatedAt}`} campaign={special} onUnavailable={() => mediaFailure(special)} />
            <div><p className="text-xs font-bold uppercase text-[#c88a20]">Gokul specials</p><h2 className="mt-2 text-2xl font-bold">{special.title}</h2>
                {special.subtitle && <p className="mt-2 text-sm text-[#756763]">{special.subtitle}</p>}
                {special.ctaTarget && <Link href={special.ctaTarget} className="mt-4 inline-flex min-h-12 items-center rounded-xl bg-[#7a1625] px-5 font-bold text-white!">{special.ctaLabel}</Link>}
            </div>
        </section>}
        {notice && <p role="status" className="my-4 rounded-xl border border-[#eadfd6] bg-white p-4 text-sm">{notice} <Link href="/cart" className="underline">View cart</Link></p>}
        <div className={cart.itemCount ? "h-20" : ""} />
        <FloatingCartButton itemCount={cart.itemCount} total={cart.subtotal} />
        <WeightSelectorSheet product={weightProduct} currentWeightGrams={weightProduct ? weights[weightProduct.id] : null}
            onClose={() => setWeightProduct(null)} onConfirm={add} />
    </AppShell>;
}
