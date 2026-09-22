"use client";

import ProductCard
    from "@/components/menu/ProductCard";

import type {
    MenuProduct
} from "@/types/menu";

import type {
    ProductRatingSummary
} from "@/types/review";


interface ProductGridProps {

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
    products,
    ratingSummaries,
    ratingsLoading,
    quantities,
    weights,
    onIncrease,
    onDecrease,
    onAdd
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

                    <ProductCard
                        key={
                            product.id
                        }
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

                )
            )}

        </div>
    );
}
