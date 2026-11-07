"use client";
import {Fragment,useMemo} from "react";
import type {ReactNode} from "react";
import {T} from "@/lib/language";

import MobilePortionCard from "./MobilePortionCard";
import {mobileMenuRows,type PortionGroup} from "@/lib/mobileMenu";
import ProductCard
    from "@/components/menu/ProductCard";
import type {ItemAvailability} from "@/services/availabilityApi";
import styles from "./CustomerHomeExperience.module.css";
import {describePickupAvailability} from "@/lib/storefrontPresentation";

import type {
    MenuProduct
} from "@/types/menu";

import type {
    ProductRatingSummary
} from "@/types/review";


interface ProductGridProps {
    pairingSeed?:number;
    pairing?:ReactNode;
    afterLeadingRows?:ReactNode;
    portionGroups?: PortionGroup[];
    compactGroups?: boolean;
    catalogProducts?: MenuProduct[];
    refined?: boolean;
    pickupItems?: ItemAvailability[];
    pickupChecking?: boolean;
    pickupStatusMessage?: string;
    dateAware?: boolean;

    products:
        MenuProduct[];

    ratingSummaries:
        Record<number, ProductRatingSummary>;

    ratingsLoading:
        boolean;

    quantities:
        Record<number, number>;

    weights:
        Record<number, number>;

    onIncrease:
        (productId: number) => void;

    onDecrease:
        (productId: number) => void;

    onAdd:
        (product: MenuProduct) => void;
}


export default function ProductGrid({
    pairingSeed,
    pairing,
    afterLeadingRows,
    portionGroups,
    compactGroups=true,
    catalogProducts,
    refined = false,
    products,
    ratingSummaries,
    ratingsLoading,
    quantities,
    weights,
    onIncrease,
    onDecrease,
    onAdd,
    pickupItems,
    pickupChecking,
    pickupStatusMessage,
    dateAware
}: ProductGridProps) {

    const pickupById=useMemo(()=>new Map(pickupItems?.map(item=>[item.productId,item])),[pickupItems]);
    if (
        products.length === 0
    ) {

        return (
            <div
                className="
                    rounded-3xl
                    border
                    border-dashed
                    border-[#eadfd6]
                    bg-white
                    px-6
                    py-12
                    text-center
                "
            >

                <div
                    className="
                        text-4xl
                    "
                >
                    🍽️
                </div>

                <h3
                    className="
                        mt-4
                        text-lg
                        font-bold
                    "
                >
                    <T text="No items found" /></h3>

                <p
                    className="
                        mt-2
                        text-sm
                        text-[#756763]
                    "
                >
                    <T text="Try another category or search term." /></p>

            </div>
        );
    }


    // A shelf can move between rows without moving a card to a different React parent.
    // Keep every card and the shelf in the same keyed list so open sheets survive reordering.
    const rows = mobileMenuRows(products,portionGroups??[],catalogProducts??products).map(row => {
        const product=row.product;
        if(row.group&&row.products)return <Fragment key={row.group.key}><div id={`gokul-product-${product.id}`} data-menu-product-card tabIndex={-1} className="relative min-w-0 scroll-mt-24">
            {/* Every size remains a link destination at the shared card's position. */}
            {Array.from(new Set(row.products.map(option=>option.id))).filter(id=>id!==product.id).map(id=><span key={id} id={`gokul-product-${id}`} aria-hidden="true" className="pointer-events-none absolute left-0 top-0 h-px w-px scroll-mt-24"/>)}
            <MobilePortionCard compact={compactGroups} group={row.group} products={row.products} quantities={quantities} onAdd={onAdd} onIncrease={onIncrease} onDecrease={onDecrease} ratings={ratingSummaries} loading={ratingsLoading} pickupItems={pickupItems} dateAware={dateAware} pickupChecking={pickupChecking} pickupStatusMessage={pickupStatusMessage}/></div>{pairing&&row.products.some(p=>p.id===pairingSeed)&&<div className="menu-grid-pairing">{pairing}</div>}</Fragment>;
        const pickup = pickupById.get(product.id);
        const unavailable = !!dateAware && (pickupChecking || pickup?.available !== true);
        return (
            <Fragment key={product.id}><div id={`gokul-product-${product.id}`} data-menu-product-card tabIndex={-1} className="relative min-w-0 scroll-mt-24">
                {dateAware && !pickupChecking && pickup && !(refined && unavailable) && (
                    <span className={`menu-availability-chip ${unavailable ? "menu-availability-chip--unavailable" : pickupChecking && !pickup ? "menu-availability-chip--checking" : ""}`}>
                        <span aria-hidden="true">{unavailable ? "!" : pickupChecking && !pickup ? "◌" : "✓"}</span>
                        {unavailable ? "Date unavailable" : pickupChecking && !pickup ? "Checking date" : "Date preview"}
                    </span>
                )}
                <ProductCard
                    premium={portionGroups!==undefined&&compactGroups}
                    refined={refined}
                    unavailableForPickup={unavailable}
                    pickupMessage={pickupChecking ? pickupStatusMessage ?? "Checking pickup" : describePickupAvailability(pickup, false)}
                    pickupChecking={pickupChecking}
                    product={product}
                    ratingSummary={ratingSummaries[product.id] ?? null}
                    ratingLoading={ratingsLoading}
                    quantity={quantities[product.id] ?? 0}
                    weightGrams={weights[product.id] ?? null}
                    onIncrease={onIncrease}
                    onDecrease={onDecrease}
                    onAdd={onAdd}
                />
                {unavailable && !refined && (!pickupChecking||pickupStatusMessage) && (
                    <p role="status" className="menu-availability-note">
                        <T text={pickupChecking ? pickupStatusMessage ?? "Checking pickup" : describePickupAvailability(pickup, false)}/>{!pickupChecking&&pickup?.available===false&&<button type="button" className="pickup-attention-action" onClick={()=>window.dispatchEvent(new Event("gokul-pickup-attention"))}><T text="Change pickup date"/></button>}
                    </p>
                )}
            </div>{pairing&&product.id===pairingSeed&&<div className="menu-grid-pairing">{pairing}</div>}</Fragment>
        );
    });
    if(afterLeadingRows)rows.splice(4,0,<Fragment key="menu-meal-shelf"><div className="col-span-full min-w-0">{afterLeadingRows}</div></Fragment>);
    return (
        <div className={dateAware ? styles.products : undefined}>
            <div className={`grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 ${refined ? "gokul-menu-product-grid" : ""}`}>
                {rows}
            </div>
        </div>
    );
}
