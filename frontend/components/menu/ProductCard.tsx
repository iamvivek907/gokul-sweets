"use client";

import Image from "next/image";

import type { MenuProduct } from "@/types/menu";
import type { ProductRatingSummary } from "@/types/review";


interface ProductCardProps {
    product: MenuProduct;
    ratingSummary: ProductRatingSummary | null;
    ratingLoading: boolean;
    quantity: number;
    weightGrams: number | null;
    onIncrease: (productId: number) => void;
    onDecrease: (productId: number) => void;
    onAdd: (product: MenuProduct) => void;
}


function formatCurrency(amount: number): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 0
        }
    ).format(amount);
}


function formatWeight(
    weightGrams: number
): string {

    if (
        weightGrams >= 1000
    ) {

        return `${new Intl.NumberFormat(
            "en-IN",
            {
                maximumFractionDigits: 3
            }
        ).format(
            weightGrams / 1000
        )} kg`;
    }


    return `${weightGrams} g`;
}


export default function ProductCard({
    product,
    ratingSummary,
    ratingLoading,
    quantity,
    weightGrams,
    onIncrease,
    onDecrease,
    onAdd
}: ProductCardProps) {

    const isAvailable =
        product.available !== false;

    const isWeighted =
        product.saleMode === "WEIGHT";

    const isInCart =
        isWeighted
            ? weightGrams !== null
            : quantity > 0;

    const selectionLabel =
        isWeighted
            ? formatWeight(
                weightGrams
                ?? product.minimumWeightGrams
                ?? 250
            )
            : String(quantity);


    return (
        <article
            className="
                group
                flex
                min-h-32
                overflow-hidden
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                shadow-[0_4px_16px_rgba(60,30,20,0.07)]
                transition

                hover:-translate-y-0.5
                hover:shadow-[0_10px_26px_rgba(60,30,20,0.11)]

                sm:flex-col
            "
        >

            {/* ============================================================ */}
            {/* Product Image                                                 */}
            {/* ============================================================ */}

            <div
                className="
                    relative
                    h-32
                    w-28
                    shrink-0
                    overflow-hidden
                    bg-[#fff0dc]

                    sm:h-auto
                    sm:aspect-[4/3]
                    sm:w-full
                "
            >

                {
                    product.imageUrl
                        ? (
                            <Image
                                src={
                                    product.imageUrl
                                }
                                alt={
                                    product.name
                                }
                                fill
                                sizes="
                                    (max-width: 639px) 112px,
                                    (max-width: 768px) 45vw,
                                    (max-width: 1024px) 30vw,
                                    280px
                                "
                                className="
                                    object-cover
                                    transition
                                    duration-300
                                    group-hover:scale-105
                                "
                                draggable={false}
                            />
                        )
                        : (
                            <div className="
                                flex
                                h-full
                                items-center
                                justify-center
                                text-3xl
                                sm:text-4xl
                            ">
                                🍬
                            </div>
                        )
                }


                {
                    !isAvailable
                    && (
                        <div className="
                            absolute
                            inset-0
                            z-10
                            flex
                            items-center
                            justify-center
                            bg-black/45
                            p-2
                        ">
                            <span className="
                                rounded-full
                                bg-white
                                px-2.5
                                py-1
                                text-center
                                text-[10px]
                                font-bold
                                text-[#5d0f1b]
                            ">
                                Unavailable
                            </span>
                        </div>
                    )
                }

            </div>


            {/* ============================================================ */}
            {/* Product Details                                               */}
            {/* ============================================================ */}

            <div className="
                flex
                min-w-0
                flex-1
                flex-col
                p-3

                sm:p-3.5
            ">

                <p className="
                    truncate
                    text-[9px]
                    font-semibold
                    uppercase
                    tracking-[0.1em]
                    text-[#c88a20]
                ">
                    {
                        product.categoryName
                    }
                </p>


                <h3 className="
                    mt-1
                    line-clamp-2
                    text-[15px]
                    font-bold
                    leading-snug
                    text-[#241715]

                    sm:text-base
                ">
                    {
                        product.name
                    }
                </h3>


                {
                    product.description
                    && (
                        <p className="
                            mt-1
                            line-clamp-2
                            text-[11px]
                            leading-4
                            text-[#756763]

                            sm:text-xs
                            sm:leading-5
                        ">
                            {
                                product.description
                            }
                        </p>
                    )
                }


                {/* Rating */}

                <div className="mt-1.5 flex min-h-5 items-center">

                    {
                        ratingLoading
                            ? (
                                <div
                                    aria-label="Loading product rating"
                                    className="
                                        h-3
                                        w-24
                                        animate-pulse
                                        rounded-full
                                        bg-[#f1e4d8]
                                    "
                                />
                            )
                            : ratingSummary
                              &&
                              ratingSummary.ratingCount > 0
                                ? (
                                    <div
                                        aria-label={`${ratingSummary.averageRating.toFixed(1)} out of 5 from ${ratingSummary.ratingCount} ratings`}
                                        className="
                                            flex
                                            items-center
                                            gap-1
                                        "
                                    >
                                        <span
                                            aria-hidden="true"
                                            className="
                                                text-sm
                                                leading-none
                                                text-[#d18b13]
                                            "
                                        >
                                            ★
                                        </span>

                                        <span className="
                                            text-[11px]
                                            font-bold
                                            text-[#241715]
                                        ">
                                            {
                                                ratingSummary.averageRating.toFixed(1)
                                            }
                                        </span>

                                        <span className="
                                            text-[10px]
                                            text-[#756763]
                                        ">
                                            ({
                                                ratingSummary.ratingCount
                                            })
                                        </span>
                                    </div>
                                )
                                : (
                                    <div className="
                                        flex
                                        items-center
                                        gap-1
                                        text-[10px]
                                        text-[#756763]
                                    ">
                                        <span
                                            aria-hidden="true"
                                            className="
                                                text-sm
                                                leading-none
                                                text-[#d8cbc3]
                                            "
                                        >
                                            ☆
                                        </span>

                                        <span>
                                            New · No ratings yet
                                        </span>
                                    </div>
                                )
                    }

                </div>


                {/* Price + Add */}

                <div className="
                    mt-auto
                    flex
                    items-center
                    justify-between
                    gap-2
                    pt-2.5
                ">

                    <span className="
                        text-sm
                        font-bold
                        text-[#241715]

                        sm:text-base
                    ">
                        {
                            formatCurrency(
                                product.price
                            )
                        }

                        {
                            isWeighted
                            && (
                                <span className="
                                    ml-1
                                    text-[10px]
                                    font-semibold
                                    text-[#756763]

                                    sm:text-xs
                                ">
                                    /kg
                                </span>
                            )
                        }
                    </span>


                    <div
                        className={`
                            relative
                            h-10
                            shrink-0

                            ${
                                isWeighted
                                    ? "w-28 sm:w-32"
                                    : "w-24 sm:w-28"
                            }
                        `}
                        aria-live="polite"
                    >

                        {/* ADD */}

                        <button
                            type="button"
                            disabled={!isAvailable}
                            tabIndex={
                                isInCart
                                    ? -1
                                    : 0
                            }
                            aria-hidden={
                                isInCart
                            }
                            onClick={() =>
                                onAdd(
                                    product
                                )
                            }
                            aria-label={`Add ${product.name} to cart`}
                            className={`
                                absolute
                                inset-0
                                rounded-xl
                                border
                                border-[#7a1625]
                                bg-[#8f172b]
                                px-3
                                text-xs
                                font-bold
                                text-white
                                shadow-[0_4px_12px_rgba(122,22,37,0.2)]
                                transition-all
                                duration-200

                                hover:bg-[#6f1020]
                                active:scale-95

                                disabled:cursor-not-allowed
                                disabled:border-[#c9b9b4]
                                disabled:bg-[#f3eeeb]
                                disabled:text-[#9a8a85]

                                ${
                                    isInCart
                                        ? "pointer-events-none scale-90 opacity-0"
                                        : "scale-100 opacity-100"
                                }
                            `}
                        >
                            ADD
                        </button>


                        {/* Quantity */}

                        <div
                            role="group"
                            aria-label={
                                isWeighted
                                    ? `${product.name} weight: ${selectionLabel}`
                                    : `${product.name} quantity: ${selectionLabel}`
                            }
                            aria-hidden={
                                !isInCart
                            }
                            className={`
                                absolute
                                inset-0
                                grid
                                grid-cols-[32px_minmax(0,1fr)_32px]
                                items-center
                                overflow-hidden
                                rounded-xl
                                bg-[#7a1625]
                                text-white
                                shadow-[0_4px_12px_rgba(122,22,37,0.22)]
                                transition-all
                                duration-200

                                ${
                                    isInCart
                                        ? "scale-100 opacity-100"
                                        : "pointer-events-none scale-90 opacity-0"
                                }
                            `}
                        >

                            <button
                                type="button"
                                tabIndex={
                                    isInCart
                                        ? 0
                                        : -1
                                }
                                onClick={() =>
                                    onDecrease(
                                        product.id
                                    )
                                }
                                aria-label={
                                    isWeighted
                                        ? `Decrease ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                        : `Remove one ${product.name}`
                                }
                                className="
                                    h-10
                                    text-lg
                                    font-bold
                                    transition
                                    hover:bg-white/15
                                    active:scale-90
                                "
                            >
                                −
                            </button>


                            {
                                isWeighted
                                    ? (
                                        <button
                                            key={
                                                selectionLabel
                                            }
                                            type="button"
                                            tabIndex={
                                                isInCart
                                                    ? 0
                                                    : -1
                                            }
                                            onClick={() =>
                                                onAdd(
                                                    product
                                                )
                                            }
                                            aria-label={`Change ${product.name} quantity. Currently ${selectionLabel}`}
                                            className="
                                                flex
                                                h-10
                                                min-w-0
                                                flex-col
                                                items-center
                                                justify-center
                                                px-1
                                                text-center
                                                transition
                                                hover:bg-white/15
                                                active:scale-95
                                            "
                                        >
                                            <span className="
                                                max-w-full
                                                truncate
                                                text-[10px]
                                                font-bold
                                                leading-none
                                            ">
                                                {
                                                    selectionLabel
                                                }
                                            </span>

                                            <span className="
                                                mt-0.5
                                                text-[7px]
                                                font-bold
                                                uppercase
                                                tracking-[0.08em]
                                                text-white/80
                                            ">
                                                Change
                                            </span>
                                        </button>
                                    )
                                    : (
                                        <span
                                            key={
                                                selectionLabel
                                            }
                                            className="
                                                text-center
                                                text-xs
                                                font-bold
                                            "
                                        >
                                            {
                                                selectionLabel
                                            }
                                        </span>
                                    )
                            }


                            <button
                                type="button"
                                disabled={
                                    !isAvailable
                                }
                                tabIndex={
                                    isInCart
                                        ? 0
                                        : -1
                                }
                                onClick={() =>
                                    onIncrease(
                                        product.id
                                    )
                                }
                                aria-label={
                                    isWeighted
                                        ? `Increase ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                        : `Add one more ${product.name}`
                                }
                                className="
                                    h-10
                                    text-lg
                                    font-bold
                                    transition
                                    hover:bg-white/15
                                    active:scale-90
                                    disabled:cursor-not-allowed
                                    disabled:opacity-50
                                "
                            >
                                +
                            </button>

                        </div>

                    </div>

                </div>

            </div>

        </article>
    );
}