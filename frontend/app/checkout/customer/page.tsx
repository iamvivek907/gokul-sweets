"use client";

import Link from "next/link";

import {
    useMemo,
    useSyncExternalStore
} from "react";

import {
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";

import CheckoutStateCard
    from "@/components/checkout/CheckoutStateCard";

import CustomerDetailsForm
    from "@/components/checkout/CustomerDetailsForm";

import {
    useCart
} from "@/hooks/useCart";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import {
    getCustomerSnapshot,
    getPickupSlotSnapshot,
    getServerCustomerSnapshot,
    getServerPickupSlotSnapshot,
    parseCustomerDetails,
    parsePickupSlot,
    saveCustomerDetails,
    subscribeToCustomer,
    subscribeToPickupSlot
} from "@/lib/checkoutStorage";

import type {
    CustomerDetails
} from "@/types/customer";


function formatCurrency(
    amount: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 0
        }
    ).format(
        amount
    );
}


function formatTime(
    value: string
): string {

    const [
        hour,
        minute
    ] =
        value.split(":");


    const date =
        new Date();


    date.setHours(
        Number(hour),
        Number(minute),
        0,
        0
    );


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            hour: "numeric",
            minute: "2-digit"
        }
    ).format(
        date
    );
}


export default function CustomerPage() {

    const router =
        useRouter();


    const {
        branch
    } =
        useSelectedBranch();


    const {
        items,
        branchId,
        itemCount,
        subtotal,
        isEmpty
    } =
        useCart();


    /*
     * =========================================================
     * PICKUP SLOT STORAGE
     * =========================================================
     *
     * useSyncExternalStore keeps the
     * server/client render hydration-safe.
     */
    const pickupSlotSnapshot =
        useSyncExternalStore(
            subscribeToPickupSlot,
            getPickupSlotSnapshot,
            getServerPickupSlotSnapshot
        );


    /*
     * =========================================================
     * CUSTOMER STORAGE
     * =========================================================
     */
    const customerSnapshot =
        useSyncExternalStore(
            subscribeToCustomer,
            getCustomerSnapshot,
            getServerCustomerSnapshot
        );


    /*
     * Parse serialized localStorage values.
     */
    const pickupSlot =
        useMemo(
            () =>
                parsePickupSlot(
                    pickupSlotSnapshot
                ),
            [
                pickupSlotSnapshot
            ]
        );


    const existingCustomer =
        useMemo(
            () =>
                parseCustomerDetails(
                    customerSnapshot
                ),
            [
                customerSnapshot
            ]
        );


    /*
     * =========================================================
     * CUSTOMER SUBMIT
     * =========================================================
     */
    function handleCustomerSubmit(
        customer:
            CustomerDetails
    ) {

        saveCustomerDetails(
            customer
        );


        router.push(
            "/checkout/review"
        );
    }


    /*
     * =========================================================
     * EMPTY CART
     * =========================================================
     */
    if (
        isEmpty
    ) {

        return (
            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                    "
                >

                    <CheckoutStateCard
                        title="Your cart is empty"
                        message="Add items before entering pickup details."
                        primaryAction={{
                            label:
                                "Explore Menu",

                            href:
                                "/menu"
                        }}
                    />

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * BRANCH MISMATCH
     * =========================================================
     */
    if (
        !branch
        ||
        branchId === null
        ||
        branch.id !== branchId
    ) {

        return (
            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                    "
                >

                    <CheckoutStateCard
                        title="Pickup branch needs attention"
                        message="The selected branch does not match the branch used by your cart."
                        tone="warning"
                        detail="Your cart is safe. Select the correct branch, then continue checkout."
                        primaryAction={{
                            label:
                                "Select Branch",

                            href:
                                "/"
                        }}
                        secondaryAction={{
                            label:
                                "Return to Cart",

                            href:
                                "/cart"
                        }}
                    />

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * NO PICKUP SLOT
     * =========================================================
     */
    if (!pickupSlot) {

        return (
            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                    "
                >

                    <CheckoutStateCard
                        title="Choose a pickup time"
                        message="Select your preferred pickup date and time before entering customer details."
                        tone="warning"
                        primaryAction={{
                            label:
                                "Choose Pickup Time",

                            href:
                                "/checkout/pickup"
                        }}
                        secondaryAction={{
                            label:
                                "Return to Cart",

                            href:
                                "/cart"
                        }}
                    />

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * CUSTOMER DETAILS SCREEN
     * =========================================================
     */
    return (
        <AppShell>

            <section
                className="
                    mx-auto
                    w-full
                    min-w-0
                    max-w-[900px]
                    px-4
                    pb-28
                    pt-5
                    sm:px-6
                    sm:pt-7
                "
            >

                <div
                    className="
                        mb-6
                    "
                >

                    <div
                        className="
                            flex
                            items-center
                            gap-2
                            text-[11px]
                            font-semibold
                            uppercase
                            tracking-[0.12em]
                        "
                    >

                        <span
                            className="
                                text-[#756763]
                            "
                        >
                            Pickup
                        </span>


                        <span
                            aria-hidden="true"
                            className="
                                text-[#c9b9b4]
                            "
                        >
                            →
                        </span>


                        <span
                            className="
                                text-[#7a1625]
                            "
                        >
                            Your details
                        </span>


                        <span
                            aria-hidden="true"
                            className="
                                text-[#c9b9b4]
                            "
                        >
                            →
                        </span>


                        <span
                            className="
                                text-[#756763]
                            "
                        >
                            Review
                        </span>

                    </div>


                    <p
                        className="
                            mt-5
                            text-xs
                            font-semibold
                            uppercase
                            tracking-[0.16em]
                            text-[#c88a20]
                        "
                    >
                        Checkout
                    </p>


                    <h1
                        className="
                            mt-2
                            text-3xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                            sm:text-4xl
                        "
                    >
                        Who&apos;s picking up?
                    </h1>


                    <p
                        className="
                            mt-2
                            max-w-xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Enter the name and mobile number we should use for
                        pickup identification and order updates.
                    </p>

                </div>


                <div
                    className="
                        grid
                        min-w-0
                        gap-6
                        md:grid-cols-[minmax(0,1fr)_320px]
                    "
                >

                    {/* =================================================
                        CUSTOMER FORM
                       ================================================= */}

                    <div
                        className="
                            min-w-0
                            rounded-3xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-5
                            shadow-[0_6px_24px_rgba(60,30,20,0.06)]
                            sm:p-6
                        "
                    >

                        <div
                            className="
                                mb-5
                                rounded-2xl
                                bg-[#fff8ef]
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                Pickup contact
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                No account is required. We&apos;ll only use these details
                                for this checkout and pickup communication.
                            </p>

                        </div>


                        <CustomerDetailsForm
                            initialValue={
                                existingCustomer
                            }
                            onSubmit={
                                handleCustomerSubmit
                            }
                        />

                    </div>


                    {/* =================================================
                        ORDER SUMMARY
                       ================================================= */}

                    <div
                        className="
                            min-w-0
                            space-y-4
                            md:sticky
                            md:top-24
                            md:self-start
                        "
                    >

                        {/* Pickup branch */}

                        <div
                            className="
                                rounded-3xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <div
                                className="
                                    flex
                                    items-start
                                    justify-between
                                    gap-3
                                "
                            >

                                <div
                                    className="
                                        min-w-0
                                    "
                                >

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-[0.12em]
                                            text-[#c88a20]
                                        "
                                    >
                                        Pickup from
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            truncate
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            branch.name
                                        }
                                    </p>


                                    {
                                        branch.address
                                        && (

                                            <p
                                                className="
                                                    mt-1
                                                    line-clamp-2
                                                    text-sm
                                                    leading-5
                                                    text-[#756763]
                                                "
                                            >
                                                {
                                                    branch.address
                                                }
                                            </p>

                                        )
                                    }

                                </div>


                                <Link
                                    href="/"
                                    className="
                                        shrink-0
                                        text-xs
                                        font-semibold
                                        text-[#7a1625]
                                        hover:underline
                                    "
                                >
                                    Change
                                </Link>

                            </div>

                        </div>


                        {/* Pickup time */}

                        <div
                            className="
                                rounded-3xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <div
                                className="
                                    flex
                                    items-center
                                    justify-between
                                    gap-4
                                "
                            >

                                <div>

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-wide
                                            text-[#c88a20]
                                        "
                                    >
                                        Pickup time
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatTime(
                                                pickupSlot.slot.startTime
                                            )
                                        }

                                        {" – "}

                                        {
                                            formatTime(
                                                pickupSlot.slot.endTime
                                            )
                                        }
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            font-medium
                                            text-[#756763]
                                        "
                                    >
                                        {
                                            pickupSlot.pickupType === "PRIORITY"
                                                ? "Priority pickup"
                                                : "Normal pickup"
                                        }
                                    </p>

                                </div>


                                <Link
                                    href="/checkout/pickup"
                                    className="
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                    "
                                >
                                    Change
                                </Link>

                            </div>

                        </div>


                        {/* Cart summary */}

                        <div
                            className="
                                rounded-3xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-[0.12em]
                                    text-[#c88a20]
                                "
                            >
                                Order summary
                            </p>


                            <div
                                className="
                                    mt-4
                                    flex
                                    items-center
                                    justify-between
                                    text-sm
                                "
                            >

                                <span
                                    className="
                                        text-[#756763]
                                    "
                                >
                                    {
                                        itemCount
                                    }
                                    {" "}
                                    item
                                    {
                                        itemCount === 1
                                            ? ""
                                            : "s"
                                    }
                                </span>


                                <span
                                    className="
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    {
                                        formatCurrency(
                                            subtotal
                                        )
                                    }
                                </span>

                            </div>


                            <div
                                className="
                                    mt-4
                                    border-t
                                    border-[#eadfd6]
                                    pt-4
                                "
                            >

                                {
                                    items
                                        .slice(
                                            0,
                                            3
                                        )
                                        .map(
                                            item => (

                                                <div
                                                    key={
                                                        item.product.id
                                                    }
                                                    className="
                                                        flex
                                                        items-center
                                                        justify-between
                                                        gap-3
                                                        py-1
                                                        text-xs
                                                    "
                                                >

                                                    <span
                                                        className="
                                                            min-w-0
                                                            truncate
                                                            text-[#756763]
                                                        "
                                                    >
                                                        {
                                                            item.quantity
                                                        }
                                                        ×{" "}
                                                        {
                                                            item.product.name
                                                        }
                                                    </span>


                                                    <span
                                                        className="
                                                            shrink-0
                                                            font-semibold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {
                                                            formatCurrency(
                                                                item.product.price
                                                                *
                                                                item.quantity
                                                            )
                                                        }
                                                    </span>

                                                </div>

                                            )
                                        )
                                }


                                {
                                    items.length > 3
                                    && (

                                        <p
                                            className="
                                                mt-2
                                                text-xs
                                                text-[#756763]
                                            "
                                        >
                                            +
                                            {
                                                items.length
                                                - 3
                                            }
                                            {" "}
                                            more item
                                            {
                                                items.length - 3 === 1
                                                    ? ""
                                                    : "s"
                                            }
                                        </p>

                                    )
                                }

                            </div>

                        </div>

                    </div>

                </div>


                <Link
                    href="/checkout/pickup"
                    className="
                        mt-6
                        inline-flex
                        min-h-11
                        items-center
                        text-sm
                        font-semibold
                        text-[#7a1625]
                        hover:underline
                    "
                >
                    ← Back to Pickup Time
                </Link>

            </section>

        </AppShell>
    );
}