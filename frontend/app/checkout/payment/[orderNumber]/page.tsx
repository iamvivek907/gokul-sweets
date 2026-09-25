"use client";

import {
    useCallback,
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
import ConfirmedPickupContext from "@/components/order/ConfirmedPickupContext";
import {formatBusinessTimestamp, parseBusinessTimestamp} from "@/lib/businessTime";

import {
    useCart
} from "@/hooks/useCart";

import {
    createCartFingerprint
} from "@/lib/cartFingerprint";

import {
    clearPendingOrder,
    getPendingOrderSnapshot,
    getServerPendingOrderSnapshot,
    parsePendingOrder,
    subscribeToPendingOrder
} from "@/lib/pendingOrderStorage";

import {
    clearPendingPayment,
    getPendingPaymentSnapshot,
    getServerPendingPaymentSnapshot,
    parsePendingPayment,
    savePendingPayment,
    subscribeToPendingPayment
} from "@/lib/pendingPaymentStorage";

import {
    openPaymentCheckout
} from "@/lib/paymentCheckout";

import {
    getCustomerOrder
} from "@/services/orderApi";

import {
    createPayment,
    getPaymentForOrder,
    refreshPayment
} from "@/services/paymentApi";

import type {
    PaymentResponse
} from "@/types/payment";


type PaymentStatusName =
    | "PENDING"
    | "PAID"
    | "FAILED"
    | "EXPIRED"
    | "REFUND_PENDING"
    | "REFUNDED"
    | "REFUND_FAILED"
    | string;


/*
 * =========================================================
 * HELPERS
 * =========================================================
 */

function getPaymentStatus(
    payment: PaymentResponse | null
): PaymentStatusName {

    return payment
        ? String(payment.paymentStatus)
        : "";
}


function formatCurrency(
    amount: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 2
        }
    ).format(amount);
}


function formatExpiry(
    value: string
): string {
    return formatBusinessTimestamp(value, {
        hour: "numeric", minute: "2-digit"
    });
}


function formatStatusLabel(
    status: PaymentStatusName
): string {

    switch (status) {

        case "PENDING":
            return "Payment pending";

        case "PAID":
            return "Paid";

        case "FAILED":
            return "Payment failed";

        case "EXPIRED":
            return "Payment expired";

        case "REFUND_PENDING":
            return "Refund in progress";

        case "REFUNDED":
            return "Refund completed";

        case "REFUND_FAILED":
            return "Refund needs attention";

        default:
            return status.replaceAll(
                "_",
                " "
            );
    }
}


function statusBadgeClass(
    status: PaymentStatusName
): string {

    switch (status) {

        case "PAID":
        case "REFUNDED":
            return "bg-green-50 text-green-700";

        case "REFUND_PENDING":
            return "bg-amber-50 text-amber-700";

        case "FAILED":
        case "EXPIRED":
        case "REFUND_FAILED":
            return "bg-red-50 text-red-700";

        default:
            return "bg-[#fff4e5] text-[#7a1625]";
    }
}


/*
 * =========================================================
 * PAYMENT RESPONSE MERGE
 * =========================================================
 *
 * refreshPayment() intentionally returns provider-specific
 * checkout fields as null because those fields are not
 * persisted in the Payment entity.
 *
 * Preserve the locally-known values when they exist.
 */

function mergePaymentResponse(
    refreshed: PaymentResponse,
    existing:
        | PaymentResponse
        | ReturnType<typeof parsePendingPayment>
        | null
): PaymentResponse {

    return {
        ...refreshed,

        providerPaymentId:
            refreshed.providerPaymentId
            ?? existing?.providerPaymentId
            ?? null,

        providerOrderId:
            refreshed.providerOrderId
            ?? existing?.providerOrderId
            ?? null,

        paymentSessionId:
            refreshed.paymentSessionId
            ?? existing?.paymentSessionId
            ?? null,

        paymentUrl:
            refreshed.paymentUrl
            ?? existing?.paymentUrl
            ?? null,

        checkoutKeyId:
            refreshed.checkoutKeyId
            ?? existing?.checkoutKeyId
            ?? null
    };
}


/*
 * =========================================================
 * PAYMENT INITIALIZATION PROMISES
 * =========================================================
 *
 * Prevents duplicate payment initialization for the same
 * order while the page is mounting.
 */

const paymentInitializationPromises =
    new Map<
        string,
        Promise<PaymentResponse | null>
    >();


/*
 * =========================================================
 * PAGE
 * =========================================================
 */

export default function PaymentPage() {

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
        items,
        clearCart
    } =
        useCart();


    /*
     * =========================================================
     * PENDING ORDER STORAGE
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
     * PENDING PAYMENT STORAGE
     * =========================================================
     */

    const pendingPaymentSnapshot =
        useSyncExternalStore(
            subscribeToPendingPayment,
            getPendingPaymentSnapshot,
            getServerPendingPaymentSnapshot
        );


    const storedPayment =
        useMemo(
            () =>
                parsePendingPayment(
                    pendingPaymentSnapshot
                ),
            [
                pendingPaymentSnapshot
            ]
        );


    /*
     * =========================================================
     * UI STATE
     * =========================================================
     */

    const [
        payment,
        setPayment
    ] =
        useState<PaymentResponse | null>(
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
        openingPayment,
        setOpeningPayment
    ] =
        useState(
            false
        );


    const [
        refreshing,
        setRefreshing
    ] =
        useState(
            false
        );


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    /*
     * =========================================================
     * CART FINGERPRINT
     * =========================================================
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
     * STORE PAYMENT LOCALLY
     * =========================================================
     */

    const persistPayment =
        useCallback(
            (
                response: PaymentResponse
            ) => {

                const fingerprint =
                    pendingOrder
                        ?.cartFingerprint
                    ??
                    currentCartFingerprint;


                savePendingPayment({
                    paymentId:
                        response.paymentId,

                    orderNumber:
                        response.orderNumber,

                    provider:
                        response.provider,

                    paymentStatus:
                        response.paymentStatus,

                    amount:
                        response.amount,

                    currency:
                        response.currency,

                    providerPaymentId:
                        response.providerPaymentId,

                    providerOrderId:
                        response.providerOrderId,

                    paymentSessionId:
                        response.paymentSessionId,

                    paymentUrl:
                        response.paymentUrl,

                    checkoutKeyId:
                        response.checkoutKeyId,

                    expiresAt:
                        response.expiresAt,

                    cartFingerprint:
                        fingerprint
                });


                setPayment(
                    response
                );
            },
            [
                currentCartFingerprint,
                pendingOrder
            ]
        );


    /*
     * =========================================================
     * PAID CHECKOUT CLEANUP
     * =========================================================
     */

    const completePaidPayment =
        useCallback(
            (
                response: PaymentResponse
            ) => {

                clearPendingPayment();

                clearPendingOrder();

                clearCart();


                if (
                    typeof window !==
                    "undefined"
                ) {

                    window.localStorage.removeItem(
                        "gokul-selected-pickup-slot"
                    );

                    window.localStorage.removeItem(
                        "gokul-customer-details"
                    );
                }


                router.replace(
                    `/orders/${encodeURIComponent(
                        response.orderNumber
                    )}`
                );
            },
            [
                clearCart,
                router
            ]
        );


    /*
     * =========================================================
     * APPLY BACKEND PAYMENT RESULT
     * =========================================================
     */

    const applyPaymentResult =
        useCallback(
            (
                response: PaymentResponse
            ) => {

                persistPayment(
                    response
                );


                const status =
                    String(
                        response.paymentStatus
                    );


                if (
                    status ===
                    "PAID"
                ) {

                    completePaidPayment(
                        response
                    );

                    return;
                }


                /*
                 * A terminal checkout must no longer remain the
                 * editable pending order.
                 *
                 * The payment itself remains persisted so late
                 * provider success/refund reconciliation can occur.
                 */

                if (
                    status ===
                        "FAILED"
                    ||
                    status ===
                        "EXPIRED"
                    ||
                    status ===
                        "REFUND_PENDING"
                    ||
                    status ===
                        "REFUNDED"
                    ||
                    status ===
                        "REFUND_FAILED"
                ) {

                    clearPendingOrder();
                }


                setError(
                    null
                );
            },
            [
                completePaidPayment,
                persistPayment
            ]
        );


    /*
     * =========================================================
     * INITIAL PAYMENT FLOW
     * =========================================================
     *
     * IMPORTANT:
     *
     * Browser localStorage is NOT authoritative.
     *
     * The order number in the URL is enough to recover an
     * existing payment from the backend.
     *
     * This is the critical PhonePe redirect recovery.
     */

    useEffect(
        () => {

            let cancelled =
                false;


            let initializationPromise =
                paymentInitializationPromises.get(
                    orderNumber
                );


            if (
                !initializationPromise
            ) {

                initializationPromise =
                    (
                        async () => {

                            /*
                             * -------------------------------------------------
                             * 1. LOCAL PAYMENT RECOVERY
                             * -------------------------------------------------
                             *
                             * Fast path for normal browser navigation.
                             */

                            if (
                                storedPayment
                                &&
                                storedPayment.orderNumber ===
                                    orderNumber
                            ) {

                                const refreshed =
                                    await refreshPayment(
                                        storedPayment.paymentId
                                    );


                                return mergePaymentResponse(
                                    refreshed,
                                    storedPayment
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 2. BACKEND PAYMENT RECOVERY
                             * -------------------------------------------------
                             *
                             * This is the important PhonePe/PWA fix.
                             *
                             * The backend returns:
                             *
                             * {
                             *     "payment": null
                             * }
                             *
                             * or:
                             *
                             * {
                             *     "payment": { ... }
                             * }
                             */

                            const lookup =
                                await getPaymentForOrder(
                                    orderNumber
                                );


                            const backendPayment =
                                lookup.payment;


                            if (
                                backendPayment
                                &&
                                backendPayment.orderNumber ===
                                    orderNumber
                            ) {

                                /*
                                 * A payment already exists.
                                 *
                                 * NEVER create another payment attempt.
                                 *
                                 * Ask the provider for the current state.
                                 */

                                const refreshed =
                                    await refreshPayment(
                                        backendPayment.paymentId
                                    );


                                return mergePaymentResponse(
                                    refreshed,
                                    backendPayment
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 3. CREATING A NEW PAYMENT REQUIRES LIVE CHECKOUT
                             * -------------------------------------------------
                             */

                            if (
                                !pendingOrder
                                ||
                                pendingOrder.orderNumber !==
                                    orderNumber
                            ) {

                                throw new Error(
                                    "No active checkout or saved payment was found for this order."
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 4. NEVER PAY A STALE CART
                             * -------------------------------------------------
                             */

                            if (
                                pendingOrder.cartFingerprint !==
                                currentCartFingerprint
                            ) {

                                return null;
                            }


                            /*
                             * -------------------------------------------------
                             * 5. CHECK BACKEND ORDER BEFORE CREATING PAYMENT
                             * -------------------------------------------------
                             */

                            const backendOrder =
                                await getCustomerOrder(
                                    orderNumber
                                );


                            const backendPaymentStatus =
                                backendOrder.paymentStatus ===
                                    null
                                    ? null
                                    : String(
                                        backendOrder.paymentStatus
                                    );


                            /*
                             * -------------------------------------------------
                             * 6. BACKEND ALREADY CONFIRMS SUCCESS
                             * -------------------------------------------------
                             */

                            if (
                                backendPaymentStatus ===
                                    "PAID"
                            ) {

                                clearPendingPayment();

                                clearPendingOrder();

                                clearCart();


                                if (
                                    typeof window !==
                                    "undefined"
                                ) {

                                    window.localStorage.removeItem(
                                        "gokul-selected-pickup-slot"
                                    );

                                    window.localStorage.removeItem(
                                        "gokul-customer-details"
                                    );
                                }


                                router.replace(
                                    `/orders/${encodeURIComponent(
                                        orderNumber
                                    )}`
                                );


                                return null;
                            }


                            /*
                             * -------------------------------------------------
                             * 7. SAFETY AGAINST DUPLICATE PAYMENT CREATION
                             * -------------------------------------------------
                             *
                             * Normally this branch should not be reached for
                             * a recoverable payment because step 2 already
                             * recovered it.
                             */

                            if (
                                backendPaymentStatus ===
                                    "PENDING"
                            ) {

                                throw new Error(
                                    "A payment attempt already exists for this order, but the payment record could not be recovered. Please check My Orders."
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 8. CHECK RESERVATION EXPIRY
                             * -------------------------------------------------
                             */

                            const reservationExpiresAtMs =
                                parseBusinessTimestamp(
                                    backendOrder.reservationExpiresAt
                                ).getTime();


                            const reservationExpired =
                                !Number.isFinite(
                                    reservationExpiresAtMs
                                )
                                ||
                                reservationExpiresAtMs <=
                                    Date.now();


                            if (
                                reservationExpired
                                &&
                                backendPaymentStatus ===
                                    null
                            ) {

                                clearPendingOrder();


                                throw new Error(
                                    "Your pickup reservation has expired. Your cart is still available, so please choose a pickup slot again."
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 9. ORDER MUST STILL BE WAITING FOR PAYMENT
                             * -------------------------------------------------
                             */

                            if (
                                backendOrder.orderStatus !==
                                "PENDING_PAYMENT"
                            ) {

                                clearPendingOrder();


                                throw new Error(
                                    `This order can no longer start a new payment because its status is ${backendOrder.orderStatus}.`
                                );
                            }


                            /*
                             * -------------------------------------------------
                             * 10. CREATE EXACTLY ONE NEW PAYMENT ATTEMPT
                             * -------------------------------------------------
                             *
                             * Provider intentionally omitted.
                             *
                             * Backend default provider controls selection.
                             */

                            return createPayment({
                                orderNumber
                            });
                        }
                    )();


                paymentInitializationPromises.set(
                    orderNumber,
                    initializationPromise
                );
            }


            async function applyInitialization():
                Promise<void> {

                try {

                    const response =
                        await initializationPromise;


                    if (
                        cancelled
                    ) {

                        return;
                    }


                    if (
                        !response
                    ) {

                        return;
                    }


                    applyPaymentResult(
                        response
                    );

                } catch (
                    exception
                ) {

                    if (
                        cancelled
                    ) {

                        return;
                    }


                    console.error(
                        "Unable to initialize payment:",
                        exception
                    );


                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to prepare payment."
                    );

                } finally {

                    paymentInitializationPromises.delete(
                        orderNumber
                    );


                    if (
                        !cancelled
                    ) {

                        setLoading(
                            false
                        );
                    }
                }
            }


            void applyInitialization();


            return () => {

                cancelled =
                    true;
            };

        },
        [
            applyPaymentResult,
            clearCart,
            currentCartFingerprint,
            orderNumber,
            pendingOrder,
            router,
            storedPayment
        ]
    );


    /*
     * =========================================================
     * REFRESH EXISTING PAYMENT
     * =========================================================
     */

    const refreshCurrentPayment =
        useCallback(
            async () => {

                if (
                    refreshing
                    ||
                    !payment
                ) {

                    return;
                }


                setRefreshing(
                    true
                );


                setError(
                    null
                );


                try {

                    const refreshed =
                        await refreshPayment(
                            payment.paymentId
                        );


                    const response =
                        mergePaymentResponse(
                            refreshed,
                            payment
                        );


                    applyPaymentResult(
                        response
                    );

                } catch (
                    exception
                ) {

                    console.error(
                        "Unable to refresh payment:",
                        exception
                    );


                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to check payment status."
                    );

                } finally {

                    setRefreshing(
                        false
                    );
                }
            },
            [
                applyPaymentResult,
                payment,
                refreshing
            ]
        );


    /*
     * =========================================================
     * PAYMENT STATUS
     * =========================================================
     */

    const paymentStatus =
        getPaymentStatus(
            payment
        );


    /*
     * =========================================================
     * PENDING PAYMENT AUTO REFRESH
     * =========================================================
     */

    useEffect(
        () => {

            if (
                paymentStatus !==
                    "PENDING"
                ||
                !payment
                ||
                openingPayment
            ) {

                return;
            }


            const timer =
                window.setInterval(
                    () => {

                        void refreshCurrentPayment();

                    },
                    5000
                );


            return () => {

                window.clearInterval(
                    timer
                );
            };

        },
        [
            openingPayment,
            payment,
            paymentStatus,
            refreshCurrentPayment
        ]
    );


    /*
     * =========================================================
     * REFUND PENDING AUTO REFRESH
     * =========================================================
     */

    useEffect(
        () => {

            if (
                paymentStatus !==
                    "REFUND_PENDING"
            ) {

                return;
            }


            const timer =
                window.setInterval(
                    () => {

                        void refreshCurrentPayment();

                    },
                    10000
                );


            return () => {

                window.clearInterval(
                    timer
                );
            };

        },
        [
            paymentStatus,
            refreshCurrentPayment
        ]
    );


    /*
     * =========================================================
     * OPEN PAYMENT CHECKOUT
     * =========================================================
     */

    async function handlePayNow():
        Promise<void> {

        if (
            !payment
        ) {

            return;
        }


        if (
            getPaymentStatus(
                payment
            ) !==
            "PENDING"
        ) {

            setError(
                "This payment is no longer pending."
            );

            return;
        }


        if (
            cartChanged
        ) {

            setError(
                "Your cart has changed since this backend order was created."
            );

            return;
        }


        /*
         * If PhonePe was recovered from the backend after the
         * browser lost localStorage, the PaymentResponse does
         * not contain paymentUrl because that URL is not stored
         * in the Payment entity.
         *
         * Do not attempt to create another payment.
         * Keep checking the existing provider payment instead.
         */

        if (
            payment.provider ===
                "PHONEPE"
            &&
            !payment.paymentUrl
        ) {

            setError(
                "Your existing PhonePe payment is being checked. Please wait for the provider status to update."
            );


            await refreshCurrentPayment();

            return;
        }


        setOpeningPayment(
            true
        );


        setError(
            null
        );


        try {

            const outcome =
                await openPaymentCheckout(
                    payment
                );


            if (
                outcome.kind ===
                "updated"
            ) {

                applyPaymentResult(
                    outcome.payment
                );

            } else if (
                outcome.kind ===
                "failed"
            ) {

                setError(
                    outcome.message
                );

                await refreshCurrentPayment();

            } else {

                /*
                 * Closing the payment gateway does not cancel
                 * the backend payment.
                 */

                await refreshCurrentPayment();
            }

        } catch (
            exception
        ) {

            console.error(
                "Unable to open payment checkout:",
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to open payment checkout."
            );

        } finally {

            setOpeningPayment(
                false
            );
        }
    }


    /*
     * =========================================================
     * START FRESH CHECKOUT
     * =========================================================
     */

    function handleChooseNewPickupSlot() {

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
                        max-w-lg
                        pb-10
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
                                mx-auto
                                h-10
                                w-10
                                animate-spin
                                rounded-full
                                border-4
                                border-[#eadfd6]
                                border-t-[#7a1625]
                            "
                        />


                        <p
                            className="
                                mt-5
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Preparing secure payment...
                        </p>


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            We are checking the latest order
                            and payment status with the backend.
                        </p>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * NO PAYMENT AVAILABLE
     * =========================================================
     */

    if (
        !payment
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
                            border-[#eadfd6]
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
                            💳
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Payment could not be prepared
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            Your cart has not been cleared.
                        </p>


                        {
                            error
                            && (

                                <div
                                    className="
                                        mt-5
                                        rounded-xl
                                        border
                                        border-red-100
                                        bg-red-50
                                        p-4
                                        text-left
                                    "
                                >

                                    <p
                                        className="
                                            text-sm
                                            font-semibold
                                            text-red-700
                                        "
                                    >
                                        {error}
                                    </p>

                                </div>

                            )
                        }


                        <button
                            type="button"
                            onClick={
                                () =>
                                    router.push(
                                        "/orders"
                                    )
                            }
                            className="
                                mt-6
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-6
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Check My Orders
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
     * STATUS MODEL
     * =========================================================
     */

    const status =
        getPaymentStatus(
            payment
        );


    const isPending =
        status ===
        "PENDING";


    const isFailed =
        status ===
        "FAILED";


    const isExpired =
        status ===
        "EXPIRED";


    const isRefundPending =
        status ===
        "REFUND_PENDING";


    const isRefunded =
        status ===
        "REFUNDED";


    const isRefundFailed =
        status ===
        "REFUND_FAILED";


    /*
     * =========================================================
     * PHONEPE RECOVERY WITHOUT LOCAL CHECKOUT URL
     * =========================================================
     */

    const phonePeStatusOnly =
        isPending
        &&
        payment.provider ===
            "PHONEPE"
        &&
        !payment.paymentUrl;


    /*
     * =========================================================
     * PAGE
     * =========================================================
     */

    return (

        <AppShell>

            <section
                className="
                    mx-auto
                    max-w-lg
                    pb-10
                "
            >

                <ConfirmedPickupContext orderNumber={orderNumber} />

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
                        Secure checkout
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
                        Payment
                    </h1>


                    <p
                        className="
                            mt-2
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Order{" "}
                        <span
                            className="
                                font-semibold
                                text-[#241715]
                            "
                        >
                            {orderNumber}
                        </span>
                    </p>

                </div>


                {
                    cartChanged
                    &&
                    isPending
                    && (

                        <div
                            className="
                                mb-5
                                rounded-2xl
                                border
                                border-amber-200
                                bg-amber-50
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-bold
                                    text-amber-800
                                "
                            >
                                Your cart changed after this
                                payment was prepared.
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    leading-6
                                    text-amber-700
                                "
                            >
                                Do not pay this stale checkout.
                                Return to the cart and continue
                                checkout so the backend can
                                validate the current items.
                            </p>

                        </div>

                    )
                }


                {
                    phonePeStatusOnly
                    && (

                        <div
                            className="
                                mb-5
                                rounded-2xl
                                border
                                border-blue-100
                                bg-blue-50
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-bold
                                    text-blue-800
                                "
                            >
                                Existing PhonePe payment found
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    leading-6
                                    text-blue-700
                                "
                            >
                                We recovered the payment from the
                                backend. The browser no longer has
                                the original checkout link, so we
                                will continue checking the existing
                                PhonePe payment instead of creating
                                a duplicate payment.
                            </p>

                        </div>

                    )
                }


                {
                    isRefundPending
                    && (

                        <div
                            className="
                                mb-5
                                rounded-3xl
                                border
                                border-amber-200
                                bg-amber-50
                                p-6
                            "
                        >

                            <div
                                className="
                                    flex
                                    items-start
                                    gap-4
                                "
                            >

                                <div
                                    className="
                                        flex
                                        h-11
                                        w-11
                                        shrink-0
                                        items-center
                                        justify-center
                                        rounded-full
                                        bg-white
                                        text-xl
                                    "
                                >
                                    ↩️
                                </div>


                                <div>

                                    <h2
                                        className="
                                            text-lg
                                            font-bold
                                            text-amber-900
                                        "
                                    >
                                        Refund in progress
                                    </h2>


                                    <p
                                        className="
                                            mt-2
                                            text-sm
                                            leading-6
                                            text-amber-800
                                        "
                                    >
                                        The payment provider reported a successful
                                        payment after this order had
                                        already become terminal. We
                                        did not restore the cancelled
                                        pickup reservation. A full
                                        refund for{" "}
                                        <strong>
                                            {
                                                formatCurrency(
                                                    payment.amount
                                                )
                                            }
                                        </strong>{" "}
                                        is being processed.
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            text-xs
                                            leading-5
                                            text-amber-700
                                        "
                                    >
                                        This page checks our backend
                                        automatically. You can also
                                        check again manually.
                                    </p>

                                </div>

                            </div>

                        </div>

                    )
                }


                {
                    isRefunded
                    && (

                        <div
                            className="
                                mb-5
                                rounded-3xl
                                border
                                border-green-200
                                bg-green-50
                                p-6
                            "
                        >

                            <div
                                className="
                                    text-3xl
                                "
                            >
                                ✅
                            </div>


                            <h2
                                className="
                                    mt-3
                                    text-xl
                                    font-bold
                                    text-green-800
                                "
                            >
                                Refund completed
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-green-700
                                "
                            >
                                The backend has confirmed the refund
                                for{" "}
                                <strong>
                                    {
                                        formatCurrency(
                                            payment.amount
                                        )
                                    }
                                </strong>
                                . The old order remains cancelled or
                                failed, and its pickup slot stays
                                released.
                            </p>

                        </div>

                    )
                }


                {
                    isRefundFailed
                    && (

                        <div
                            className="
                                mb-5
                                rounded-3xl
                                border
                                border-red-200
                                bg-red-50
                                p-6
                            "
                        >

                            <div
                                className="
                                    text-3xl
                                "
                            >
                                ⚠️
                            </div>


                            <h2
                                className="
                                    mt-3
                                    text-xl
                                    font-bold
                                    text-red-800
                                "
                            >
                                Refund needs attention
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                The automatic refund did not reach
                                a successful final state. Your old
                                order has not been restored and no
                                pickup slot has been re-reserved.
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    font-semibold
                                    text-red-800
                                "
                            >
                                Please keep this order number for
                                support: {orderNumber}
                            </p>

                        </div>

                    )
                }


                {
                    isFailed
                    && (

                        <div
                            className="
                                mb-5
                                rounded-3xl
                                border
                                border-red-200
                                bg-red-50
                                p-6
                            "
                        >

                            <div
                                className="
                                    text-3xl
                                "
                            >
                                ❌
                            </div>


                            <h2
                                className="
                                    mt-3
                                    text-xl
                                    font-bold
                                    text-red-800
                                "
                            >
                                Payment failed
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                The order is no longer holding its
                                pickup reservation. Your cart is
                                still available.
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-xs
                                    leading-5
                                    text-red-600
                                "
                            >
                                If the payment provider later reports that money
                                was actually captured, the backend
                                automatically starts the refund
                                reconciliation flow.
                            </p>

                        </div>

                    )
                }


                {
                    isExpired
                    && (

                        <div
                            className="
                                mb-5
                                rounded-3xl
                                border
                                border-red-200
                                bg-red-50
                                p-6
                            "
                        >

                            <div
                                className="
                                    text-3xl
                                "
                            >
                                ⏱️
                            </div>


                            <h2
                                className="
                                    mt-3
                                    text-xl
                                    font-bold
                                    text-red-800
                                "
                            >
                                Payment time expired
                            </h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                The old pickup reservation has been
                                released. Your cart is still safe,
                                so you can choose a new pickup slot.
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-xs
                                    leading-5
                                    text-red-600
                                "
                            >
                                If the payment provider later reports a captured
                                payment, the backend will move it
                                into automatic refund reconciliation
                                instead of confirming this expired
                                order.
                            </p>

                        </div>

                    )
                }


                <div
                    className="
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-6
                        shadow-sm
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
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#c88a20]
                                "
                            >
                                Amount
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-3xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {
                                    formatCurrency(
                                        payment.amount
                                    )
                                }
                            </p>

                        </div>


                        <span
                            className="
                                rounded-full
                                bg-[#fff4e5]
                                px-3
                                py-1
                                text-xs
                                font-bold
                                text-[#7a1625]
                            "
                        >
                            {
                                payment.provider ===
                                    "RAZORPAY"
                                    ? "Razorpay"
                                    : payment.provider ===
                                        "PAYTM"
                                        ? "Paytm"
                                        : "PhonePe"
                            }
                        </span>

                    </div>


                    <div
                        className="
                            mt-6
                            rounded-2xl
                            border
                            border-[#eadfd6]
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
                                Payment status
                            </span>


                            <span
                                className={`
                                    rounded-full
                                    px-3
                                    py-1
                                    text-xs
                                    font-bold
                                    ${statusBadgeClass(
                                        status
                                    )}
                                `}
                            >
                                {
                                    formatStatusLabel(
                                        status
                                    )
                                }
                            </span>

                        </div>


                        <div
                            className="
                                mt-3
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
                                Payment ID
                            </span>


                            <span
                                className="
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                #{payment.paymentId}
                            </span>

                        </div>


                        {
                            payment.providerPaymentId
                            && (

                                <div
                                    className="
                                        mt-3
                                        flex
                                        items-start
                                        justify-between
                                        gap-4
                                    "
                                >

                                    <span
                                        className="
                                            shrink-0
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        Provider transaction
                                    </span>


                                    <span
                                        className="
                                            break-all
                                            text-right
                                            text-xs
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            payment.providerPaymentId
                                        }
                                    </span>

                                </div>

                            )
                        }


                        {
                            payment.expiresAt
                            &&
                            isPending
                            && (

                                <div
                                    className="
                                        mt-3
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
                                        Payment deadline
                                    </span>


                                    <span
                                        className="
                                            text-sm
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatExpiry(
                                                payment.expiresAt
                                            )
                                        }
                                    </span>

                                </div>

                            )
                        }

                    </div>


                    {
                        isPending
                        && (

                            <div
                                className="
                                    mt-5
                                    rounded-2xl
                                    border
                                    border-blue-100
                                    bg-blue-50
                                    p-4
                                "
                            >

                                <p
                                    className="
                                        text-sm
                                        font-semibold
                                        text-blue-800
                                    "
                                >
                                    {
                                        phonePeStatusOnly
                                            ? "Your existing PhonePe payment is being checked."
                                            : "Closing the payment window does not cancel your order immediately."
                                    }
                                </p>


                                <p
                                    className="
                                        mt-1
                                        text-xs
                                        leading-5
                                        text-blue-700
                                    "
                                >
                                    {
                                        phonePeStatusOnly
                                            ? "We found the payment on the backend and will not create a duplicate payment. The status is checked automatically."
                                            : "We always verify the payment through the backend. A pending attempt remains active until the provider reports a final result or the backend payment deadline is reached."
                                    }
                                </p>

                            </div>

                        )
                    }


                    {
                        isPending
                        &&
                        !phonePeStatusOnly
                        && (

                            <button
                                type="button"
                                disabled={
                                    openingPayment
                                    ||
                                    refreshing
                                    ||
                                    cartChanged
                                }
                                onClick={
                                    () =>
                                        void handlePayNow()
                                }
                                className="
                                    mt-6
                                    flex
                                    min-h-14
                                    w-full
                                    items-center
                                    justify-center
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
                                    openingPayment
                                        ? "Opening payment..."
                                        : `Pay ${formatCurrency(
                                            payment.amount
                                        )}`
                                }
                            </button>

                        )
                    }


                    {
                        (
                            isPending
                            ||
                            isRefundPending
                            ||
                            isRefundFailed
                            ||
                            isFailed
                            ||
                            isExpired
                        )
                        && (

                            <button
                                type="button"
                                disabled={
                                    refreshing
                                }
                                onClick={
                                    () =>
                                        void refreshCurrentPayment()
                                }
                                className="
                                    mt-3
                                    flex
                                    min-h-11
                                    w-full
                                    items-center
                                    justify-center
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-sm
                                    font-semibold
                                    text-[#7a1625]
                                    transition
                                    hover:bg-[#fffaf3]
                                    disabled:cursor-not-allowed
                                    disabled:opacity-50
                                "
                            >
                                {
                                    refreshing
                                        ? "Checking latest status..."
                                        : isRefundPending
                                            ? "Check Refund Status"
                                            : "Check Payment Status"
                                }
                            </button>

                        )
                    }


                    {
                        (
                            isFailed
                            ||
                            isExpired
                        )
                        && (

                            <button
                                type="button"
                                onClick={
                                    handleChooseNewPickupSlot
                                }
                                className="
                                    mt-3
                                    min-h-12
                                    w-full
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-6
                                    font-bold
                                    text-white
                                    transition
                                    hover:bg-[#5d0f1b]
                                "
                            >
                                Choose New Pickup Slot
                            </button>

                        )
                    }


                    {
                        (
                            isRefundPending
                            ||
                            isRefunded
                            ||
                            isRefundFailed
                            ||
                            isFailed
                            ||
                            isExpired
                        )
                        && (

                            <button
                                type="button"
                                onClick={
                                    () =>
                                        router.push(
                                            `/orders/${encodeURIComponent(
                                                orderNumber
                                            )}`
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
                                View Order Details
                            </button>

                        )
                    }


                    {
                        isRefunded
                        && (

                            <button
                                type="button"
                                onClick={
                                    () =>
                                        router.push(
                                            "/cart"
                                        )
                                }
                                className="
                                    mt-4
                                    min-h-12
                                    w-full
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-6
                                    font-bold
                                    text-white
                                "
                            >
                                Return to Cart
                            </button>

                        )
                    }


                    {
                        isRefundFailed
                        && (

                            <div
                                className="
                                    mt-5
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
                                        text-red-800
                                    "
                                >
                                    Do not retry the old payment.
                                </p>


                                <p
                                    className="
                                        mt-1
                                        text-xs
                                        leading-5
                                        text-red-700
                                    "
                                >
                                    The payment/refund record is
                                    preserved for reconciliation.
                                    Keep order number {orderNumber}
                                    available when contacting the
                                    shop.
                                </p>

                            </div>

                        )
                    }


                    {
                        error
                        && (

                            <div
                                className="
                                    mt-5
                                    rounded-xl
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
                                    {error}
                                </p>

                            </div>

                        )
                    }

                </div>


                <p
                    className="
                        mt-4
                        text-center
                        text-xs
                        leading-5
                        text-[#756763]
                    "
                >
                    Your order is confirmed only after
                    our backend verifies a successful
                    payment. Closing or going back
                    from the payment window does not directly cancel
                    the order.
                </p>

            </section>

        </AppShell>
    );
}
