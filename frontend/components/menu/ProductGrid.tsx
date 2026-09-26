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
        <div
            className="
                grid
                grid-cols-1
                gap-3

                sm:grid-cols-2
                sm:gap-4

                lg:grid-cols-3
            "
        >

            {products.map(
                product => (

                    <div key={product.id}>
                    {dateAware && <p role="status" className={`mb-2 rounded-xl px-3 py-2 text-xs ${pickupItems?.find(item => item.productId === product.id)?.available ? "bg-green-50 text-green-900" : "bg-[#fff0dc] text-[#754321]"}`}>
                        {refined
                            ? describePickupAvailability(pickupItems?.find(item => item.productId === product.id), !!pickupChecking)
                            : (() => {
                            const item = pickupItems?.find(value => value.productId === product.id);
                            if (!item) return pickupChecking ? "Pickup check pending - not confirmed" : "Choose pickup to check availability";
                            if (!item.available) return `${item.reason}${item.code === "QUANTITY_TOO_LARGE" ? ` Up to ${item.availableQuantity} ${item.unit === "GRAM" ? "g" : "pieces"} remain.` : ""}`;
                            return `Pickup options available${item.availableQuantity == null ? "" : ` - up to ${item.availableQuantity} ${item.unit === "GRAM" ? "g" : "pieces"} now`}. Final cart checked at checkout.`;
                        })()}
                    </p>}
                    <ProductCard
                        refined={refined}
                        unavailableForPickup={refined && dateAware && pickupItems?.some(item => item.productId === product.id && !item.available) === true}
                        product={
                            product
                        }
                        ratingSummary={
                            ratingSummaries[
                                product.id
                            ]
                            ?? null
                        }
                        ratingLoading={
                            ratingsLoading
                        }
                        quantity={
                            quantities[
                                product.id
                            ]
                            ?? 0
                        }
                        weightGrams={
                            weights[
                                product.id
                            ]
                            ?? null
                        }
                        onIncrease={
                            onIncrease
                        }
                        onDecrease={
                            onDecrease
                        }
                        onAdd={
                            onAdd
                        }
                    />
                    </div>

                )
            )}

        </div>
        </div>
    );
}
