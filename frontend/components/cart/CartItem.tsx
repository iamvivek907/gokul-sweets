"use client";

import Image
    from "next/image";

import type {
    CartItem as CartItemType
} from "@/types/cart";


interface CartItemProps {

    item:
        CartItemType;

    onIncrease:
        (productId: number) => void;

    onDecrease:
        (productId: number) => void;

    onChangeWeight:
        (item: CartItemType) => void;

    onRemove:
        (productId: number) => void;
}


function formatCurrency(
    amount: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            maximumFractionDigits:
                0
        }
    ).format(
        amount
    );
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
            weightGrams
            /
            1000
        )} kg`;
    }


    return `${weightGrams} g`;
}


export default function CartItem({
    item,
    onIncrease,
    onDecrease,
    onChangeWeight,
    onRemove
}: CartItemProps) {

    const {
        product,
        quantity,
        weightGrams
    } =
        item;


    const isWeighted =
        product.saleMode === "WEIGHT";


    const itemTotal =
        isWeighted
            ? product.price * (weightGrams ?? 0) / 1000
            : product.price * quantity;


    return (
        <article
            className="
                w-full
                min-w-0
                overflow-hidden
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                p-4
                shadow-[0_4px_16px_rgba(60,30,20,0.06)]
            "
        >

            <div
                className="
                    flex
                    min-w-0
                    gap-3
                    sm:gap-4
                "
            >

                <div
                    className="
                        relative
                        h-20
                        w-20
                        shrink-0
                        overflow-hidden
                        rounded-2xl
                        bg-[#fff0dc]
                        sm:h-24
                        sm:w-24
                    "
                >

                    {product.imageUrl
                        ? (
                            <Image
                                src={
                                    product.imageUrl
                                }
                                alt={
                                    product.name
                                }
                                fill
                                sizes="96px"
                                className="
                                    object-cover
                                "
                            />
                        )
                        : (
                            <div
                                className="
                                    flex
                                    h-full
                                    w-full
                                    items-center
                                    justify-center
                                    text-3xl
                                "
                            >
                                🍬
                            </div>
                        )}

                </div>


                <div
                    className="
                        min-w-0
                        flex-1
                    "
                >

                    <div
                        className="
                            flex
                            min-w-0
                            items-start
                            justify-between
                            gap-2
                        "
                    >

                        <div
                            className="
                                min-w-0
                            "
                        >

                            <p
                                className="
                                    truncate
                                    text-[10px]
                                    font-semibold
                                    uppercase
                                    tracking-[0.12em]
                                    text-[#c88a20]
                                    sm:text-xs
                                "
                            >
                                {product.categoryName}
                            </p>


                            <h2
                                className="
                                    mt-1
                                    line-clamp-2
                                    text-sm
                                    font-bold
                                    leading-snug
                                    text-[#241715]
                                    sm:text-base
                                "
                            >
                                {product.name}
                            </h2>

                        </div>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    onRemove(
                                        product.id
                                    )
                            }
                            aria-label={
                                `Remove ${product.name} from cart`
                            }
                            className="
                                shrink-0
                                rounded-lg
                                px-2
                                py-1.5
                                text-xs
                                font-semibold
                                text-[#9a6960]
                                transition

                                hover:bg-[#fff4e5]
                                hover:text-[#7a1625]
                                active:scale-[0.96]
                            "
                        >
                            Remove
                        </button>

                    </div>


                    <p
                        className="
                            mt-2
                            text-xs
                            text-[#756763]
                        "
                    >
                        {formatCurrency(product.price)} {isWeighted ? "per kg" : "each"}
                    </p>

                </div>

            </div>


            <div
                className="
                    mt-4
                    flex
                    items-center
                    justify-between
                    gap-3
                    border-t
                    border-[#f1e8e1]
                    pt-4
                "
            >

                <div
                    className="
                        inline-flex
                        shrink-0
                        items-center
                        overflow-hidden
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-[#fffaf3]
                    "
                >

                    <button
                        type="button"
                        onClick={
                            () =>
                                onDecrease(
                                    product.id
                                )
                        }
                        aria-label={
                            isWeighted
                                ? `Decrease ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                : `Decrease quantity of ${product.name}`
                        }
                        className="
                            flex
                            h-10
                            w-10
                            items-center
                            justify-center
                            text-lg
                            font-bold
                            text-[#7a1625]
                            transition

                            hover:bg-[#fff0dc]
                            active:scale-90
                        "
                    >
                        −
                    </button>


                    {isWeighted
                        ? (
                            <button
                                type="button"
                                onClick={
                                    () =>
                                        onChangeWeight(
                                            item
                                        )
                                }
                                aria-label={`Change ${product.name} quantity. Currently ${formatWeight(
                                    weightGrams
                                    ?? product.minimumWeightGrams
                                    ?? 250
                                )}`}
                                className="
                                    flex
                                    h-10
                                    min-w-20
                                    flex-col
                                    items-center
                                    justify-center
                                    px-2
                                    text-center
                                    text-[#241715]
                                    transition

                                    hover:bg-[#fff0dc]
                                    active:scale-95
                                "
                            >

                                <span
                                    className="
                                        text-xs
                                        font-bold
                                        leading-none
                                    "
                                >
                                    {formatWeight(
                                        weightGrams
                                        ?? product.minimumWeightGrams
                                        ?? 250
                                    )}
                                </span>


                                <span
                                    className="
                                        mt-1
                                        text-[8px]
                                        font-bold
                                        uppercase
                                        leading-none
                                        tracking-[0.08em]
                                        text-[#7a1625]
                                    "
                                >
                                    Change
                                </span>

                            </button>
                        )
                        : (
                            <span
                                className="
                                    min-w-16
                                    text-center
                                    text-sm
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {quantity}
                            </span>
                        )}


                    <button
                        type="button"
                        onClick={
                            () =>
                                onIncrease(
                                    product.id
                                )
                        }
                        aria-label={
                            isWeighted
                                ? `Increase ${product.name} by ${product.weightStepGrams ?? 50} grams`
                                : `Increase quantity of ${product.name}`
                        }
                        className="
                            flex
                            h-10
                            w-10
                            items-center
                            justify-center
                            text-lg
                            font-bold
                            text-[#7a1625]
                            transition

                            hover:bg-[#fff0dc]
                            active:scale-90
                        "
                    >
                        +
                    </button>

                </div>


                <div
                    className="
                        min-w-0
                        text-right
                    "
                >

                    <p
                        className="
                            text-[10px]
                            font-semibold
                            uppercase
                            tracking-[0.08em]
                            text-[#756763]
                        "
                    >
                        Item total
                    </p>


                    <p
                        className="
                            mt-0.5
                            text-lg
                            font-bold
                            text-[#241715]
                        "
                    >
                        {formatCurrency(itemTotal)}
                    </p>

                </div>

            </div>

        </article>
    );
}
