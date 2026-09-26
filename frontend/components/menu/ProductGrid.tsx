"use client";

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
                    No items found
                </h3>

                <p
                    className="
                        mt-2
                        text-sm
                        text-[#756763]
                    "
                >
                    Try another category or search term.
                </p>

            </div>
        );
    }


    return (
        <div className={dateAware ? styles.products : undefined}>
            <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4">
                {products.map(product => {
                    const pickup = pickupItems?.find(item => item.productId === product.id);
                    const unavailable = dateAware && pickup?.available === false;
                    return (
                        <div key={product.id} className="relative min-w-0">
                            {dateAware && (pickup || pickupChecking) && (
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
                            {unavailable && (
                                <p role="status" className="menu-availability-note">
                                    {describePickupAvailability(pickup, false)}
                                </p>
                            )}
                        </div>
                    );
                })}
            </div>
        </div>
    );
}
