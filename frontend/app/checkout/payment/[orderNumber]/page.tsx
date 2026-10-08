"use client";
import Link from "next/link";
import {mergePaymentResponse, refreshKnownPayment} from "@/lib/paymentRecovery";
import {getCartSnapshot} from "@/lib/cartStorage";

import BrandLoading from "@/components/common/BrandLoading";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {T,useTranslation} from "@/lib/language";
import PaymentLeaveChoice from "@/components/checkout/PaymentLeaveChoice";

import {
    useCallback,
    useEffect,
    useLayoutEffect,
    useMemo,
    useRef,
    useState,
    useSyncExternalStore
} from "react";

import {
    useParams,
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";
import MobilePaymentCancelDialog from "@/components/checkout/MobilePaymentCancelDialog";
import CheckoutExperienceFrame from "@/components/checkout/CheckoutExperienceFrame";
import ConfirmedPickupContext from "@/components/order/ConfirmedPickupContext";
import {formatBusinessTimestamp, parseBusinessTimestamp} from "@/lib/businessTime";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {usePaymentPolling, type PaymentPollResult} from "@/hooks/usePaymentPolling";
import {hasOpenedPaymentGateway, markPaymentGatewayOpened, clearPaymentGatewayVisit} from "@/lib/paymentGatewayVisit";
import {MIN_MANUAL_PAYMENT_CHECK_MS, isTemporaryPaymentFailure} from "@/lib/paymentPolling";
import {ApiError} from "@/services/apiClient";
import {reconcilePaidCart} from "@/lib/paidCartRecovery";

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
    cancelPaymentCheckout,
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
    const translate = useTranslation();
    const {features, error: configurationError} = useStorefrontConfiguration();
    const paidCartRecovery = features?.paidCartRecovery;
    const paymentPollingV2 = features?.paymentPollingV2 === true;

    const router =
        useRouter();
    const phone = usePhoneViewport();


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

    const deliveryOrder = pendingOrder?.orderNumber === orderNumber &&
        pendingOrder.fulfillmentType === "DELIVERY";


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
    const [pollingNotice, setPollingNotice] = useState<string | null>(null);
    const [gatewayOpened, setGatewayOpened] = useState(false);
    const initializedPaymentRef = useRef<{orderNumber: string; paymentId: number} | null>(null);
    const refreshInFlightRef = useRef(false);
    // A settled response stops any timer callback already queued before React
    // has committed the corresponding status update.
    const settledPaymentIdRef = useRef<number | null>(null);
    const nextAllowedCheckRef = useRef(0);
    const [paymentClock, setPaymentClock] = useState(0);
    const activePaymentId = payment?.paymentId;
    useEffect(() => {
        if (!paymentPollingV2 || !activePaymentId) return;
        const tick = () => setPaymentClock(Date.now());
        tick();
        const timer = window.setInterval(tick, 15_000);
        return () => window.clearInterval(timer);
    }, [paymentPollingV2, activePaymentId]);


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
                // Storage notifies subscribers synchronously. Mark this payment
                // before writing so initialization cannot restart on that update.
                initializedPaymentRef.current = {orderNumber: response.orderNumber, paymentId: response.paymentId};

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

                if (paidCartRecovery !== false) {
                    reconcilePaidCart(response.orderNumber);
                } else {
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
                }


                router.replace(
                    `/orders/${encodeURIComponent(
                        response.orderNumber
                    )}`
                );
            },
            [
                clearCart,
                paidCartRecovery,
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
                settledPaymentIdRef.current = response.paymentStatus === "PENDING"
                    ? null : response.paymentId;

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
                    clearPaymentGatewayVisit(response.orderNumber);

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
                    clearPaymentGatewayVisit(response.orderNumber);

                    if (!deliveryOrder) clearPendingOrder();
                }


                setError(
                    null
                );
            },
            [
                completePaidPayment,
                deliveryOrder,
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
            // Wait for the rollout setting before recovering a payment. An initial
            // null must never start the legacy five-second provider polling.
            if (!features && !configurationError) return;
            if (initializedPaymentRef.current?.orderNumber === orderNumber) return;

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

                                return refreshKnownPayment(storedPayment, paymentPollingV2);
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

                                return refreshKnownPayment(backendPayment, paymentPollingV2);
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

                            if(pendingOrder.offerRecheckRequired||pendingOrder.priceReviewRequired){
                                router.replace(`/checkout/offers/${encodeURIComponent(orderNumber)}`);
                                return null;
                            }

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

                                if (paidCartRecovery !== false) {
                                    reconcilePaidCart(orderNumber);
                                } else {
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

                                if (!deliveryOrder) clearPendingOrder();


                                throw new Error(
                                    deliveryOrder
                                        ? "Your delivery reservation has expired. Your cart is still available, so please choose another rider window."
                                        : "Your pickup reservation has expired. Your cart is still available, so please choose a pickup slot again."
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

                                if (!deliveryOrder) clearPendingOrder();


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
                    setGatewayOpened(hasOpenedPaymentGateway(response.orderNumber, response.paymentId));

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
            paidCartRecovery,
            features,
            configurationError,
            paymentPollingV2,
            currentCartFingerprint,
            deliveryOrder,
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
            async (automatic = false): Promise<PaymentPollResult> => {

                if (automatic && payment && (payment.paymentStatus !== "PENDING" ||
                    settledPaymentIdRef.current === payment.paymentId)) {
                    return {success: true, permanent: true};
                }

                if (
                    (paymentPollingV2 ? refreshInFlightRef.current : refreshing)
                    ||
                    !payment
                ) {
                    return undefined;
                }

                if (paymentPollingV2 && Date.now() < nextAllowedCheckRef.current) {
                    if (!automatic) setPollingNotice("We checked recently. Please wait a moment before checking again.");
                    return {success: false, retryAfterMs: nextAllowedCheckRef.current - Date.now()};
                }

                refreshInFlightRef.current = true;


                setRefreshing(
                    true
                );


                if (!automatic || !paymentPollingV2) setError(null);


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
                    const terminal = response.paymentStatus !== "PENDING";
                    if (paymentPollingV2) {
                        nextAllowedCheckRef.current = Date.now() + MIN_MANUAL_PAYMENT_CHECK_MS;
                        setPollingNotice(null);
                    }
                    return {success: true, permanent: terminal};

                } catch (
                    exception
                ) {

                    console.error(
                        "Unable to refresh payment:",
                        exception
                    );


                    if (paymentPollingV2 && exception instanceof ApiError && isTemporaryPaymentFailure(exception.status)) {
                        const delay = Math.max(30_000, exception.retryAfterMs ?? 0);
                        nextAllowedCheckRef.current = Date.now() + delay;
                        setPollingNotice("We couldn't confirm the latest payment status yet. Your payment may still be processing. We'll check again shortly.");
                        setError(null);
                        return {success: false, retryAfterMs: delay};
                    }
                    setError(exception instanceof Error ? exception.message : "Unable to check payment status.");
                    return {success: false, permanent: paymentPollingV2};

                } finally {

                    setRefreshing(
                        false
                    );
                    refreshInFlightRef.current = false;
                }
            },
            [
                applyPaymentResult,
                payment,
                paymentPollingV2,
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

    const paymentDeadlineMs = payment ? parseBusinessTimestamp(payment.expiresAt).getTime() : 0;
    usePaymentPolling(paymentPollingV2 && gatewayOpened, payment?.paymentId, paymentStatus,
        Number.isFinite(paymentDeadlineMs) ? paymentDeadlineMs : 0,
        openingPayment, () => refreshCurrentPayment(true),
        () => setPollingNotice("Automatic status checks are paused after repeated connection errors. You can check again using the button below."));


    /*
     * =========================================================
     * PENDING PAYMENT AUTO REFRESH
     * =========================================================
     */

    useEffect(
        () => {

            if (paymentPollingV2) return;
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

                        void refreshCurrentPayment(true);

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
            paymentPollingV2,
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

            if (paymentPollingV2) return;
            if (
                paymentStatus !==
                    "REFUND_PENDING"
            ) {

                return;
            }


            const timer =
                window.setInterval(
                    () => {

                        // Refund settlement has its own lifecycle; a payment
                        // failure guard must not suppress refund reconciliation.
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
            paymentPollingV2,
            paymentStatus,
            refreshCurrentPayment
        ]
    );


    /*
     * =========================================================
     * OPEN PAYMENT CHECKOUT
     * =========================================================
     */

    const [feeBreakdown,setFeeBreakdown]=useState<{fee:number;tax:number;paymentFee:number;paymentTax:number;paymentRate:number}|null>(null);
    useEffect(()=>{let alive=true;getCustomerOrder(orderNumber).then(order=>{if(alive)setFeeBreakdown({fee:order.convenienceFee??0,tax:order.convenienceFeeTax??0,paymentFee:order.paymentFee??0,paymentTax:order.paymentFeeTax??0,paymentRate:order.paymentFeeRate??0});}).catch(()=>{});return()=>{alive=false;};},[orderNumber]);
    const [cancelling,setCancelling]=useState(false);
    const cancellationRunning=useRef(false);
    const [confirmCancel,setConfirmCancel]=useState(false);
    async function cancelCheckout(destination?:string) {
        if(!payment || cancellationRunning.current || paymentLaunchRunning.current || openingPayment || refreshing) return;
        cancellationRunning.current=true;
        setCancelling(true);setError(null);
        const signal=AbortSignal.timeout(15000);
        try {
            const recoveryOrder=await getCustomerOrder(payment.orderNumber,signal);
            const result=await cancelPaymentCheckout(payment.paymentId,signal);
            applyPaymentResult(result);
            if(result.paymentStatus==="EXPIRED" || result.paymentStatus==="FAILED") {
                clearPendingPayment();clearPendingOrder();clearPaymentGatewayVisit(payment.orderNumber);
                router.replace(destination ?? (recoveryOrder.fulfillmentType === "DELIVERY" ? "/delivery/check" : `${window.matchMedia("(max-width: 640px)").matches&&localStorage.getItem(`gokul-mobile-checkout:${payment.orderNumber}`)==="1"?"/checkout/mobile":"/checkout/review"}${window.matchMedia("(max-width: 640px)").matches ? `?paymentRecovery=${result.paymentStatus.toLowerCase()}` : ""}`));
            } else {setConfirmCancel(false);setError("Payment was already confirmed. View this order before starting another checkout.");}
        } catch(error) {setError(signal.aborted ? "The payment check took too long. We haven’t confirmed whether cancellation completed. Stay here or retry to check the latest status." : error instanceof Error ? error.message : "Could not check payment. Stay here or retry to check the latest status.");}
        finally {cancellationRunning.current=false;setCancelling(false);}
    }

    const paymentLaunchRunning = useRef(false);

    // Keep the restore subscription stable while other payment reads update
    // state. A render must not invalidate a status check already in flight.
    const recoveryState = useRef({payment, paymentPollingV2, applyPaymentResult});
    useLayoutEffect(() => { recoveryState.current = {payment, paymentPollingV2, applyPaymentResult}; }, [payment, paymentPollingV2, applyPaymentResult]);
    useLayoutEffect(() => {
        let alive = true;
        const restored = (event: PageTransitionEvent) => {
            const state = recoveryState.current;
            if (!event.persisted || !state.payment || state.payment.orderNumber !== orderNumber) return;
            void refreshKnownPayment(state.payment, state.paymentPollingV2)
                .then(current => { if (alive) recoveryState.current.applyPaymentResult(current); })
                .catch(error => { if (alive) setError(error instanceof Error ? error.message : "Unable to check payment. Please check My Orders."); });
        };
        window.addEventListener("pageshow", restored);
        return () => { alive = false; window.removeEventListener("pageshow", restored); };
    }, [orderNumber]);

    async function handlePayNow():
        Promise<void> {

        if (
            !payment || payment.orderNumber !== orderNumber || cancelling || cancellationRunning.current || refreshing || paymentLaunchRunning.current
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


        paymentLaunchRunning.current = true;
        setOpeningPayment(true);
        setError(null);
        let gatewayInvoked = false;
        try {
            const cartBeforeCheck = getCartSnapshot();
            const checkoutBeforeCheck = getPendingOrderSnapshot();
            const current = await refreshKnownPayment(payment, paymentPollingV2, {allowTemporaryFallback: false});
            applyPaymentResult(current);
            if (current.paymentStatus !== "PENDING") return;
            if (cartBeforeCheck !== getCartSnapshot() || checkoutBeforeCheck !== getPendingOrderSnapshot()) {
                setError("Your checkout changed while payment was being checked. Review your order before paying.");
                return;
            }
            if (current.paymentId !== payment.paymentId || current.provider !== payment.provider || current.amount !== payment.amount || current.currency !== payment.currency) {
                setError("Payment details changed. Review them before continuing.");
                return;
            }
            const expiresAt = parseBusinessTimestamp(current.expiresAt).getTime();
            if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
                setError("This payment window has closed. Please check its status before starting another checkout.");
                return;
            }
            if (current.provider === "PHONEPE" && !current.paymentUrl) {
                setError("Your existing PhonePe payment is being checked. Please check My Orders before paying again.");
                return;
            }
            if (paymentPollingV2) {
                markPaymentGatewayOpened(current.orderNumber, current.paymentId);
                setGatewayOpened(true);
            }
            gatewayInvoked = true;
            const outcome = await openPaymentCheckout(current);

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
                if (paymentPollingV2) {
                    clearPaymentGatewayVisit(payment.orderNumber);
                    setGatewayOpened(false);
                }

                setError(
                    outcome.message
                );

                if (!paymentPollingV2) await refreshCurrentPayment();

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
            if (paymentPollingV2 && gatewayInvoked) {
                clearPaymentGatewayVisit(payment.orderNumber);
                setGatewayOpened(false);
            }

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
            paymentLaunchRunning.current = false;
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


        router.push(deliveryOrder ? "/delivery/check" : "/checkout/pickup");
    }


    /*
     * =========================================================
     * LOADING
     * =========================================================
     */

    if (phone && (loading || payment?.paymentStatus === "PAID") && features?.simplifiedCheckout && features.checkoutExperienceV2 && features.acceptedCheckoutQuote) return <AppShell showSocialPopup={false}>
        <BrandLoading fullscreen className="payment-status-loading" label="Checking your payment…" detail="Waiting for the payment provider to confirm your payment. Please keep this page open." />
    </AppShell>;

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

                        <BrandLoading label="Preparing secure payment..." detail="Checking your order and connecting to the payment provider. Please keep this page open." />


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            <T text="We are checking the latest order and payment status with the backend." /></p>

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
                            <T text="Payment could not be prepared" /></h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            <T text="Your cart has not been cleared." /></p>


                        {
                            error
                            && (

                                <div
                                    role="alert"
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
                                        {translate(error)}
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
                            <T text="Check My Orders" /></button>


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
                            <T text="Return to Cart" /></button>

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

    const paymentDeadlineReached = paymentPollingV2 && isPending && paymentClock > 0 &&
        Number.isFinite(paymentDeadlineMs) && paymentClock >= paymentDeadlineMs;


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

    if (phone && features?.simplifiedCheckout && features.checkoutExperienceV2 && features.acceptedCheckoutQuote && isPending) return <AppShell showSocialPopup={false}>
        <PaymentLeaveChoice autoCancel canStayWhileBusy={!cancelling} active={isPending} busy={cancelling || refreshing || openingPayment} error={translate(error)} onCancel={cancelCheckout} />
        <MobilePaymentCancelDialog active={confirmCancel} busy={cancelling || refreshing || openingPayment} error={error} onKeep={() => setConfirmCancel(false)} onCancel={cancelCheckout} />
        <section className="mobile-order-detail"><nav><Link href="/orders">← My orders</Link><Link href={`/orders/${encodeURIComponent(orderNumber)}`}>View order</Link></nav>
        <header><h1><T text="Checking your payment…" /></h1><p>{orderNumber}</p></header>
        <section className="mobile-order-panel" role="status"><p><T text="Your cart is saved. Your order opens automatically once payment is confirmed." /></p><strong>{formatCurrency(payment.amount)}</strong>
        {paymentDeadlineReached && <p><T text="This payment window has closed. Check payment before starting another checkout." /></p>}
        {cartChanged && <p><T text="Your cart has changed. Check this order before starting another checkout." /></p>}
        </section>{error && <p role="alert">{translate(error)}</p>}
        {paymentPollingV2 && pollingNotice && <p role="status" className="mobile-order-note">{translate(pollingNotice)}</p>}
        <div className="mobile-order-actions">
        {!phonePeStatusOnly && <button type="button" disabled={openingPayment || refreshing || cancelling || cartChanged || paymentDeadlineReached} onClick={() => void handlePayNow()}>{openingPayment ? translate("Opening payment…") : translate("Continue payment")}</button>}
        <button type="button" disabled={openingPayment || refreshing || cancelling} onClick={() => void refreshCurrentPayment()}>{refreshing ? translate("Checking payment…") : translate("Check Payment Status")}</button>
        <Link href="/menu"><T text="Back to menu"/></Link>
        <p className="mobile-payment-leave-note"><T text="Leaving this page checks payment and cancels an unpaid order. Your cart stays saved."/></p>
        </div></section>
    </AppShell>;

    if (phone && features?.simplifiedCheckout && features.checkoutExperienceV2 && features.acceptedCheckoutQuote && (isFailed || isExpired)) return <AppShell showSocialPopup={false}>
        <section className="mobile-order-detail"><nav><Link href="/orders">← My orders</Link><Link href="/menu">View menu</Link></nav>
        <header><h1><T text="Payment didn’t complete" /></h1><p>{orderNumber}</p></header>
        <section className="mobile-order-panel"><p><T text="Your cart is saved. We’ll check the payment before opening a fresh checkout." /></p><p>Payment: {payment.paymentStatus}</p><strong>{formatCurrency(payment.amount)}</strong></section>
        {error && <p role="alert">{translate(error)}</p>}
        <div className="mobile-order-actions"><button type="button" disabled={cancelling || refreshing || openingPayment} onClick={() => void cancelCheckout()}>{cancelling ? translate("Checking payment…") : translate("Retry checkout")}</button><Link href={`/orders/${encodeURIComponent(orderNumber)}`}>View order</Link></div>
        </section>
    </AppShell>;

    return (

        <AppShell>
            <CheckoutExperienceFrame enabled={features?.checkoutExperienceV2 === true} stage="payment">
            <MobilePaymentCancelDialog active={confirmCancel && (features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true)} busy={cancelling || refreshing || openingPayment} error={error} onKeep={() => setConfirmCancel(false)} onCancel={cancelCheckout} />

            <section
                className="
                    mx-auto
                    max-w-lg
                    pb-10
                "
            >

                {!deliveryOrder && <ConfirmedPickupContext orderNumber={orderNumber} />}
                {(isFailed || isExpired) && <section className="mobile-payment-recovery" aria-label="Retry your order">
                    <h2><T text="Payment didn’t complete" /></h2>
                    <p><T text="Your cart is saved. We’ll check the payment before opening a fresh checkout." /></p>
                    <button type="button" disabled={cancelling || refreshing || openingPayment} onClick={() => void cancelCheckout()}>{cancelling ? translate("Checking payment…") : translate("Retry checkout")}</button>
                </section>}

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
                        <T text="Secure checkout" /></p>


                    <h1
                        className="
                            mt-1
                            text-3xl
                            font-bold
                            tracking-tight
                            text-[#241715]
                        "
                    >
                        <T text="Payment" /></h1>


                    <p
                        className="
                            mt-2
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        <T text="Order" />{" "}
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
                                <T text="Your cart changed after this payment was prepared." /></p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    leading-6
                                    text-amber-700
                                "
                            >
                                <T text="Do not pay this stale checkout. Return to the cart and continue checkout so the backend can validate the current items." /></p>

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
                                <T text="Existing PhonePe payment found" /></p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    leading-6
                                    text-blue-700
                                "
                            >
                                <T text="We recovered the payment from the backend. The browser no longer has the original checkout link, so we will continue checking the existing PhonePe payment instead of creating a duplicate payment." /></p>

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
                                        <T text="Refund in progress" /></h2>


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
                                        {deliveryOrder ? "delivery reservation" : "pickup reservation"}. A full
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
                                        <T text="This page checks our backend automatically. You can also check again manually." /></p>

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
                                <T text="Refund completed" /></h2>


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
                                failed, and its {deliveryOrder ? "rider window" : "pickup slot"} stays
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
                                <T text="Refund needs attention" /></h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                <T text="The automatic refund did not reach a successful final state. Your old order has not been restored and no" />{" "}{deliveryOrder ? "rider window" : "pickup slot"} has been re-reserved.
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    font-semibold
                                    text-red-800
                                "
                            >
                                <T text="Please keep this order number for support:" />{" "}{orderNumber}
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
                                <T text="Payment failed" /></h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                <T text="The order is no longer holding its" />{" "}{deliveryOrder ? "delivery reservation" : "pickup reservation"}<T text=". Your cart is still available." /></p>


                            <p
                                className="
                                    mt-2
                                    text-xs
                                    leading-5
                                    text-red-600
                                "
                            >
                                <T text="If the payment provider later reports that money was actually captured, the backend automatically starts the refund reconciliation flow." /></p>

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
                                <T text="Payment time expired" /></h2>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    leading-6
                                    text-red-700
                                "
                            >
                                The old {deliveryOrder ? "delivery" : "pickup"} <T text="reservation has been released. Your cart is still safe, so you can choose a new" />{" "}{deliveryOrder ? "rider window" : "pickup slot"}.
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-xs
                                    leading-5
                                    text-red-600
                                "
                            >
                                <T text="If the payment provider later reports a captured payment, the backend will move it into automatic refund reconciliation instead of confirming this expired order." /></p>

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
                                <T text="Amount" /></p>


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
                                <T text="Payment status" /></span>


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
                                <T text="Payment ID" /></span>


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
                                        <T text="Provider transaction" /></span>


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
                                        <T text="Payment deadline" /></span>


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
                                            : translate("Closing the payment window does not cancel your order immediately.")
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
                                            ? (paymentPollingV2 && !gatewayOpened
                                                ? "A payment attempt already exists. If you paid on another device, choose Check Payment Status below."
                                                : "This payment is still being checked. You can safely leave and return to this order.")
                                            : (paymentPollingV2 && !gatewayOpened
                                                ? translate("Ready when you are. Tap Pay to open secure checkout.")
                                                : "You can safely leave this page and return later. We'll show your order once the payment is confirmed.")
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
                                    ||
                                    paymentDeadlineReached
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
                                        : `${gatewayOpened ? translate("Retry payment") : translate("Pay")} ${formatCurrency(
                                            payment.amount
                                        )}`
                                }
                            </button>

                        )
                    }

                    <PaymentLeaveChoice active={isPending} busy={cancelling || refreshing || openingPayment} error={translate(error)} onCancel={cancelCheckout}/>
                    {feeBreakdown&&feeBreakdown.paymentFee>0&&<p className="mt-4 rounded-xl border p-3 text-sm"><T text="Online payment fee" /> ({feeBreakdown.paymentRate}%): {formatCurrency(feeBreakdown.paymentFee)} · <T text="Includes" /> {formatCurrency(feeBreakdown.paymentTax)} <T text="fee tax" /></p>}
                    {feeBreakdown && feeBreakdown.fee>0 && <p className="mt-4 rounded-xl border p-3 text-sm"><T text="Payable amount includes a convenience fee of" />{" "}{formatCurrency(feeBreakdown.fee)} <T text="(including" />{" "}{formatCurrency(feeBreakdown.tax)} <T text="fee tax)." /></p>}
                    {(isPending || isFailed || isExpired) && <section aria-label="Payment recovery" className="mt-4 rounded-2xl border border-[#c4d4c9] bg-[#fffaf2] p-4 text-[#173c39]">
                        <p className="font-bold">{isPending ? translate("Need to stop this checkout?") : "Your cart is ready to try again"}</p>
                        <p className="mt-1 text-sm leading-6"><T text="Check payment and release the unpaid pickup reservation. Keep your items for another attempt." /></p>
                        {confirmCancel ? <div className="payment-cancel-inline mt-3"><p className="text-sm"><T text="Close the gateway first. A payment received after cancellation enters the refund process." /></p>
                            <div className="mt-3 flex flex-wrap gap-3"><button type="button" disabled={cancelling || refreshing || openingPayment} onClick={()=>void cancelCheckout()} className="min-h-11 rounded-xl bg-[#173c39] px-4 font-bold text-white disabled:opacity-50">{cancelling ? translate("Checking payment…") : translate("Cancel order & keep cart")}</button>
                            <button type="button" disabled={cancelling} onClick={()=>setConfirmCancel(false)} className="min-h-11 rounded-xl border px-4 font-bold"><T text="Continue payment" /></button></div></div>
                            : <button type="button" disabled={openingPayment || refreshing} onClick={()=>setConfirmCancel(true)} className="mt-3 min-h-11 rounded-xl border border-[#c4d4c9] bg-white px-4 font-bold">{isPending ? translate("Cancel this order") : translate("Check & retry checkout")}</button>}
                    </section>}
                    {paymentPollingV2 && isPending && paymentDeadlineReached &&
                        <p role="status" className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900">
                            <T text="This payment window has closed. Please check its status before starting a new checkout. If you paid, it may take a little longer to confirm." /></p>}

                    {paymentPollingV2 && pollingNotice && isPending &&
                        <p role="status" className="mt-5 rounded-xl border border-blue-100 bg-blue-50 p-4 text-sm text-blue-800">
                            {pollingNotice}
                        </p>}


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
                                            : translate("Check Payment Status")
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
                                {deliveryOrder ? "Choose New Delivery Window" : "Choose New Pickup Slot"}
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
                                <T text="View Order Details" /></button>

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
                                <T text="Return to Cart" /></button>

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
                                    <T text="Do not retry the old payment." /></p>


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
                                role="alert"
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
                                    {translate(error)}
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
                    <T text="Your order is confirmed only after our backend verifies a successful payment. Closing or going back from the payment window does not directly cancel the order." /></p>

            </section>

        </CheckoutExperienceFrame>
        </AppShell>
    );
}
