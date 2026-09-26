interface CartSummaryProps {
    refined?: boolean;

    itemCount:
        number;

    subtotal:
        number;

    canContinue:
        boolean;

    onContinue:
        () => void;
}


function formatCurrency(
    amount: number, exact = false
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            maximumFractionDigits:
                exact ? 2 : 0
        }
    ).format(
        amount
    );
}


export default function CartSummary({
    refined = false,
    itemCount,
    subtotal,
    canContinue,
    onContinue
}: CartSummaryProps) {

    return (
        <div
            className="
                w-full
                min-w-0
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                p-5
                shadow-[0_6px_24px_rgba(60,30,20,0.08)]
            "
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-[0.14em]
                    text-[#c88a20]
                "
            >
                Checkout
            </p>


            <h2
                className="
                    mt-1
                    text-xl
                    font-bold
                    text-[#241715]
                "
            >
                {refined ? "Your cart estimate" : "Order Summary"}
            </h2>


            <div
                className="
                    mt-5
                    space-y-3
                "
            >

                <div
                    className="
                        flex
                        items-center
                        justify-between
                        gap-4
                        text-sm
                    "
                >

                    <span
                        className="
                            text-[#756763]
                        "
                    >
                        Items
                    </span>


                    <span
                        className="
                            font-semibold
                            text-[#241715]
                        "
                    >
                        {itemCount}
                    </span>

                </div>


                <div
                    className="
                        flex
                        items-center
                        justify-between
                        gap-4
                        border-t
                        border-[#eadfd6]
                        pt-4
                    "
                >

                    <span
                        className="
                            font-semibold
                            text-[#241715]
                        "
                    >
                        {refined ? "Items subtotal · estimate" : "Cart subtotal"}
                    </span>


                    <span
                        className="
                            text-xl
                            font-bold
                            text-[#7a1625]
                        "
                    >
                        {formatCurrency(subtotal, refined)}
                    </span>

                </div>

            </div>


            <div
                className="
                    mt-4
                    rounded-2xl
                    bg-[#fff4e5]
                    p-4
                "
            >

                <p
                    className="
                        text-xs
                        font-semibold
                        text-[#241715]
                    "
                >
                    {refined ? "See every charge before payment" : "Final payable amount comes later"}
                </p>


                <p
                    className="
                        mt-1
                        text-xs
                        leading-5
                        text-[#756763]
                    "
                >
                    {refined
                        ? "Your branch’s latest item prices, any applicable pickup charge or tax, and discounts appear together in the final quote. You review the total before paying."
                        : "Pickup charges, tax and any eligible rebate are recalculated by Gokul Sweets before payment."}
                </p>

            </div>


            {!canContinue
                && (
                    <p
                        className="
                            mt-4
                            rounded-xl
                            border
                            border-[#e5b768]
                            bg-[#fff7e8]
                            px-3
                            py-2.5
                            text-xs
                            leading-5
                            text-[#7a1625]
                        "
                    >
                        Select the pickup branch associated with this cart to continue.
                    </p>
                )}


            <button
                type="button"
                disabled={
                    !canContinue
                }
                onClick={
                    onContinue
                }
                className="
                    mt-5
                    flex
                    min-h-12
                    w-full
                    items-center
                    justify-center
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    text-sm
                    font-bold
                    text-white!
                    transition

                    hover:bg-[#5d0f1b]
                    active:scale-[0.98]

                    disabled:cursor-not-allowed
                    disabled:bg-[#c9b9b4]
                    disabled:active:scale-100
                "
            >
                Continue to Pickup Time
            </button>


            <p
                className="
                    mt-3
                    text-center
                    text-[11px]
                    leading-4
                    text-[#756763]
                "
            >
                You can still review your order before payment.
            </p>

        </div>
    );
}
