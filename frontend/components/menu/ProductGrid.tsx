"use client";
import {Fragment} from "react";
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
    portionGroups?: PortionGroup[];
    catalogProducts?: MenuProduct[];
    refined?: boolean;
    pickupItems?: ItemAvailability[];
    pickupChecking?: boolean;
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
    portionGroups,
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
    dateAware
}: ProductGridProps) {

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


    return (
        <div className={dateAware ? styles.products : undefined}>
            <div className={`grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 ${refined ? "gokul-menu-product-grid" : ""}`}>
                {mobileMenuRows(products,portionGroups??[],catalogProducts??products).map(row => {
                    const product=row.product;
                    if(row.group&&row.products)return <Fragment key={row.group.key}><div id={`gokul-product-${product.id}`}><MobilePortionCard group={row.group} products={row.products} quantities={quantities} onAdd={onAdd} onIncrease={onIncrease} onDecrease={onDecrease} ratings={ratingSummaries} loading={ratingsLoading} pickupItems={pickupItems} dateAware={dateAware}/></div>{pairing&&row.products.some(p=>p.id===pairingSeed)&&<div className="menu-grid-pairing">{pairing}</div>}</Fragment>;
                    const pickup = pickupItems?.find(item => item.productId === product.id);
                    const unavailable = dateAware && pickup?.available === false;
                    return (
                        <Fragment key={product.id}><div id={`gokul-product-${product.id}`} className="relative min-w-0 scroll-mt-24">
                            {dateAware && (pickup || pickupChecking) && !(refined && unavailable) && (
                                <span className={`menu-availability-chip ${unavailable ? "menu-availability-chip--unavailable" : pickupChecking && !pickup ? "menu-availability-chip--checking" : ""}`}>
                                    <span aria-hidden="true">{unavailable ? "!" : pickupChecking && !pickup ? "◌" : "✓"}</span>
                                    {unavailable ? "Date unavailable" : pickupChecking && !pickup ? "Checking date" : "Date preview"}
                                </span>
                            )}
                            <ProductCard
                                refined={refined}
                                unavailableForPickup={refined && unavailable}
                                product={product}
                                ratingSummary={ratingSummaries[product.id] ?? null}
                                ratingLoading={ratingsLoading}
                                quantity={quantities[product.id] ?? 0}
                                weightGrams={weights[product.id] ?? null}
                                onIncrease={onIncrease}
                                onDecrease={onDecrease}
                                onAdd={onAdd}
                            />
                            {unavailable && (!refined || pickup?.code === "QUANTITY_TOO_LARGE") && (
                                <p role="status" className="menu-availability-note">
                                    {describePickupAvailability(pickup, false)}
                                </p>
                            )}
                        </div>{pairing&&product.id===pairingSeed&&<div className="menu-grid-pairing">{pairing}</div>}</Fragment>
                    );
                })}
            </div>
        </div>
    );
}
