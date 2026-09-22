"use client";

import Link from "next/link";


interface FloatingCartButtonProps {

    itemCount:
        number;

    total:
        number;
}


function formatCurrency(
    amount: number
): string {

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


export default function FloatingCartButton({
    itemCount,
    total
}: FloatingCartButtonProps) {

    if (
        itemCount <= 0
    ) {

        return null;
    }


    return (
        <div
            className="
                fixed
                bottom-[calc(5.5rem+env(safe-area-inset-bottom))]
                left-1/2
                z-60
                w-[calc(100vw-2rem)]
                max-w-lg
                -translate-x-1/2
            "
        >

            <Link
                href="/cart"
                className="
                    flex
                    min-h-16
                    w-full
                    items-center
                    justify-between
                    gap-3
                    rounded-2xl
                    bg-[#7a1625]
                    px-4
                    py-3
                    text-white!
                    shadow-[0_10px_30px_rgba(70,15,30,0.30)]
                    transition

                    hover:bg-[#5d0f1b]
                    active:scale-[0.98]

                    sm:px-5
                "
            >

                <div
                    className="
                        flex
                        min-w-0
                        items-center
                        gap-3
                    "
                >

                    <div
                        className="
                            flex
                            h-10
                            w-10
                            shrink-0
                            items-center
                            justify-center
                            rounded-full
                            bg-white
                            text-sm
                            font-bold
                            text-[#7a1625]
                        "
                    >
                        {itemCount}
                    </div>


                    <div
                        className="
                            min-w-0
                        "
                    >

                        <p
                            className="
                                text-xs
                                text-white/75!
                            "
                        >
                            {
                                itemCount === 1
                                    ? "1 item"
                                    : `${itemCount} items`
                            }
                        </p>


                        <p
                            className="
                                truncate
                                font-bold
                                text-white!
                            "
                        >
                            View Cart
                        </p>

                    </div>

                </div>


                <div
                    className="
                        shrink-0
                        text-right
                    "
                >

                    <p
                        className="
                            font-bold
                            text-white!
                        "
                    >
                        {
                            formatCurrency(
                                total
                            )
                        }
                    </p>


                    <p
                        className="
                            text-xs
                            text-white/75!
                        "
                    >
                        →
                    </p>

                </div>

            </Link>

        </div>
    );
}
