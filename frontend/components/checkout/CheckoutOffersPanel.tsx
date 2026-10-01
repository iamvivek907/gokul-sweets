"use client";
import CheckoutAdjustmentDialog from "./CheckoutAdjustmentDialog";
import type {CartItem} from "@/types/cart";
import type {PickupSelection} from "@/types/pickup";
import CheckoutMobileAction from "./CheckoutMobileAction";
import {usefulRebateTarget} from "@/lib/pickupAddOnRebate";
import {getCartSnapshot,parseCart,saveCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getCustomerSnapshot,getPickupSlotSnapshot,parseCustomerDetails,parsePickupSlot,savePickupSlot} from "@/lib/checkoutStorage";
import {previewCheckoutQuote,updatePendingCheckout} from "@/services/orderApi";
import {apiClient} from "@/services/apiClient";
import PickupAddOns from "@/components/checkout/PickupAddOns";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

import Link
    from "next/link";

import {
    useEffect,
    useMemo,
    useState,
    useSyncExternalStore
} from "react";

import {
    useRouter
} from "next/navigation";

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
    clearPickupSlot
} from "@/lib/checkoutStorage";

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

import type {
    CustomerOrderResponse
} from "@/types/order";


interface CheckoutOffersPanelProps {
    reviewRequired?:boolean;
    onCartMutationBusy?: (busy:boolean)=>void;
    onUpdateError?: (message:string)=>void;

    orderNumber:
        string;
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
                2
        }
    ).format(
        amount
    );
}


function getRebateTypeLabel(
    rebate: AvailableRebateResponse
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


export default function CheckoutOffersPanel({
    orderNumber, onCartMutationBusy, onUpdateError, reviewRequired=false
}: CheckoutOffersPanelProps) {

    const features=useStorefrontFeatures();
    const [addonBusy,setAddonBusy]=useState(false);
    const [priceReviewRequired,setPriceReviewRequired]=useState(reviewRequired);
    const [totalChanged,setTotalChanged]=useState(reviewRequired);
    const [spendTargets,setSpendTargets]=useState<AvailableRebateResponse[]>([]);
    useEffect(()=>{const c=new AbortController();apiClient<AvailableRebateResponse[]>(`/api/orders/${encodeURIComponent(orderNumber)}/rebate-spend-targets`,{credentials:"include",signal:c.signal}).then(setSpendTargets).catch(()=>{});return()=>c.abort();},[orderNumber]);
    const router =
        useRouter();


    const {
        items
    } =
        useCart();


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
            && !addonBusy
        );


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
        remainingSeconds > 0
        &&
        remainingSeconds <=
            5 * 60;


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
            false
        );


    const [
        offersLoadedSuccessfully,
        setOffersLoadedSuccessfully
    ] =
        useState(
            false
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
        exclusiveCode,
        setExclusiveCode
    ] =
        useState(
            ""
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
        errorSource,
        setErrorSource
    ] =
        useState<
            "offers"
            | "code"
            | "general"
            | null
        >(
            null
        );


    const [
        successMessage,
        setSuccessMessage
    ] =
        useState<
            string
            | null
        >(
            null
        );


    const [
        orderSummary,
        setOrderSummary
    ] =
        useState<
            CustomerOrderResponse
            | null
        >(
            null
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


    useEffect(
        () => {

            if (
                !pendingOrder
                ||
                pendingOrder.orderNumber !==
                    orderNumber
            ) {

                return;
            }


            const controller =
                new AbortController();


            async function loadOrderSummary() {

                try {

                    const response =
                        await getCustomerOrder(
                            orderNumber,
                            controller.signal
                        );


                    if (controller.signal.aborted) return;
                    setOrderSummary(
                        response
                    );

                } catch (exception) {

                    if (
                        exception instanceof DOMException
                        &&
                        exception.name === "AbortError"
                    ) {

                        return;
                    }


                    console.error(
                        "Unable to refresh final order price:",
                        exception
                    );


                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to refresh your final price."
                    );


                    setErrorSource(
                        "general"
                    );
                }
            }


            void loadOrderSummary();


            return () => {

                controller.abort();
            };

        },
        [
            orderNumber,
            pendingOrder
        ]
    );


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

                    const response =
                        await getCustomerOrder(
                            orderNumber
                        );


                    if (cancelled) {
                        return;
                    }


                    if (
                        response.orderStatus !==
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


                    setReservationReleaseError(
                        "We could not confirm that the old pickup time has been released. Please try again shortly."
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


    async function handleFindOffers() {
        if(addonBusy)return;

        if (
            loading
            ||
            applyingCode
            ||
            removing
            ||
            reservationExpired
            ||
            cartChanged
            ||
            !pendingOrder
            ||
            pendingOrder.orderNumber !==
                orderNumber
        ) {

            return;
        }


        setLoading(
            true
        );


        setOffersLoadedSuccessfully(
            false
        );


        setError(
            null
        );


        setErrorSource(
            null
        );


        setSuccessMessage(
            null
        );


        try {

            const response =
                await getAvailableRebates(
                    orderNumber
                );


            setRebates(
                response
            );


            setOffersLoadedSuccessfully(
                true
            );

        } catch (exception) {

            console.error(
                "Unable to load available offers:",
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


            setErrorSource(
                "offers"
            );

        } finally {

            setLoading(
                false
            );
        }
    }


    function updatePendingOrderTotal(
        totalAmount: number
    ) {

        if (
            !pendingOrder
            ||
            pendingOrder.orderNumber !==
                orderNumber
        ) {

            return;
        }


        savePendingOrder({
            ...pendingOrder,

            totalAmount
        });
    }


    async function applyCode(
        code: string,
        source:
            "public"
            | "exclusive"
    ) {

        if(addonBusy)return;
        const normalizedCode =
            code
                .trim()
                .toUpperCase();


        if (
            !normalizedCode
            ||
            applyingCode
            ||
            removing
            ||
            reservationExpired
            ||
            cartChanged
        ) {

            return;
        }


        setApplyingCode(
            normalizedCode
        );


        setError(
            null
        );


        setErrorSource(
            null
        );


        setSuccessMessage(
            null
        );


        try {

            const response =
                await applyRebate(
                    orderNumber,
                    normalizedCode
                );


            setAppliedRebate(
                response
            );


            updatePendingOrderTotal(
                response.totalAmount
            );


            setOrderSummary(
                current =>
                    current
                        ? {
                            ...current,

                            totalAmount:
                                response.totalAmount
                        }
                        : current
            );


            setExclusiveCode(
                source === "exclusive"
                    ? response.rebateCode
                        ?? normalizedCode
                    : ""
            );


            setSuccessMessage(
                `${response.rebateCode ?? normalizedCode} applied. You saved ${formatCurrency(
                    response.rebateAmount
                )}.`
            );

        } catch (exception) {

            console.error(
                "Unable to apply offer code:",
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "This offer code could not be applied."
            );


            setErrorSource(
                "code"
            );

        } finally {

            setApplyingCode(
                null
            );
        }
    }


    async function handleRemove() {
        if(addonBusy)return;

        if (
            removing
            ||
            applyingCode
            ||
            reservationExpired
            ||
            cartChanged
        ) {

            return;
        }


        setRemoving(
            true
        );


        setError(
            null
        );


        setErrorSource(
            null
        );


        setSuccessMessage(
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


            setExclusiveCode(
                ""
            );


            updatePendingOrderTotal(
                response.totalAmount
            );


            setOrderSummary(
                current =>
                    current
                        ? {
                            ...current,

                            totalAmount:
                                response.totalAmount
                        }
                        : current
            );


            setSuccessMessage(
                "Offer removed. Your final price has been updated."
            );

        } catch (exception) {

            console.error(
                "Unable to remove applied offer:",
                exception
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to remove this offer."
            );


            setErrorSource(
                "general"
            );

        } finally {

            setRemoving(
                false
            );
        }
    }


    function handleRetry() {

        void handleFindOffers();
    }


    function handleContinueToPayment() {
        if(addonBusy || priceReviewRequired || applyingCode || removing)return;

        if (
            !pendingOrder
            ||
            pendingOrder.orderNumber !==
                orderNumber
        ) {

            setError(
                "No pending checkout was found for this order."
            );


            setErrorSource(
                "general"
            );

            return;
        }


        if (
            reservationExpired
        ) {

            setError(
                "Your pickup reservation has expired. Please choose a pickup slot again."
            );


            setErrorSource(
                "general"
            );

            return;
        }


        if (
            cartChanged
        ) {

            setError(
                "Your cart has changed. Review the updated cart before payment."
            );


            setErrorSource(
                "general"
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


    function handleChooseNewPickupTime() {

        if (
            !reservationReleaseReady
        ) {

            return;
        }


        clearPendingOrder();

        clearPickupSlot();


        router.push(
            "/checkout/pickup"
        );
    }


    const [adjusting,setAdjusting]=useState(false);
    async function refreshAfterAddition(changedItems?:CartItem[],changedPickup?:PickupSelection) {
        setTotalChanged(true);setPriceReviewRequired(true);setAppliedRebate(null);setSuccessMessage(null);
        const cartSnapshot=getCartSnapshot(), pickupSnapshot=getPickupSlotSnapshot(), branchSnapshot=getStoredBranchSnapshot();
        const storedCart=parseCart(cartSnapshot),cart=changedItems?{...storedCart,items:changedItems}:storedCart,pickup=changedPickup??parsePickupSlot(pickupSnapshot),customer=parseCustomerDetails(getCustomerSnapshot());
        const saved=parsePendingOrder(getPendingOrderSnapshot());
        const unchanged=()=>cartSnapshot===getCartSnapshot()&&pickupSnapshot===getPickupSlotSnapshot()&&branchSnapshot===getStoredBranchSnapshot()&&parsePendingOrder(getPendingOrderSnapshot())?.orderNumber===orderNumber;
        try {
            if(!saved || saved.orderNumber!==orderNumber || saved.branchId!==cart.branchId || !pickup || pickup.pickupType!=="NORMAL" || !customer || !cart.items.length)throw new Error("Review your cart and pickup details before refreshing the price.");
            const current=await getCustomerOrder(orderNumber);
            if(current.orderStatus!=="PENDING_PAYMENT" || current.paymentStatus!==null)throw new Error("This order has a payment attempt or has changed. Review its payment status before adding items.");
            const request={branchId:saved.branchId,pickupSlotId:pickup.slot.id,pickupType:pickup.pickupType,customerName:customer.name,customerPhone:customer.phone,items:cart.items.map(i=>({productId:i.product.id,quantity:i.product.saleMode==="UNIT"?i.quantity:null,weightGrams:i.weightGrams}))};
            const quote=features?.acceptedCheckoutQuote?await previewCheckoutQuote(request,orderNumber):null;
            if(!unchanged())throw new Error("Your cart or pickup changed during the price check. Review it again.");
            const updated=await updatePendingCheckout(orderNumber,{pickupSlotId:request.pickupSlotId,pickupType:request.pickupType,items:request.items,quoteToken:quote?.token});
            if(!unchanged() || updated.orderStatus!=="PENDING_PAYMENT")throw new Error("Your checkout changed. Review the cart before continuing.");
            if(changedItems)saveCart(cart);if(changedPickup)savePickupSlot(changedPickup);
            setOrderSummary({...current,pickupDate:pickup.date,pickupStartTime:pickup.slot.startTime,pickupEndTime:pickup.slot.endTime,pickupType:pickup.pickupType,items:updated.items,subtotal:updated.subtotal,taxAmount:updated.taxAmount,priorityCharge:updated.priorityCharge,convenienceFee:updated.convenienceFee,convenienceFeeTax:updated.convenienceFeeTax,totalAmount:updated.totalAmount,reservationExpiresAt:updated.reservationExpiresAt});
            savePendingOrder({...saved,pickupSlotId:pickup.slot.id,totalAmount:updated.totalAmount,reservationExpiresAt:updated.reservationExpiresAt,cartFingerprint:createCartFingerprint(cart.items)});
            setError(null);setErrorSource(null);
            const [offers,targets]=await Promise.allSettled([getAvailableRebates(orderNumber),apiClient<AvailableRebateResponse[]>(`/api/orders/${encodeURIComponent(orderNumber)}/rebate-spend-targets`,{credentials:"include"})]);
            setRebates(offers.status==="fulfilled"?offers.value:[]);setOffersLoadedSuccessfully(offers.status==="fulfilled");
            setSpendTargets(targets.status==="fulfilled"?targets.value:[]);
            setSuccessMessage("Addition checked. Review the updated total and choose any available offer before payment.");
        } catch(cause) {
            const message=cause instanceof Error?cause.message:"Could not refresh the price. Review your cart before payment.";
            setError(message);setErrorSource("general");onUpdateError?.(message);throw cause;
        }
    }

    const displayTotal =
        appliedRebate
            ?.totalAmount
        ??
        orderSummary
            ?.totalAmount
        ??
        pendingOrder
            ?.totalAmount
        ??
        0;


    return (
        <div
            className="
                mt-5
                space-y-4
            "
        >

            <div
                className={`
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
                            className="
                                text-xs
                                font-bold
                                uppercase
                                tracking-[0.12em]
                                text-[#7a1625]
                            "
                        >
                            Pickup reserved
                        </p>


                        <p
                            className="
                                mt-1
                                text-xs
                                text-[#756763]
                            "
                        >
                            Complete checkout before the timer ends.
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
                            {reservationMinutes}:{padSeconds(reservationSeconds)}
                        </p>


                        <p
                            className="
                                text-[9px]
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

            </div>


            {
                reservationExpired
                    ? (
                        <div
                            className="
                                rounded-3xl
                                border
                                border-red-200
                                bg-white
                                p-5
                            "
                        >
                            <p className="font-bold text-red-700">
                                Pickup reservation expired
                            </p>

                            <p className="mt-2 text-sm leading-6 text-[#756763]">
                                Choose a new pickup time before continuing.
                                Your cart is still available.
                            </p>

                            {
                                reservationReleaseError
                                && (
                                    <p
                                        role="alert"
                                        className="mt-3 text-sm font-semibold text-red-700"
                                    >
                                        {reservationReleaseError}
                                    </p>
                                )
                            }

                            <button
                                type="button"
                                disabled={
                                    !reservationReleaseReady
                                }
                                onClick={
                                    handleChooseNewPickupTime
                                }
                                className="
                                    mt-4
                                    inline-flex
                                    min-h-11
                                    items-center
                                    rounded-xl
                                    bg-[#7a1625]
                                    px-4
                                    text-sm
                                    font-bold
                                    text-white!

                                    disabled:cursor-wait
                                    disabled:bg-[#c9b9b4]
                                "
                            >
                                {
                                    reservationReleaseReady
                                        ? "Choose New Pickup Time"
                                        : "Releasing old pickup time..."
                                }
                            </button>
                        </div>
                    )
                    : cartChanged
                        ? (
                            <div
                                className="
                                    rounded-3xl
                                    border
                                    border-amber-200
                                    bg-white
                                    p-5
                                "
                            >
                                <p className="font-bold text-[#7a1625]">
                                    Your cart has changed
                                </p>

                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    Review the cart again so this same pending
                                    order can be updated before payment.
                                </p>

                                <button type="button" onClick={()=>setAdjusting(true)}
                                    className="
                                        mt-4
                                        inline-flex
                                        min-h-11
                                        items-center
                                        text-sm
                                        font-bold
                                        text-[#7a1625]
                                    "
                                >
                                    Review changes here
                                </button>
                            </div>
                        )
                        : (
                            <>

                                <div
                                    className="
                                        rounded-3xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                        shadow-[0_6px_24px_rgba(60,30,20,0.06)]
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
                                            <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[#c88a20]">
                                                Offers & savings
                                            </p>

                                            <h2 className="mt-1 text-xl font-bold text-[#241715]">
                                                Save on this order
                                            </h2>
                                        </div>

                                        {
                                            offersLoadedSuccessfully
                                            &&
                                            rebates.length > 0
                                            && (
                                                <span className="shrink-0 rounded-full bg-[#fff0dc] px-3 py-1 text-xs font-bold text-[#7a1625]">
                                                    {rebates.length}{" "}
                                                    {rebates.length === 1 ? "offer" : "offers"}
                                                </span>
                                            )
                                        }
                                    </div>


                                    {
                                        loading
                                            ? (
                                                <div className="mt-5 space-y-3">
                                                    {[1, 2].map(
                                                        item => (
                                                            <div
                                                                key={item}
                                                                className="h-24 animate-pulse rounded-2xl bg-[#f7f0ea]"
                                                            />
                                                        )
                                                    )}
                                                </div>
                                            )
                                            : !offersLoadedSuccessfully
                                                ? (
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

                                                        <p
                                                            className="
                                                                text-sm
                                                                font-bold
                                                                text-[#241715]
                                                            "
                                                        >
                                                            See offers available for this order
                                                        </p>


                                                        <p
                                                            className="
                                                                mt-1
                                                                text-xs
                                                                leading-5
                                                                text-[#756763]
                                                            "
                                                        >
                                                            We&apos;ll check the server using your
                                                            final cart value, branch, pickup type
                                                            and eligibility.
                                                        </p>


                                                        <button
                                                            type="button"
                                                            onClick={
                                                                () =>
                                                                    void handleFindOffers()
                                                            }
                                                            className="
                                                                mt-4
                                                                flex
                                                                min-h-11
                                                                w-full
                                                                items-center
                                                                justify-center
                                                                rounded-xl
                                                                border
                                                                border-[#7a1625]
                                                                bg-white
                                                                px-4
                                                                text-sm
                                                                font-bold
                                                                text-[#7a1625]
                                                                transition

                                                                hover:bg-[#fff0dc]
                                                                active:scale-[0.98]
                                                            "
                                                        >
                                                            Find available offers
                                                        </button>

                                                    </div>
                                                )
                                                : rebates.length === 0
                                                    ? (
                                                        <div className="mt-5 rounded-2xl bg-[#fffaf3] p-4 text-center">
                                                            <p className="text-sm font-bold text-[#241715]">
                                                                No public offers available
                                                            </p>
                                                            <p className="mt-1 text-xs leading-5 text-[#756763]">
                                                                Have a creator, partner or promotional
                                                                code? You can still enter it below.
                                                            </p>
                                                        </div>
                                                    )
                                                    : (
                                                        <div className="mt-5 space-y-3">
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
                                                                                key={rebate.rebateId}
                                                                                className={`
                                                                                    rounded-2xl
                                                                                    border
                                                                                    p-4

                                                                                    ${
                                                                                        isApplied
                                                                                            ? "border-green-300 bg-green-50/40 ring-1 ring-green-100"
                                                                                            : index === 0
                                                                                                ? "border-[#d9a64d] bg-[#fffdf9]"
                                                                                                : "border-[#eadfd6] bg-white"
                                                                                    }
                                                                                `}
                                                                            >
                                                                                <div className="flex items-start justify-between gap-3">
                                                                                    <div className="min-w-0 flex-1">
                                                                                        <div className="flex flex-wrap items-center gap-2">
                                                                                            <span className="rounded-full bg-[#fff0dc] px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-[#7a1625]">
                                                                                                {rebate.code}
                                                                                            </span>

                                                                                            {
                                                                                                index === 0
                                                                                                &&
                                                                                                !isApplied
                                                                                                && (
                                                                                                    <span className="rounded-full bg-green-100 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-green-700">
                                                                                                        Best saving
                                                                                                    </span>
                                                                                                )
                                                                                            }

                                                                                            {
                                                                                                isApplied
                                                                                                && (
                                                                                                    <span className="rounded-full bg-green-100 px-2.5 py-1 text-[10px] font-bold uppercase tracking-wide text-green-700">
                                                                                                        Applied ✓
                                                                                                    </span>
                                                                                                )
                                                                                            }
                                                                                        </div>

                                                                                        <p className="mt-3 font-bold text-[#241715]">
                                                                                            {rebate.name}
                                                                                        </p>

                                                                                        {
                                                                                            rebate.description
                                                                                            && (
                                                                                                <p className="mt-1 text-xs leading-5 text-[#756763]">
                                                                                                    {rebate.description}
                                                                                                </p>
                                                                                            )
                                                                                        }

                                                                                        <p className="mt-2 text-xs font-semibold text-[#756763]">
                                                                                            {getRebateTypeLabel(rebate)}
                                                                                        </p>
                                                                                    </div>

                                                                                    <div className="shrink-0 text-right">
                                                                                        <p className="text-[10px] font-semibold uppercase tracking-wide text-[#756763]">
                                                                                            Save
                                                                                        </p>
                                                                                        <p className="mt-1 text-lg font-extrabold text-green-700">
                                                                                            {formatCurrency(rebate.rebateAmount)}
                                                                                        </p>
                                                                                    </div>
                                                                                </div>

                                                                                {
                                                                                    usefulRebateTarget([rebate]) !== null
                                                                                    && (
                                                                                        <p className="mt-3 rounded-xl bg-[#fffaf3] px-3 py-2 text-xs leading-5 text-[#756763]">
                                                                                            Add {formatCurrency(rebate.amountNeededForNextSlab!)} more
                                                                                            to unlock the next saving level.
                                                                                        </p>
                                                                                    )
                                                                                }

                                                                                <button
                                                                                    type="button"
                                                                                    disabled={
                                                                                        Boolean(
                                                                                            addonBusy || applyingCode
                                                                                            ||
                                                                                            removing
                                                                                        )
                                                                                    }
                                                                                    onClick={
                                                                                        () =>
                                                                                            isApplied
                                                                                                ? void handleRemove()
                                                                                                : void applyCode(
                                                                                                    rebate.code,
                                                                                                    "public"
                                                                                                )
                                                                                    }
                                                                                    className={`
                                                                                        mt-4
                                                                                        min-h-11
                                                                                        w-full
                                                                                        rounded-xl
                                                                                        px-4
                                                                                        text-sm
                                                                                        font-bold
                                                                                        transition
                                                                                        disabled:cursor-not-allowed
                                                                                        disabled:opacity-60

                                                                                        ${
                                                                                            isApplied
                                                                                                ? "border border-[#eadfd6] bg-white text-[#7a1625]"
                                                                                                : "bg-[#7a1625] text-white! hover:bg-[#5d0f1b]"
                                                                                        }
                                                                                    `}
                                                                                >
                                                                                    {
                                                                                        isApplying
                                                                                            ? "Applying..."
                                                                                            : isApplied
                                                                                                ? removing
                                                                                                    ? "Removing..."
                                                                                                    : "Remove offer"
                                                                                                : `Apply & save ${formatCurrency(rebate.rebateAmount)}`
                                                                                    }
                                                                                </button>
                                                                            </article>
                                                                        );
                                                                    }
                                                                )
                                                            }
                                                        </div>
                                                    )
                                    }


                                    {features?.pickupAddOns && pendingOrder && orderSummary?.pickupType==="NORMAL" && orderSummary.pickupDate && orderSummary.orderStatus==="PENDING_PAYMENT" && orderSummary.paymentStatus==null && <PickupAddOns branchId={pendingOrder.branchId} date={orderSummary.pickupDate} orderNumber={orderNumber} offers={spendTargets} disabled={loading || reservationExpired || !!applyingCode || removing} onBusy={busy=>{setAddonBusy(busy);onCartMutationBusy?.(busy);}} onAdded={()=>refreshAfterAddition()} onAdjust={()=>setAdjusting(true)}/>}
                                    <div className="mt-5 border-t border-[#eadfd6] pt-5">
                                        <p className="text-sm font-bold text-[#241715]">
                                            Have a creator or exclusive code?
                                        </p>

                                        <p className="mt-1 text-xs leading-5 text-[#756763]">
                                            Enter a code shared by an influencer,
                                            partner, event or private campaign.
                                        </p>

                                        <form
                                            onSubmit={
                                                event => {
                                                    event.preventDefault();
                                                    void applyCode(
                                                        exclusiveCode,
                                                        "exclusive"
                                                    );
                                                }
                                            }
                                            className="mt-3 flex min-w-0 gap-2"
                                        >
                                            <input
                                                type="text"
                                                value={exclusiveCode}
                                                onChange={
                                                    event => {

                                                        setExclusiveCode(
                                                            event.target.value
                                                                .toUpperCase()
                                                        );


                                                        if (
                                                            errorSource ===
                                                                "code"
                                                        ) {

                                                            setError(
                                                                null
                                                            );

                                                            setErrorSource(
                                                                null
                                                            );
                                                        }
                                                    }
                                                }
                                                disabled={
                                                    Boolean(
                                                        applyingCode
                                                        ||
                                                        removing
                                                    )
                                                }
                                                autoCapitalize="characters"
                                                autoComplete="off"
                                                placeholder="Enter offer code"
                                                aria-label="Exclusive offer code"
                                                className="
                                                    min-h-12
                                                    min-w-0
                                                    flex-1
                                                    rounded-xl
                                                    border
                                                    border-[#eadfd6]
                                                    bg-[#fffaf3]
                                                    px-4
                                                    text-sm
                                                    font-semibold
                                                    uppercase
                                                    tracking-wide
                                                    text-[#241715]
                                                    outline-none
                                                    focus:border-[#7a1625]
                                                    focus:ring-2
                                                    focus:ring-[#7a1625]/10
                                                "
                                            />

                                            <button
                                                type="submit"
                                                disabled={
                                                    !exclusiveCode.trim()
                                                    ||
                                                    Boolean(
                                                        applyingCode
                                                        ||
                                                        removing
                                                    )
                                                }
                                                className="
                                                    min-h-12
                                                    shrink-0
                                                    rounded-xl
                                                    bg-[#241715]
                                                    px-4
                                                    text-sm
                                                    font-bold
                                                    text-white!
                                                    transition
                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-50
                                                "
                                            >
                                                {
                                                    applyingCode ===
                                                        exclusiveCode
                                                            .trim()
                                                            .toUpperCase()
                                                        ? "Checking..."
                                                        : "Apply"
                                                }
                                            </button>
                                        </form>
                                    </div>


                                    {
                                        appliedRebate
                                        && (
                                            <div
                                                role="status"
                                                className="mt-4 rounded-2xl border border-green-200 bg-green-50 p-4"
                                            >
                                                <div className="flex items-start justify-between gap-3">
                                                    <div className="min-w-0">
                                                        <p className="text-xs font-bold uppercase tracking-wide text-green-700">
                                                            Offer applied ✓
                                                        </p>

                                                        <p className="mt-1 font-bold text-[#241715]">
                                                            {appliedRebate.rebateCode}
                                                        </p>

                                                        {
                                                            appliedRebate.rebateName
                                                            && (
                                                                <p className="mt-1 text-xs text-[#756763]">
                                                                    {appliedRebate.rebateName}
                                                                </p>
                                                            )
                                                        }
                                                    </div>

                                                    <p className="shrink-0 font-extrabold text-green-700">
                                                        -{formatCurrency(appliedRebate.rebateAmount)}
                                                    </p>
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
                                                    className="mt-3 min-h-10 text-sm font-bold text-[#7a1625] disabled:opacity-50"
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


                                    {
                                        successMessage
                                        && (
                                            <div
                                                role="status"
                                                className="mt-4 rounded-xl border border-green-100 bg-green-50 p-3"
                                            >
                                                <p className="text-sm font-semibold text-green-700">
                                                    {successMessage}
                                                </p>
                                            </div>
                                        )
                                    }


                                    {
                                        error
                                        && (
                                            <div
                                                role="alert"
                                                className="mt-4 rounded-xl border border-red-100 bg-red-50 p-3"
                                            >
                                                <p className="text-sm font-semibold text-red-700">
                                                    {error}
                                                </p>

                                                {
                                                    errorSource ===
                                                        "offers"
                                                    && (
                                                        <button
                                                            type="button"
                                                            onClick={handleRetry}
                                                            className="mt-2 text-xs font-bold text-[#7a1625]"
                                                        >
                                                            Try checking offers again
                                                        </button>
                                                    )
                                                }
                                            </div>
                                        )
                                    }

                                </div>


                                <div
                                    className="
                                        rounded-3xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                        shadow-[0_6px_24px_rgba(60,30,20,0.06)]
                                    "
                                >
                                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-[#c88a20]">
                                        Final price
                                    </p>

                                    {
                                        orderSummary
                                        && (
                                            <div className="mt-4 space-y-2 text-sm">
                                                <div className="flex items-center justify-between gap-4">
                                                    <span className="text-[#756763]">
                                                        Items subtotal
                                                    </span>
                                                    <span className="font-semibold text-[#241715]">
                                                        {formatCurrency(orderSummary.subtotal)}
                                                    </span>
                                                </div>

                                                <div className="flex items-center justify-between gap-4">
                                                    <span className="text-[#756763]">
                                                        GST
                                                    </span>
                                                    <span className="font-semibold text-[#241715]">
                                                        {formatCurrency(orderSummary.taxAmount)}
                                                    </span>
                                                </div>

                                                {(orderSummary.convenienceFee ?? 0)>0 && <div className="flex justify-between gap-4"><span>Convenience fee (tax included)</span><span>{formatCurrency(orderSummary.convenienceFee ?? 0)}</span></div>}
                                                {
                                                    orderSummary.priorityCharge >
                                                        0
                                                    && (
                                                        <div className="flex items-center justify-between gap-4">
                                                            <span className="text-[#756763]">
                                                                Priority pickup
                                                            </span>
                                                            <span className="font-semibold text-[#241715]">
                                                                {formatCurrency(orderSummary.priorityCharge)}
                                                            </span>
                                                        </div>
                                                    )
                                                }

                                                {
                                                    appliedRebate
                                                    && (
                                                        <div className="flex items-center justify-between gap-4">
                                                            <span className="text-green-700">
                                                                Offer saving
                                                            </span>
                                                            <span className="font-bold text-green-700">
                                                                -{formatCurrency(appliedRebate.rebateAmount)}
                                                            </span>
                                                        </div>
                                                    )
                                                }
                                            </div>
                                        )
                                    }

                                    <div className="mt-4 flex items-end justify-between gap-4 border-t border-[#eadfd6] pt-4">
                                        <div>
                                            <p className="text-sm font-semibold text-[#241715]">
                                                Payable
                                            </p>
                                            <p className="mt-1 text-xs text-[#756763]">
                                                Confirmed final amount
                                            </p>
                                        </div>

                                        <p className="text-2xl font-extrabold text-[#7a1625]">
                                            {formatCurrency(displayTotal)}
                                        </p>
                                    </div>

                                    {totalChanged && <label className="mt-4 flex min-h-11 items-start gap-3 rounded-xl border border-[#d4e1d9] bg-[#fffaf2] p-3 text-sm text-[#173a37]"><input type="checkbox" className="mt-1 h-5 w-5" checked={!priceReviewRequired} onChange={e=>setPriceReviewRequired(!e.target.checked)} />I have reviewed the updated total and offers.</label>}
                                    <button
                                        type="button"
                                        disabled={
                                            priceReviewRequired || loading || addonBusy
                                            ||
                                            Boolean(
                                                applyingCode
                                                ||
                                                removing
                                            )
                                        }
                                        onClick={handleContinueToPayment}
                                        className="
                                            mt-5
                                            flex
                                            min-h-14
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
                                        "
                                    >
                                        Continue to Payment
                                    </button>

                                    <button type="button" disabled={addonBusy} onClick={()=>setAdjusting(true)}

                                        className="
                                            mt-3
                                            flex
                                            min-h-11
                                            items-center
                                            justify-center
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]
                                        "
                                    >
                                        Adjust quantities or pickup
                                    </button>
                                </div>

                            </>
                        )
            }

            {adjusting && pendingOrder && parsePickupSlot(getPickupSlotSnapshot()) && <CheckoutAdjustmentDialog items={items} pickup={parsePickupSlot(getPickupSlotSnapshot())!} branchId={pendingOrder.branchId} days={features?.futureOrderingDays??30} onClose={()=>setAdjusting(false)} onApply={async(changedItems,changedPickup)=>{setAddonBusy(true);onCartMutationBusy?.(true);try{await refreshAfterAddition(changedItems,changedPickup);}finally{setAddonBusy(false);onCartMutationBusy?.(false);}}}/>}
            {!reservationExpired && !cartChanged && <CheckoutMobileAction label={priceReviewRequired ? "Review updated total" : "Continue to payment"} amount={displayTotal} disabled={priceReviewRequired || addonBusy || loading || !!applyingCode || removing} onContinue={handleContinueToPayment} />}
        </div>
    );
}
