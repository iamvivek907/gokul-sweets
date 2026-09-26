"use client";

import {
    useEffect,
    useMemo,
    useState,
    useSyncExternalStore
} from "react";

import {
    useParams,
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";
import CheckoutExperienceFrame from "@/components/checkout/CheckoutExperienceFrame";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

import {
    useCart
} from "@/hooks/useCart";

import {
    useReservationCountdown
} from "@/hooks/useReservationCountdown";

import {
    createCartFingerprint
} from "@/lib/cartFingerprint";

import {
    clearPendingOrder,
    getPendingOrderSnapshot,
    getServerPendingOrderSnapshot,
    parsePendingOrder,
    savePendingOrder,
    subscribeToPendingOrder
} from "@/lib/pendingOrderStorage";

import {
    getCustomerOrder
} from "@/services/orderApi";

import {
    applyRebate,
    getAvailableRebates,
    removeRebate
} from "@/services/rebateApi";

import type {
    AppliedRebateResponse,
    AvailableRebateResponse
} from "@/types/rebate";


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
                2
        }
    ).format(
        amount
    );
}


function getRebateTypeLabel(
    rebate:
        AvailableRebateResponse
): string {

    switch (
        rebate.rebateType
    ) {

        case "PERCENTAGE":
            return "Percentage offer";

        case "FIXED_AMOUNT":
            return "Fixed saving";

        case "SLAB":
            return "Spend & save";

        default:
            return "Special offer";
    }
}


function padSeconds(
    value: number
): string {

    return value
        .toString()
        .padStart(
            2,
            "0"
        );
}


export default function OffersPage() {
    const checkoutExperienceV2 = useStorefrontFeatures()?.checkoutExperienceV2 === true;

    const router =
        useRouter();


    const params =
        useParams<{
            orderNumber: string;
        }>();


    const orderNumber =
        decodeURIComponent(
            params.orderNumber
        );


    const {
        items
    } =
        useCart();


    /*
     * =========================================================
     * PENDING ORDER
     * =========================================================
     */

    const pendingOrderSnapshot =
        useSyncExternalStore(
            subscribeToPendingOrder,
            getPendingOrderSnapshot,
            getServerPendingOrderSnapshot
        );


    const pendingOrder =
        useMemo(
            () =>
                parsePendingOrder(
                    pendingOrderSnapshot
                ),
            [
                pendingOrderSnapshot
            ]
        );


    /*
     * =========================================================
     * RESERVATION COUNTDOWN
     * =========================================================
     *
     * reservationExpiresAt comes directly from Spring Boot.
     *
     * The browser only displays the countdown. It never
     * calculates or extends the reservation deadline.
     */

    const {
        remainingSeconds,
        expired:
            reservationExpired,
        minutes:
            reservationMinutes,
        seconds:
            reservationSeconds
    } =
        useReservationCountdown(
            pendingOrder
                ?.reservationExpiresAt
        );


    const reservationRunningLow =
        !reservationExpired
        &&
        remainingSeconds >
            0
        &&
        remainingSeconds <=
            5 * 60;


    /*
     * =========================================================
     * CART FINGERPRINT
     * =========================================================
     *
     * A changed local cart means the backend order must be
     * updated before rebates or payment can continue.
     *
     * The Review page performs that update against the same
     * PENDING_PAYMENT order and then stores the new fingerprint.
     */

    const currentCartFingerprint =
        useMemo(
            () =>
                createCartFingerprint(
                    items
                ),
            [
                items
            ]
        );


    const cartChanged =
        Boolean(
            pendingOrder
            &&
            pendingOrder.orderNumber ===
                orderNumber
            &&
            pendingOrder.cartFingerprint !==
                currentCartFingerprint
        );


    /*
     * =========================================================
     * UI STATE
     * =========================================================
     */

    const [
        rebates,
        setRebates
    ] =
        useState<
            AvailableRebateResponse[]
        >(
            []
        );


    const [
        appliedRebate,
        setAppliedRebate
    ] =
        useState<
            AppliedRebateResponse
            | null
        >(
            null
        );


    const [
        loading,
        setLoading
    ] =
        useState(
            true
        );


    const [
        applyingCode,
        setApplyingCode
    ] =
        useState<
            string
            | null
        >(
            null
        );


    const [
        removing,
        setRemoving
    ] =
        useState(
            false
        );


    const [
        error,
        setError
    ] =
        useState<
            string
            | null
        >(
            null
        );


    const [
        offersLoadedSuccessfully,
        setOffersLoadedSuccessfully
    ] =
        useState(
            false
        );


    const [
        reloadKey,
        setReloadKey
    ] =
        useState(
            0
        );


    const [
        reservationReleaseReady,
        setReservationReleaseReady
    ] =
        useState(
            false
        );


    const [
        reservationReleaseError,
        setReservationReleaseError
    ] =
        useState<
            string
            | null
        >(
            null
        );


    /*
     * =========================================================
     * WAIT FOR BACKEND RESERVATION RELEASE
     * =========================================================
     *
     * The browser countdown can reach zero slightly before the
     * scheduled backend expiry processor has released capacity.
     *
     * Do not allow a replacement checkout to start until the
     * backend confirms that this old order is no longer
     * PENDING_PAYMENT.
     */

    useEffect(
        () => {

            if (
                !reservationExpired
                ||
                !pendingOrder
                ||
                pendingOrder.orderNumber !==
                    orderNumber
            ) {

                return;
            }


            let cancelled =
                false;

            let retryTimer:
                number
                | null =
                null;


            async function checkReservationRelease():
                Promise<void> {

                try {

                    const backendOrder =
                        await getCustomerOrder(
                            orderNumber
                        );


                    if (cancelled) {
                        return;
                    }


                    if (
                        backendOrder.orderStatus !==
                        "PENDING_PAYMENT"
                    ) {

                        setReservationReleaseReady(
                            true
                        );

                        setReservationReleaseError(
                            null
                        );

                        return;
                    }


                    setReservationReleaseReady(
                        false
                    );

                    setReservationReleaseError(
                        null
                    );


                    retryTimer =
                        window.setTimeout(
                            () => {

                                void checkReservationRelease();

                            },
                            2000
                        );

                } catch (exception) {

                    if (cancelled) {
                        return;
                    }


                    console.error(
                        "Unable to confirm reservation release:",
                        exception
                    );


                    setReservationReleaseReady(
                        false
                    );

                    setReservationReleaseError(
                        "We could not confirm that the expired pickup reservation has been released yet. Please return to the cart or try again shortly."
                    );
                }
            }


            void checkReservationRelease();


            return () => {

                cancelled =
                    true;


                if (
                    retryTimer !==
                    null
                ) {

                    window.clearTimeout(
                        retryTimer
                    );
                }
            };

        },
        [
            orderNumber,
            pendingOrder,
            reservationExpired
        ]
    );


    /*
     * =========================================================
     * EXPIRED RESERVATION RECOVERY
     * =========================================================
     */

    function handleChooseNewPickupSlot() {

        if (
            !reservationReleaseReady
        ) {

            return;
        }


        /*
         * Keep cart, branch and customer details.
         *
         * Only the dead pending-order pointer and its old pickup
         * selection are cleared.
         */
        clearPendingOrder();


        if (
            typeof window !==
            "undefined"
        ) {

            window.localStorage.removeItem(
                "gokul-selected-pickup-slot"
            );
        }


        router.push(
            "/checkout/pickup"
        );
    }


    /*
     * =========================================================
     * LOAD AVAILABLE REBATES
     * =========================================================
     */

    useEffect(
        () => {

            if (
                !pendingOrder
                ||
                pendingOrder.orderNumber !==
                    orderNumber
                ||
                cartChanged
                ||
                reservationExpired
            ) {

                return;
            }


            let cancelled =
                false;


            async function loadRebates() {

                try {

                    const response =
                        await getAvailableRebates(
                            orderNumber
                        );


                    if (
                        cancelled
                    ) {

                        return;
                    }


                    setRebates(
                        response
                    );


                    setOffersLoadedSuccessfully(
                        true
                    );


                    setError(
                        null
                    );

                } catch (exception) {

                    if (
                        cancelled
                    ) {

                        return;
                    }


                    console.error(
                        "Unable to load rebates:",
                        exception
                    );


                    setRebates(
                        []
                    );


                    setOffersLoadedSuccessfully(
                        false
                    );


                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to check available offers."
                    );

                } finally {

                    if (
                        !cancelled
                    ) {

                        setLoading(
                            false
                        );
                    }
                }
            }


            void loadRebates();


            return () => {

                cancelled =
                    true;
            };

        },
        [
            cartChanged,
            orderNumber,
            pendingOrder,
            reloadKey,
            reservationExpired
        ]
    );


    /*
     * =========================================================
     * UPDATE LOCAL PENDING ORDER TOTAL
     * =========================================================
     */

    function updatePendingOrderTotal(
        totalAmount: number
    ) {

        if (
            !pendingOrder
        ) {

            return;
        }


        savePendingOrder({
            ...pendingOrder,

            totalAmount
        });
    }


    /*
     * =========================================================
     * APPLY REBATE
     * =========================================================
     */

    async function handleApply(
        rebate:
            AvailableRebateResponse
    ) {

        if (
            applyingCode
            ||
            removing
            ||
            reservationExpired
        ) {

            return;
        }


        setApplyingCode(
            rebate.code
        );


        setError(
            null
        );


        try {

            const response =
                await applyRebate(
                    orderNumber,
                    rebate.code
                );


            setAppliedRebate(
                response
            );


            updatePendingOrderTotal(
                response.totalAmount
            );

        } catch (exception) {

            console.error(
                "Unable to apply rebate:",
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to apply this offer."
            );

        } finally {

            setApplyingCode(
                null
            );
        }
    }


    /*
     * =========================================================
     * REMOVE REBATE
     * =========================================================
     */

    async function handleRemove() {

        if (
            removing
            ||
            applyingCode
            ||
            reservationExpired
        ) {

            return;
        }


        setRemoving(
            true
        );


        setError(
            null
        );


        try {

            const response =
                await removeRebate(
                    orderNumber
                );


            setAppliedRebate(
                null
            );


            updatePendingOrderTotal(
                response.totalAmount
            );

        } catch (exception) {

            console.error(
                "Unable to remove rebate:",
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to remove this offer."
            );

        } finally {

            setRemoving(
                false
            );
        }
    }


    /*
     * =========================================================
     * ADD MORE ITEMS
     * =========================================================
     *
     * IMPORTANT NEW FLOW:
     *
     * Do NOT cancel the order.
     * Do NOT release the pickup reservation.
     * Do NOT restart the reservation timer.
     *
     * The customer goes back to the cart while this same
     * PENDING_PAYMENT checkout remains active.
     *
     * The Review page updates THIS SAME backend order before
     * returning the customer to Offers.
     */

    function handleAddMoreItems() {

        if (
            applyingCode
            ||
            removing
            ||
            reservationExpired
        ) {

            return;
        }


        router.push(
            "/cart"
        );
    }


    /*
     * =========================================================
     * RETRY OFFERS
     * =========================================================
     */

    function handleRetry() {

        if (
            reservationExpired
        ) {

            return;
        }


        setLoading(
            true
        );


        setError(
            null
        );


        setOffersLoadedSuccessfully(
            false
        );


        setReloadKey(
            current =>
                current + 1
        );
    }


    /*
     * =========================================================
     * CONTINUE TO PAYMENT
     * =========================================================
     */

    function handleContinueToPayment() {

        if (
            !pendingOrder
            ||
            pendingOrder.orderNumber !==
                orderNumber
        ) {

            setError(
                "No pending checkout was found for this order."
            );

            return;
        }


        if (
            reservationExpired
        ) {

            setError(
                "Your pickup reservation has expired. Please choose a pickup slot again."
            );

            return;
        }


        if (
            cartChanged
        ) {

            setError(
                "Your cart has changed. Please continue checkout from the cart so this order can be updated before payment."
            );

            return;
        }


        if (
            applyingCode
            ||
            removing
        ) {

            return;
        }


        router.push(
            `/checkout/payment/${encodeURIComponent(
                orderNumber
            )}`
        );
    }


    /*
     * =========================================================
     * MISSING PENDING ORDER
     * =========================================================
     */

    if (
        !pendingOrder
        ||
        pendingOrder.orderNumber !==
            orderNumber
    ) {

        return (

            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                    "
                >

                    <div
                        className="
                            rounded-3xl
                            border
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
                            🧾
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Checkout not found
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            We could not find the pending
                            order for this checkout.
                        </p>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    router.push(
                                        "/cart"
                                    )
                            }
                            className="
                                mt-6
                                min-h-12
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-semibold
                                text-white
                            "
                        >
                            Return to Cart
                        </button>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * RESERVATION EXPIRED
     * =========================================================
     */

    if (
        reservationExpired
    ) {

        return (

            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                        pb-10
                    "
                >

                    <div
                        className="
                            rounded-3xl
                            border
                            border-red-200
                            bg-white
                            px-6
                            py-10
                            text-center
                        "
                    >

                        <div
                            className="
                                text-4xl
                            "
                        >
                            ⏱️
                        </div>


                        <h1
                            className="
                                mt-4
                                text-2xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Pickup reservation expired
                        </h1>


                        <p
                            className="
                                mt-3
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            The reserved checkout time has ended.
                            Your cart is still safe. We are making
                            sure the old pickup reservation has
                            been released before you choose a new
                            slot.
                        </p>


                        <div
                            className="
                                mt-5
                                rounded-2xl
                                bg-red-50
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-semibold
                                    text-red-700
                                "
                            >
                                Your cart and customer details
                                are still available.
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    leading-5
                                    text-red-600
                                "
                            >
                                You do not need to add your items
                                or enter your details again.
                            </p>

                        </div>


                        {
                            !reservationReleaseReady
                            &&
                            !reservationReleaseError
                            && (

                                <div
                                    className="
                                        mt-5
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-[#fffaf3]
                                        p-4
                                    "
                                >

                                    <div
                                        className="
                                            mx-auto
                                            h-6
                                            w-6
                                            animate-spin
                                            rounded-full
                                            border-2
                                            border-[#eadfd6]
                                            border-t-[#7a1625]
                                        "
                                    />


                                    <p
                                        className="
                                            mt-3
                                            text-sm
                                            font-semibold
                                            text-[#756763]
                                        "
                                    >
                                        Releasing expired pickup
                                        reservation...
                                    </p>

                                </div>

                            )
                        }


                        {
                            reservationReleaseReady
                            && (

                                <div
                                    className="
                                        mt-5
                                        rounded-2xl
                                        border
                                        border-green-200
                                        bg-green-50
                                        p-4
                                    "
                                >

                                    <p
                                        className="
                                            text-sm
                                            font-semibold
                                            text-green-700
                                        "
                                    >
                                        The old pickup reservation
                                        has been released.
                                    </p>

                                </div>

                            )
                        }


                        {
                            reservationReleaseError
                            && (

                                <div
                                    className="
                                        mt-5
                                        rounded-2xl
                                        border
                                        border-red-200
                                        bg-red-50
                                        p-4
                                    "
                                >

                                    <p
                                        className="
                                            text-sm
                                            leading-6
                                            text-red-700
                                        "
                                    >
                                        {
                                            reservationReleaseError
                                        }
                                    </p>

                                </div>

                            )
                        }


                        <button
                            type="button"
                            disabled={
                                !reservationReleaseReady
                            }
                            onClick={
                                handleChooseNewPickupSlot
                            }
                            className="
                                mt-6
                                min-h-12
                                w-full
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-bold
                                text-white
                                transition

                                hover:bg-[#5d0f1b]

                                disabled:cursor-not-allowed
                                disabled:bg-[#c9b9b4]
                            "
                        >
                            {
                                reservationReleaseReady
                                    ? "Choose New Pickup Slot"
                                    : "Waiting for Slot Release..."
                            }
                        </button>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    router.push(
                                        "/cart"
                                    )
                            }
                            className="
                                mt-3
                                min-h-11
                                w-full
                                text-sm
                                font-semibold
                                text-[#7a1625]
                            "
                        >
                            Return to Cart
                        </button>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * CART CHANGED
     * =========================================================
     *
     * The Offers page cannot safely apply rebates or start
     * payment while the local cart differs from the latest
     * backend order contents.
     */

    if (
        cartChanged
    ) {

        return (

            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-lg
                        pb-10
                    "
                >

                    <div
                        className="
                            mb-4
                            rounded-2xl
                            border
                            border-[#f0d18f]
                            bg-[#fff2d2]
                            p-4
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
                                        font-bold
                                        uppercase
                                        tracking-wide
                                        text-[#7a1625]
                                    "
                                >
                                    Pickup reserved
                                </p>


                                <p
                                    className="
                                        mt-1
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    Time remaining
                                </p>

                            </div>


                            <p
                                className={`
                                    text-2xl
                                    font-extrabold

                                    ${
                                        reservationRunningLow
                                            ? "text-red-700"
                                            : "text-[#7a1625]"
                                    }
                                `}
                            >
                                {
                                    reservationMinutes
                                }:
                                {
                                    padSeconds(
                                        reservationSeconds
                                    )
                                }
                            </p>

                        </div>

                    </div>


                    <div
                        className="
                            rounded-3xl
                            border
                            border-amber-200
                            bg-white
                            px-6
                            py-10
                            text-center
                        "
                    >

                        <div
                            className="
                                text-4xl
                            "
                        >
                            🛒
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Your cart has been updated
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            Your pickup reservation is still
                            being held. Continue checkout so
                            we can update this same order with
                            your latest cart.
                        </p>


                        <div
                            className="
                                mt-5
                                rounded-2xl
                                bg-[#fffaf3]
                                p-4
                                text-left
                            "
                        >

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                The reservation timer does not
                                restart when you add items.
                            </p>

                        </div>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    router.push(
                                        "/cart"
                                    )
                            }
                            className="
                                mt-6
                                min-h-12
                                w-full
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-bold
                                text-white
                            "
                        >
                            Continue from Cart
                        </button>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * LOADING
     * =========================================================
     */

    if (
        loading
    ) {

        return (

            <AppShell>

                <section
                    className="
                        mx-auto
                        max-w-2xl
                    "
                >

                    <div
                        className="
                            rounded-2xl
                            border
                            border-[#f0d18f]
                            bg-[#fff2d2]
                            p-4
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

                            <span
                                className="
                                    text-sm
                                    font-semibold
                                    text-[#7a1625]
                                "
                            >
                                Pickup reserved
                            </span>


                            <span
                                className="
                                    font-extrabold
                                    text-[#7a1625]
                                "
                            >
                                {
                                    reservationMinutes
                                }:
                                {
                                    padSeconds(
                                        reservationSeconds
                                    )
                                }
                            </span>

                        </div>

                    </div>


                    <div
                        className="
                            mt-4
                            rounded-3xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-8
                            text-center
                        "
                    >

                        <div
                            className="
                                mx-auto
                                h-8
                                w-8
                                animate-spin
                                rounded-full
                                border-4
                                border-[#eadfd6]
                                border-t-[#7a1625]
                            "
                        />


                        <p
                            className="
                                mt-4
                                text-sm
                                font-semibold
                                text-[#756763]
                            "
                        >
                            Finding your best offers...
                        </p>

                    </div>

                </section>

            </AppShell>
        );
    }


    const displayAmountBeforeRebate =
        appliedRebate
            ?.amountBeforeRebate
        ??
        pendingOrder.totalAmount;


    const displayTotal =
        appliedRebate
            ?.totalAmount
        ??
        pendingOrder.totalAmount;


    return (

        <AppShell>
            <CheckoutExperienceFrame enabled={checkoutExperienceV2} stage="offers">

            <section
                className="
                    mx-auto
                    max-w-2xl
                    pb-10
                "
            >

                {/* Reservation timer */}

                <div
                    className={`
                        mb-5
                        rounded-2xl
                        border
                        p-4

                        ${
                            reservationRunningLow
                                ? "border-red-200 bg-red-50"
                                : "border-[#f0d18f] bg-[#fff2d2]"
                        }
                    `}
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
                                className={`
                                    text-xs
                                    font-bold
                                    uppercase
                                    tracking-wide

                                    ${
                                        reservationRunningLow
                                            ? "text-red-700"
                                            : "text-[#7a1625]"
                                    }
                                `}
                            >
                                Pickup slot reserved
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                Complete checkout before
                                your reservation expires.
                            </p>

                        </div>


                        <div
                            className="
                                shrink-0
                                text-right
                            "
                        >

                            <p
                                className={`
                                    text-2xl
                                    font-extrabold

                                    ${
                                        reservationRunningLow
                                            ? "text-red-700"
                                            : "text-[#7a1625]"
                                    }
                                `}
                            >
                                {
                                    reservationMinutes
                                }:
                                {
                                    padSeconds(
                                        reservationSeconds
                                    )
                                }
                            </p>


                            <p
                                className="
                                    text-[10px]
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#756763]
                                "
                            >
                                remaining
                            </p>

                        </div>

                    </div>


                    {
                        reservationRunningLow
                        && (

                            <p
                                className="
                                    mt-3
                                    border-t
                                    border-red-200
                                    pt-3
                                    text-xs
                                    font-semibold
                                    leading-5
                                    text-red-700
                                "
                            >
                                Hurry — less than 5 minutes
                                remain to complete checkout.
                            </p>

                        )
                    }

                </div>


                {/* Page header */}

                <div
                    className="
                        mb-6
                    "
                >

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#c88a20]
                        "
                    >
                        Checkout
                    </p>


                    <h1
                        className="
                            mt-1
                            text-3xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                        "
                    >
                        Offers & savings
                    </h1>


                    <p
                        className="
                            mt-2
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Choose an eligible offer or add
                        more items before payment.
                    </p>

                </div>


                {/* Order total */}

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
                                Your order
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                {
                                    orderNumber
                                }
                            </p>

                        </div>


                        <p
                            className="
                                text-2xl
                                font-extrabold
                                text-[#241715]
                            "
                        >
                            {
                                formatCurrency(
                                    displayTotal
                                )
                            }
                        </p>

                    </div>


                    {
                        appliedRebate
                        && (

                            <div
                                className="
                                    mt-4
                                    border-t
                                    border-[#eadfd6]
                                    pt-4
                                "
                            >

                                <div
                                    className="
                                        flex
                                        items-center
                                        justify-between
                                        gap-3
                                        text-sm
                                    "
                                >

                                    <span
                                        className="
                                            text-[#756763]
                                        "
                                    >
                                        Order amount
                                    </span>


                                    <span
                                        className="
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatCurrency(
                                                displayAmountBeforeRebate
                                            )
                                        }
                                    </span>

                                </div>


                                <div
                                    className="
                                        mt-2
                                        flex
                                        items-center
                                        justify-between
                                        gap-3
                                        text-sm
                                    "
                                >

                                    <span
                                        className="
                                            text-[#756763]
                                        "
                                    >
                                        Offer saving
                                    </span>


                                    <span
                                        className="
                                            font-bold
                                            text-green-700
                                        "
                                    >
                                        -
                                        {
                                            formatCurrency(
                                                appliedRebate
                                                    .rebateAmount
                                            )
                                        }
                                    </span>

                                </div>

                            </div>

                        )
                    }

                </div>


                {/* Applied rebate */}

                {
                    appliedRebate
                    && (

                        <div
                            className="
                                mt-4
                                rounded-3xl
                                border
                                border-green-200
                                bg-green-50
                                p-5
                            "
                        >

                            <div
                                className="
                                    flex
                                    items-start
                                    justify-between
                                    gap-4
                                "
                            >

                                <div>

                                    <p
                                        className="
                                            text-xs
                                            font-bold
                                            uppercase
                                            tracking-wide
                                            text-green-700
                                        "
                                    >
                                        Offer applied
                                    </p>


                                    <h2
                                        className="
                                            mt-1
                                            text-lg
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            appliedRebate
                                                .rebateName
                                            ??
                                            appliedRebate
                                                .rebateCode
                                        }
                                    </h2>


                                    {
                                        appliedRebate
                                            .rebateCode
                                        && (

                                            <p
                                                className="
                                                    mt-1
                                                    text-xs
                                                    font-bold
                                                    uppercase
                                                    tracking-wide
                                                    text-[#7a1625]
                                                "
                                            >
                                                {
                                                    appliedRebate
                                                        .rebateCode
                                                }
                                            </p>

                                        )
                                    }


                                    <p
                                        className="
                                            mt-2
                                            text-sm
                                            font-semibold
                                            text-green-700
                                        "
                                    >
                                        You saved{" "}
                                        {
                                            formatCurrency(
                                                appliedRebate
                                                    .rebateAmount
                                            )
                                        }
                                    </p>

                                </div>


                                <div
                                    className="
                                        flex
                                        h-10
                                        w-10
                                        shrink-0
                                        items-center
                                        justify-center
                                        rounded-full
                                        bg-green-100
                                        text-lg
                                        font-bold
                                        text-green-700
                                    "
                                >
                                    ✓
                                </div>

                            </div>


                            <button
                                type="button"
                                disabled={
                                    removing
                                    ||
                                    applyingCode !==
                                        null
                                }
                                onClick={
                                    () =>
                                        void handleRemove()
                                }
                                className="
                                    mt-4
                                    min-h-11
                                    w-full
                                    rounded-xl
                                    border
                                    border-green-300
                                    bg-white
                                    px-4
                                    text-sm
                                    font-bold
                                    text-green-800

                                    disabled:cursor-not-allowed
                                    disabled:opacity-60
                                "
                            >
                                {
                                    removing
                                        ? "Removing..."
                                        : "Remove offer"
                                }
                            </button>

                        </div>

                    )
                }


                {/* Error */}

                {
                    error
                    && (

                        <div
                            className="
                                mt-4
                                rounded-2xl
                                border
                                border-red-100
                                bg-red-50
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-semibold
                                    text-red-700
                                "
                            >
                                Unable to continue
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    leading-6
                                    text-red-600
                                "
                            >
                                {
                                    error
                                }
                            </p>


                            {
                                !offersLoadedSuccessfully
                                && (

                                    <button
                                        type="button"
                                        onClick={
                                            handleRetry
                                        }
                                        className="
                                            mt-3
                                            text-sm
                                            font-bold
                                            text-[#7a1625]
                                        "
                                    >
                                        Try checking offers again
                                    </button>

                                )
                            }

                        </div>

                    )
                }


                {/* Available offers */}

                <div
                    className="
                        mt-6
                    "
                >

                    <div
                        className="
                            mb-3
                            flex
                            items-end
                            justify-between
                            gap-3
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
                                Available now
                            </p>


                            <h2
                                className="
                                    mt-1
                                    text-xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                Your eligible offers
                            </h2>

                        </div>


                        {
                            rebates.length >
                                0
                            && (

                                <span
                                    className="
                                        rounded-full
                                        bg-[#f6dfad]
                                        px-3
                                        py-1
                                        text-xs
                                        font-bold
                                        text-[#7a1625]
                                    "
                                >
                                    {
                                        rebates.length
                                    }{" "}
                                    {
                                        rebates.length ===
                                        1
                                            ? "offer"
                                            : "offers"
                                    }
                                </span>

                            )
                        }

                    </div>


                    {
                        offersLoadedSuccessfully
                        &&
                        rebates.length ===
                            0
                        ? (

                            <div
                                className="
                                    rounded-3xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-6
                                    py-8
                                    text-center
                                "
                            >

                                <div
                                    className="
                                        text-3xl
                                    "
                                >
                                    🎁
                                </div>


                                <h3
                                    className="
                                        mt-3
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    No eligible offers right now
                                </h3>


                                <p
                                    className="
                                        mt-2
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    You can still continue
                                    normally to payment.
                                </p>

                            </div>

                        )
                        : (

                            <div
                                className="
                                    space-y-3
                                "
                            >

                                {
                                    rebates.map(
                                        (
                                            rebate,
                                            index
                                        ) => {

                                            const isApplied =
                                                appliedRebate
                                                    ?.rebateCode ===
                                                rebate.code;


                                            const isApplying =
                                                applyingCode ===
                                                rebate.code;


                                            return (

                                                <article
                                                    key={
                                                        rebate.rebateId
                                                    }
                                                    className={`
                                                        rounded-3xl
                                                        border
                                                        bg-white
                                                        p-5

                                                        ${
                                                            isApplied
                                                                ? "border-green-300 ring-1 ring-green-100"
                                                                : index === 0
                                                                    ? "border-[#d9a64d]"
                                                                    : "border-[#eadfd6]"
                                                        }
                                                    `}
                                                >

                                                    <div
                                                        className="
                                                            flex
                                                            items-start
                                                            justify-between
                                                            gap-4
                                                        "
                                                    >

                                                        <div
                                                            className="
                                                                min-w-0
                                                            "
                                                        >

                                                            <div
                                                                className="
                                                                    flex
                                                                    flex-wrap
                                                                    items-center
                                                                    gap-2
                                                                "
                                                            >

                                                                <span
                                                                    className="
                                                                        rounded-full
                                                                        bg-[#fff2d2]
                                                                        px-2.5
                                                                        py-1
                                                                        text-[10px]
                                                                        font-bold
                                                                        uppercase
                                                                        tracking-wide
                                                                        text-[#7a1625]
                                                                    "
                                                                >
                                                                    {
                                                                        rebate.code
                                                                    }
                                                                </span>


                                                                {
                                                                    index ===
                                                                        0
                                                                    &&
                                                                    !isApplied
                                                                    && (

                                                                        <span
                                                                            className="
                                                                                rounded-full
                                                                                bg-green-100
                                                                                px-2.5
                                                                                py-1
                                                                                text-[10px]
                                                                                font-bold
                                                                                uppercase
                                                                                tracking-wide
                                                                                text-green-700
                                                                            "
                                                                        >
                                                                            Best saving
                                                                        </span>

                                                                    )
                                                                }


                                                                {
                                                                    isApplied
                                                                    && (

                                                                        <span
                                                                            className="
                                                                                rounded-full
                                                                                bg-green-100
                                                                                px-2.5
                                                                                py-1
                                                                                text-[10px]
                                                                                font-bold
                                                                                uppercase
                                                                                tracking-wide
                                                                                text-green-700
                                                                            "
                                                                        >
                                                                            Applied
                                                                        </span>

                                                                    )
                                                                }

                                                            </div>


                                                            <h3
                                                                className="
                                                                    mt-3
                                                                    text-lg
                                                                    font-bold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                {
                                                                    rebate.name
                                                                }
                                                            </h3>


                                                            <p
                                                                className="
                                                                    mt-1
                                                                    text-xs
                                                                    font-semibold
                                                                    text-[#c88a20]
                                                                "
                                                            >
                                                                {
                                                                    getRebateTypeLabel(
                                                                        rebate
                                                                    )
                                                                }
                                                            </p>


                                                            {
                                                                rebate.description
                                                                && (

                                                                    <p
                                                                        className="
                                                                            mt-2
                                                                            text-sm
                                                                            leading-6
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {
                                                                            rebate.description
                                                                        }
                                                                    </p>

                                                                )
                                                            }

                                                        </div>


                                                        <div
                                                            className="
                                                                shrink-0
                                                                text-right
                                                            "
                                                        >

                                                            <p
                                                                className="
                                                                    text-xs
                                                                    font-semibold
                                                                    uppercase
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Save
                                                            </p>


                                                            <p
                                                                className="
                                                                    mt-1
                                                                    text-xl
                                                                    font-extrabold
                                                                    text-green-700
                                                                "
                                                            >
                                                                {
                                                                    formatCurrency(
                                                                        rebate.rebateAmount
                                                                    )
                                                                }
                                                            </p>

                                                        </div>

                                                    </div>


                                                    <div
                                                        className="
                                                            mt-4
                                                            rounded-2xl
                                                            bg-[#fffaf3]
                                                            p-4
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

                                                            <span
                                                                className="
                                                                    text-sm
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Pay after offer
                                                            </span>


                                                            <span
                                                                className="
                                                                    font-extrabold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                {
                                                                    formatCurrency(
                                                                        rebate.payableAfterRebate
                                                                    )
                                                                }
                                                            </span>

                                                        </div>


                                                        {
                                                            rebate.minimumOrderAmount !==
                                                                null
                                                            && (

                                                                <p
                                                                    className="
                                                                        mt-2
                                                                        text-xs
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    Minimum order{" "}
                                                                    {
                                                                        formatCurrency(
                                                                            rebate.minimumOrderAmount
                                                                        )
                                                                    }
                                                                </p>

                                                            )
                                                        }


                                                        {
                                                            rebate.maximumDiscountAmount !==
                                                                null
                                                            && (

                                                                <p
                                                                    className="
                                                                        mt-1
                                                                        text-xs
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    Maximum saving{" "}
                                                                    {
                                                                        formatCurrency(
                                                                            rebate.maximumDiscountAmount
                                                                        )
                                                                    }
                                                                </p>

                                                            )
                                                        }


                                                        {
                                                            rebate.amountNeededForNextSlab !==
                                                                null
                                                            &&
                                                            rebate.nextSlabRebateAmount !==
                                                                null
                                                            &&
                                                            rebate.amountNeededForNextSlab >
                                                                0
                                                            && (

                                                                <div
                                                                    className="
                                                                        mt-3
                                                                        rounded-xl
                                                                        border
                                                                        border-[#f0d18f]
                                                                        bg-[#fff2d2]
                                                                        px-3
                                                                        py-3
                                                                    "
                                                                >

                                                                    <p
                                                                        className="
                                                                            text-xs
                                                                            font-semibold
                                                                            leading-5
                                                                            text-[#7a1625]
                                                                        "
                                                                    >
                                                                        Add{" "}
                                                                        {
                                                                            formatCurrency(
                                                                                rebate.amountNeededForNextSlab
                                                                            )
                                                                        }{" "}
                                                                        more to unlock{" "}
                                                                        {
                                                                            formatCurrency(
                                                                                rebate.nextSlabRebateAmount
                                                                            )
                                                                        }{" "}
                                                                        savings.
                                                                    </p>


                                                                    {
                                                                        rebate.nextSlabMinimumOrderAmount !==
                                                                            null
                                                                        && (

                                                                            <p
                                                                                className="
                                                                                    mt-1
                                                                                    text-[11px]
                                                                                    text-[#756763]
                                                                                "
                                                                            >
                                                                                Next reward unlocks at{" "}
                                                                                {
                                                                                    formatCurrency(
                                                                                        rebate.nextSlabMinimumOrderAmount
                                                                                    )
                                                                                }
                                                                                .
                                                                            </p>

                                                                        )
                                                                    }


                                                                    <button
                                                                        type="button"
                                                                        disabled={
                                                                            applyingCode !==
                                                                                null
                                                                            ||
                                                                            removing
                                                                        }
                                                                        onClick={
                                                                            handleAddMoreItems
                                                                        }
                                                                        className="
                                                                            mt-3
                                                                            inline-flex
                                                                            min-h-10
                                                                            items-center
                                                                            justify-center
                                                                            rounded-xl
                                                                            border
                                                                            border-[#7a1625]
                                                                            bg-white
                                                                            px-4
                                                                            text-xs
                                                                            font-bold
                                                                            text-[#7a1625]

                                                                            disabled:cursor-not-allowed
                                                                            disabled:opacity-60
                                                                        "
                                                                    >
                                                                        Add items to unlock offer
                                                                    </button>

                                                                </div>

                                                            )
                                                        }

                                                    </div>


                                                    {
                                                        isApplied
                                                        ? (

                                                            <button
                                                                type="button"
                                                                disabled
                                                                className="
                                                                    mt-4
                                                                    min-h-11
                                                                    w-full
                                                                    rounded-xl
                                                                    bg-green-100
                                                                    px-4
                                                                    text-sm
                                                                    font-bold
                                                                    text-green-700
                                                                "
                                                            >
                                                                ✓ Applied
                                                            </button>

                                                        )
                                                        : (

                                                            <button
                                                                type="button"
                                                                disabled={
                                                                    applyingCode !==
                                                                        null
                                                                    ||
                                                                    removing
                                                                }
                                                                onClick={
                                                                    () =>
                                                                        void handleApply(
                                                                            rebate
                                                                        )
                                                                }
                                                                className="
                                                                    mt-4
                                                                    min-h-11
                                                                    w-full
                                                                    rounded-xl
                                                                    bg-[#7a1625]
                                                                    px-4
                                                                    text-sm
                                                                    font-bold
                                                                    text-white

                                                                    disabled:cursor-not-allowed
                                                                    disabled:opacity-60
                                                                "
                                                            >
                                                                {
                                                                    isApplying
                                                                        ? "Applying..."
                                                                        : `Apply & save ${formatCurrency(
                                                                            rebate.rebateAmount
                                                                        )}`
                                                                }
                                                            </button>

                                                        )
                                                    }

                                                </article>
                                            );
                                        }
                                    )
                                }

                            </div>

                        )
                    }

                </div>


                {/* Continue */}

                <div
                    className="
                        mt-6
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
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                Amount to pay
                            </p>


                            {
                                appliedRebate
                                && (

                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            font-semibold
                                            text-green-700
                                        "
                                    >
                                        Offer saving applied
                                    </p>

                                )
                            }

                        </div>


                        <p
                            className="
                                text-2xl
                                font-extrabold
                                text-[#7a1625]
                            "
                        >
                            {
                                formatCurrency(
                                    displayTotal
                                )
                            }
                        </p>

                    </div>


                    <button
                        type="button"
                        disabled={
                            applyingCode !==
                                null
                            ||
                            removing
                        }
                        onClick={
                            handleContinueToPayment
                        }
                        className="
                            mt-5
                            min-h-12
                            w-full
                            rounded-xl
                            bg-[#7a1625]
                            px-5
                            font-bold
                            text-white

                            disabled:cursor-not-allowed
                            disabled:opacity-60
                        "
                    >
                        Continue to Payment
                    </button>


                    {
                        !appliedRebate
                        && (

                            <p
                                className="
                                    mt-3
                                    text-center
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                You can continue without applying
                                an offer.
                            </p>

                        )
                    }

                </div>


                <p
                    className="
                        mt-4
                        px-3
                        text-center
                        text-xs
                        leading-5
                        text-[#756763]
                    "
                >
                    Your pickup reservation stays active while
                    you review offers or add items. The timer
                    does not restart when the cart changes.
                </p>

            </section>

        </CheckoutExperienceFrame>
        </AppShell>
    );
}