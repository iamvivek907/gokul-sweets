"use client";
import {T,useTranslation} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";

import {usePickupClock} from "@/hooks/usePickupClock";
import {pickupIsFresh} from "@/lib/pickupFreshness";
import CheckoutAdjustmentDialog from "@/components/checkout/CheckoutAdjustmentDialog";
import {getCartSnapshot,parseCart,saveCart} from "@/lib/cartStorage";
import {savePickupSlot} from "@/lib/checkoutStorage";
import CheckoutMobileAction from "@/components/checkout/CheckoutMobileAction";
import PickupAddOns from "@/components/checkout/PickupAddOns";
import {formatWeight} from "@/lib/orderQuantity";

import {apiClient} from "@/services/apiClient";
import Link from "next/link";

import {
    useEffect,
    useMemo,
    useRef,
    useState,
    useSyncExternalStore
} from "react";

import {
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import ReviewPickupRecovery from "@/components/checkout/ReviewPickupRecovery";
import CheckoutExperienceFrame from "@/components/checkout/CheckoutExperienceFrame";
import {formatBusinessTime, parseBusinessTimestamp} from "@/lib/businessTime";
import {pendingCheckoutAction} from "@/lib/checkoutQuoteContext";


import CheckoutOffersPanel
    from "@/components/checkout/CheckoutOffersPanel";

import {
    useCart
} from "@/hooks/useCart";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import {
    getPickupSlotSnapshot,
    getServerPickupSlotSnapshot,
    subscribeToPickupSlot,
    parsePickupSlot,

    getCustomerSnapshot,
    getServerCustomerSnapshot,
    subscribeToCustomer,
    parseCustomerDetails
} from "@/lib/checkoutStorage";

import {
    createOrder,
    previewCheckoutQuote,
    getCustomerOrder,
    updatePendingCheckout
} from "@/services/orderApi";

import {
    clearPendingOrder,
    getPendingOrderSnapshot,
    getServerPendingOrderSnapshot,
    parsePendingOrder,
    savePendingOrder,
    subscribeToPendingOrder
} from "@/lib/pendingOrderStorage";

import {
    createCartFingerprint
} from "@/lib/cartFingerprint";

import type {
    CreateOrderRequest,
    CheckoutQuote,
    UpdatePendingOrderRequest
} from "@/types/order";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useOnlineStatus} from "@/hooks/useOnlineStatus";

import {
    addOrderToHistory
} from "@/lib/orderHistoryStorage";

import {
    checkoutErrorMessage
} from "@/lib/checkoutErrorMessage";

import {
    checkInventory
} from "@/services/inventoryApi";

import type {
    InventoryCheckResponse
} from "@/types/inventory";


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
    ).format(
        amount
    );
}


function cartItemSubtotal(
    item: ReturnType<typeof useCart>["items"][number]
): number {
    return item.product.saleMode === "WEIGHT"
        ? item.product.price * (item.weightGrams ?? 0) / 1000
        : item.product.price * item.quantity;
}


function cartItemSelection(
    item: ReturnType<typeof useCart>["items"][number]
): string {

    if (item.product.saleMode === "WEIGHT") {

        const weight =
            item.weightGrams
            ?? item.product.minimumWeightGrams
            ?? 250;

        return (
            `${formatWeight(weight)} × `
            + `${formatCurrency(item.product.price)}/kg`
        );
    }

    return (
        `${item.quantity} × `
        + formatCurrency(item.product.price)
    );
}


function formatTime(
    value: string
): string {
    return formatBusinessTime(value);
}


function formatPickupDate(
    value: string
): string {

    const [
        year,
        month,
        day
    ] =
        value
            .split("-")
            .map(Number);


    const date =
        new Date(
            year,
            month - 1,
            day
        );


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            weekday: "short",
            day: "numeric",
            month: "short",
            year: "numeric"
        }
    ).format(
        date
    );
}


function createIdempotencyKey():
    string {

    if (
        typeof crypto !== "undefined"
        &&
        typeof crypto.randomUUID === "function"
    ) {

        return crypto.randomUUID();
    }


    return (
        `order-${Date.now()}-`
        +
        Math.random()
            .toString(36)
            .slice(2)
    );
}

function formatSuggestedDate(
    value: string
): string {

    return new Intl.DateTimeFormat(
        "en-IN",
        {
            weekday: "long",
            day: "numeric",
            month: "long",
            year: "numeric",
            timeZone: "Asia/Kolkata"
        }
    ).format(
        new Date(
            `${value}T00:00:00+05:30`
        )
    );
}


function formatInventoryQuantity(
    quantity: number,
    inventoryUnit: string | null
): string {

    if (
        inventoryUnit === "GRAM"
    ) {

        return formatWeight(
            quantity
        );
    }


    return `${new Intl.NumberFormat(
        "en-IN"
    ).format(quantity)} ${
        quantity === 1
            ? "item"
            : "items"
    }`;
}


function ReviewInventoryIssue({
    issue,
    branchPhone, onAdjust
}: {
    issue: InventoryCheckResponse;
    branchPhone: string | null; onAdjust:()=>void;
}) {
    useTranslation();

    const unavailableItems =
        issue.items.filter(
            item =>
                !item.orderable
        );


    return (
        <div
            role="alert"
            className="
                mt-5
                rounded-3xl
                border
                border-amber-200
                bg-[#fff8e8]
                p-5
                sm:p-6
            "
        >
            <p
                className="
                    text-xs
                    font-bold
                    uppercase
                    tracking-[0.14em]
                    text-[#c88a20]
                "
            >
                <T text="Quantity confirmation required" /></p>

            <h2
                className="
                    mt-2
                    text-xl
                    font-extrabold
                    text-[#7a1625]
                "
            >
                <T text="We cannot confirm the complete order for this pickup date" /></h2>

            <p
                className="
                    mt-2
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                <T text="Some products need more preparation or stock than is currently available online." /></p>

            <div
                className="
                    mt-5
                    space-y-3
                "
            >
                {
                    unavailableItems.map(
                        item => (
                            <div
                                key={
                                    item.productId
                                }
                                className="
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-4
                                "
                            >
                                <p
                                    className="
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    {
                                        item.productName
                                    }
                                </p>

                                <div
                                    className="
                                        mt-2
                                        grid
                                        grid-cols-2
                                        gap-3
                                        text-sm
                                    "
                                >
                                    <div>
                                        <p className="text-xs text-[#756763]">
                                            <T text="You requested" /></p>

                                        <p className="mt-1 font-bold text-[#241715]">
                                            {
                                                formatInventoryQuantity(
                                                    item.requestedQuantity,
                                                    item.inventoryUnit
                                                )
                                            }
                                        </p>
                                    </div>

                                    <div>
                                        <p className="text-xs text-[#756763]">
                                            <T text="Available online" /></p>

                                        <p className="mt-1 font-bold text-[#7a1625]">
                                            {
                                                formatInventoryQuantity(
                                                    item.availableQuantity,
                                                    item.inventoryUnit
                                                )
                                            }
                                        </p>
                                    </div>
                                </div>

                                <p
                                    className="
                                        mt-3
                                        text-sm
                                        leading-6
                                        text-[#756763]
                                    "
                                >
                                    <T text="Please reduce the quantity, choose another date, or contact the branch for a large-order confirmation." /></p>
                            </div>
                        )
                    )
                }
            </div>

            {
                issue.suggestedDate
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
                                font-bold
                                text-green-800
                            "
                        >
                            <T text="Earliest available date" /></p>

                        <p
                            className="
                                mt-1
                                text-base
                                font-extrabold
                                text-green-900
                            "
                        >
                            {
                                formatSuggestedDate(
                                    issue.suggestedDate
                                )
                            }
                        </p>

                        <Link
                            href={
                                `/checkout/pickup?suggestedDate=${
                                    encodeURIComponent(
                                        issue.suggestedDate
                                    )
                                }`
                            }
                            className="
                                mt-4
                                flex
                                min-h-11
                                w-full
                                items-center
                                justify-center
                                rounded-xl
                                bg-green-700
                                px-4
                                text-sm
                                font-bold
                                text-white!
                            "
                        >
                            <T text="Choose This Pickup Date" /><LinkFeedback /></Link>
                    </div>
                )
            }

            <div
                className="
                    mt-5
                    rounded-2xl
                    border
                    border-[#eadfd6]
                    bg-white
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
                    <T text="Need this quantity specially prepared?" /></p>

                <p
                    className="
                        mt-1
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    <T text="Please call the selected branch. Our team can check production capacity and confirm whether this large order can be prepared." /></p>

                {
                    branchPhone
                    && (
                        <a
                            href={
                                `tel:${branchPhone}`
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
                                px-4
                                text-sm
                                font-bold
                                text-[#7a1625]!
                            "
                        >
                            <T text="Call" />{" "}{branchPhone}
                        </a>
                    )
                }
            </div>

            <button type="button" onClick={onAdjust}
                className="
                    mt-3
                    flex
                    min-h-11
                    w-full
                    items-center
                    justify-center
                    text-sm
                    font-bold
                    text-[#7a1625]!
                "
            >
                <T text="Adjust Cart Quantity" /></button>
        </div>
    );
}

export default function ReviewPage() {
    const translate = useTranslation();

    const storefrontFeatures = useStorefrontFeatures();
    const accessible = storefrontFeatures?.accessibleOrderingV2 === true;
    const online = useOnlineStatus();
    const [addonBusy,setAddonBusy]=useState(false);
    const quoteEnabled = storefrontFeatures?.acceptedCheckoutQuote === true;
    const inPlaceBranchSwitch = storefrontFeatures?.inPlaceBranchSwitch === true;
    const [acceptedQuote, setAcceptedQuote] = useState<{key: string; quote: CheckoutQuote} | null>(null);
    const [quoteNotice, setQuoteNotice] = useState<{oldTotal: string; newTotal: string} | null>(null);
    const [quoteClock, setQuoteClock] = useState(0);
    useEffect(() => {
        if (!acceptedQuote) return;
        const immediate = window.setTimeout(() => setQuoteClock(Date.now()), 0);
        const timer = window.setInterval(() => setQuoteClock(Date.now()), 15_000);
        return () => {window.clearTimeout(immediate); window.clearInterval(timer);};
    }, [acceptedQuote]);
    const quoteExpired = acceptedQuote !== null && quoteClock > 0 &&
        Date.parse(acceptedQuote.quote.expiresAt) <= quoteClock;
    const [pickupRecovery, setPickupRecovery] = useState(false);
    const [editingPickup, setEditingPickup] = useState(false);
    const [adjustingCheckout,setAdjustingCheckout]=useState(false);
    const [priceRevision,setPriceRevision]=useState(0);

    const router =
        useRouter();


    const {
        branch
    } =
        useSelectedBranch();


    const {
        items,
        itemCount,
        subtotal,
        branchId,
        isEmpty
    } =
        useCart();


    /*
     * =========================================================
     * EXISTING PENDING ORDER
     * =========================================================
     *
     * When the customer came back from Offers to add items,
     * this identifies the already-reserved backend order.
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


    /*
     * =========================================================
     * ORDER STATE
     * =========================================================
     */

    const [
        submitting,
        setSubmitting
    ] =
        useState(false);

    const [
    inventoryIssue,
    setInventoryIssue
] =
    useState<InventoryCheckResponse | null>(
        null
    );

    const [
        orderError,
        setOrderError
    ] =
        useState<string | null>(
            null
        );


    /*
     * Keep the same idempotency key when the user
     * retries after an uncertain/network failure.
     */
    const idempotencyKeyRef =
        useRef<string | null>(
            null
        );


    /*
     * =========================================================
     * PICKUP
     * =========================================================
     */

    const pickupSnapshot =
        useSyncExternalStore(
            subscribeToPickupSlot,
            getPickupSlotSnapshot,
            getServerPickupSlotSnapshot
        );


    const pickupClock = usePickupClock();
    const pickupSelection =
        useMemo(
            () => {const saved = parsePickupSlot(pickupSnapshot); return saved && (!pickupClock || pickupIsFresh(saved,new Date(pickupClock))) ? saved : null;},
            [
                pickupSnapshot, pickupClock
            ]
        );


    /*
     * =========================================================
     * CUSTOMER
     * =========================================================
     */

    const customerSnapshot =
        useSyncExternalStore(
            subscribeToCustomer,
            getCustomerSnapshot,
            getServerCustomerSnapshot
        );


    const customer =
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
     * VALIDATION
     * =========================================================
     */

    const invalidBranch =
        !branch
        ||
        branchId === null
        ||
        branch.id !== branchId;


    /*
     * =========================================================
     * CREATE OR UPDATE ORDER
     * =========================================================
     */

    const [priceChecking, setPriceChecking] = useState(false);
    const autoPriceKey = useRef("");
    const initialPriceKey = branch && pickupSelection && customer && items.length
        ? JSON.stringify([{branchId: branch.id, pickupSlotId: pickupSelection.slot.id,
            pickupType: pickupSelection.pickupType, customerName: customer.name, customerPhone: customer.phone,
            items: items.map(item => ({productId: item.product.id,
                quantity: item.product.saleMode === "UNIT" ? item.quantity : null, weightGrams: item.weightGrams}))}, undefined])
        : "";
    const pendingOrderNumber = pendingOrder?.orderNumber;
    useEffect(() => {
        if (!storefrontFeatures?.simplifiedCheckout || !quoteEnabled || invalidBranch || !initialPriceKey || pendingOrderNumber || !online) return;
        if (autoPriceKey.current === initialPriceKey) return;
        const request = JSON.parse(initialPriceKey)[0] as Parameters<typeof previewCheckoutQuote>[0];
        let active = true;
        const timer = window.setTimeout(() => {
            autoPriceKey.current = initialPriceKey;
            setPriceChecking(true); setAcceptedQuote(null);
            void previewCheckoutQuote(request)
                .then(quote => {if (active) setAcceptedQuote({key: initialPriceKey, quote});})
                .catch(() => {if (active) setOrderError("We couldn’t check your total. Tap Check price & offers to try again.");})
                .finally(() => {if (active) setPriceChecking(false);});
        }, 150);
        return () => {
            active = false; window.clearTimeout(timer);
            if (autoPriceKey.current === initialPriceKey) autoPriceKey.current = "";
            setPriceChecking(false);
        };
    }, [storefrontFeatures?.simplifiedCheckout, quoteEnabled, invalidBranch, initialPriceKey, pendingOrderNumber, online]);

    async function handlePlaceOrder() {
        if(addonBusy || priceChecking)return;

        if (accessible && !online) {
            setOrderError("You're offline. Your cart and pickup details are saved. Reconnect, then check your final price again.");
            return;
        }

        if (submitting) {
            return;
        }


        if (
            !branch
            ||
            branchId === null
            ||
            branch.id !== branchId
        ) {

            setOrderError(
                "Pickup branch does not match this cart."
            );

            return;
        }


        if (!pickupSelection) {

            setOrderError(
                translate("Please select a pickup date and time.")
            );

            return;
        }


        if (!customer) {

            setOrderError(
                translate("Please enter your customer details.")
            );

            return;
        }


        if (items.length === 0) {

            setOrderError(
                "Your cart is empty."
            );

            return;
        }


        const customerPhone =
            customer.phone.replace(
                /\D/g,
                ""
            );


        if (
            !/^[6-9][0-9]{9}$/.test(
                customerPhone
            )
        ) {

            setOrderError(
                translate("Please enter a valid 10-digit Indian mobile number.")
            );

            return;
        }


        const customerName =
            customer.name.trim();


        if (
            customerName.length < 2
        ) {

            setOrderError(
                translate("Please enter a valid customer name.")
            );

            return;
        }


        const requestItems =
            items.map(
                item => ({
                    productId:
                        item.product.id,

                    quantity:
                        item.product.saleMode === "UNIT"
                            ? item.quantity
                            : null,

                    weightGrams:
                        item.product.saleMode === "WEIGHT"
                            ? item.weightGrams
                            : null
                })
            );


        const localPendingOrderCandidate =
            pendingOrder !== null
            &&
            pendingOrder.fulfillmentType !== "DELIVERY"
            &&
            pendingOrder.orderStatus ===
                "PENDING_PAYMENT"
            &&
            pendingOrder.branchId ===
                branch.id;

        const quoteRequest: CreateOrderRequest = {
            branchId: branch.id,
            pickupSlotId: pickupSelection.slot.id,
            customerName,
            customerPhone,
            pickupType: pickupSelection.pickupType,
            items: requestItems
        };
        let quoteOrderNumber: string | undefined;
        setSubmitting(true);
setOrderError(null);
setInventoryIssue(null);
setPickupRecovery(false);

try {

    // Resolve the browser's pending-order hint before binding a signed quote.
    // An expired order must never supply a quote token for a new order.
    let reusablePendingOrder = false;
    if (localPendingOrderCandidate && pendingOrder) {
        const serverOrder = await getCustomerOrder(pendingOrder.orderNumber);
        const expiresAt = parseBusinessTimestamp(serverOrder.reservationExpiresAt).getTime();
        const action = pendingCheckoutAction(serverOrder, expiresAt, Date.now());
        reusablePendingOrder = action === "reuse";
        if (!reusablePendingOrder) {
            if (quoteEnabled && action === "payment") {
                router.push(`/checkout/payment/${encodeURIComponent(pendingOrder.orderNumber)}`);
                return;
            }
            if (quoteEnabled && action === "paid") {
                if (pendingOrder.cartFingerprint === currentCartFingerprint) {
                    router.push(`/orders/${encodeURIComponent(pendingOrder.orderNumber)}`);
                    return;
                }
            }
            clearPendingOrder();
            setAcceptedQuote(null);
            idempotencyKeyRef.current = null;
        }
    }

    quoteOrderNumber = reusablePendingOrder ? pendingOrder?.orderNumber : undefined;
    const quoteKey = JSON.stringify([quoteRequest, quoteOrderNumber]);

    const ownReservationCheck = quoteOrderNumber && pendingOrder?.pickupSlotId===pickupSelection.slot.id ? await apiClient<{orderable:boolean}>(`/api/menu/pickup-addons/check?branchId=${branch.id}&orderNumber=${encodeURIComponent(quoteOrderNumber)}`,{method:"POST",body:JSON.stringify({serviceDate:pickupSelection.date,items:requestItems}),credentials:"include"}) : null;
    const inventory = reusablePendingOrder ? null :
        await checkInventory(
            branch.id,
            {
                serviceDate:
                    pickupSelection.date,

                items:
                    requestItems
            }
        );


    if (
        inventory?.enforcementEnabled
        &&
        !inventory.orderable
    ) {

        setInventoryIssue(inventory);
        setAdjustingCheckout(true);
        return;
    }

    if(ownReservationCheck&&!ownReservationCheck.orderable){setOrderError("Your updated cart needs a quantity or pickup adjustment. Check the options here before continuing.");setAdjustingCheckout(true);return;}
    if (quoteEnabled) {
        if (!acceptedQuote || acceptedQuote.key !== quoteKey ||
            Date.parse(acceptedQuote.quote.expiresAt) <= Date.now()) {
            const refreshed = await previewCheckoutQuote(quoteRequest, quoteOrderNumber);
            setAcceptedQuote({key: quoteKey, quote: refreshed});
            setQuoteNotice(acceptedQuote?.key === quoteKey
                ? {oldTotal: acceptedQuote.quote.totalAmount, newTotal: refreshed.totalAmount} : null);
            return;
        }
    }


            /*
             * =================================================
             * UPDATE EXISTING PENDING ORDER
             * =================================================
             *
             * This is the Add Items flow.
             *
             * The backend keeps:
             *
             * - same order ID / number
             * - same reservation expiry
             *
             * It can atomically transfer the reservation to
             * the newly selected pickup time and recalculates
             * prices, tax, priority charge and rebate eligibility.
             */
            if (reusablePendingOrder && pendingOrder) {

                    const updateRequest:
                        UpdatePendingOrderRequest =
                        {
                            pickupSlotId:
                                pickupSelection.slot.id,

                            pickupType:
                                pickupSelection.pickupType,

                            items:
                                requestItems,
                            quoteToken: quoteEnabled ? acceptedQuote?.quote.token : undefined
                        };


                    const response =
                        await updatePendingCheckout(
                            pendingOrder.orderNumber,
                            updateRequest
                        );


                    if (
                        response.orderStatus !==
                        "PENDING_PAYMENT"
                    ) {

                        setOrderError(
                            `Order cannot continue because its status is ${response.orderStatus}.`
                        );

                        return;
                    }


                    savePendingOrder({
                        orderId:
                            response.id,

                        orderNumber:
                            response.orderNumber,

                        orderStatus:
                            response.orderStatus,

                        branchId:
                            response.branchId,

                        pickupSlotId:
                            response.pickupSlotId,

                        totalAmount:
                            response.totalAmount,

                        reservationExpiresAt:
                            response.reservationExpiresAt,

                        createdAt:
                            response.createdAt,

                        cartFingerprint:
                            createCartFingerprint(
                                items
                            )
                    });



                    return;
            }


            /*
             * =================================================
             * CREATE FIRST / REPLACEMENT BACKEND ORDER
             * =================================================
             */

            const createRequest:
                CreateOrderRequest =
                {
                    branchId:
                        branch.id,

                    pickupSlotId:
                        pickupSelection.slot.id,

                    customerName,

                    customerPhone,

                    pickupType:
                        pickupSelection.pickupType,

                    items:
                        requestItems,
                    quoteToken: quoteEnabled ? acceptedQuote?.quote.token : undefined
                };


            /*
             * One logical create-order operation gets one
             * idempotency key.
             */
            if (
                !idempotencyKeyRef.current
            ) {

                idempotencyKeyRef.current =
                    createIdempotencyKey();
            }


            const response =
                await createOrder(
                    createRequest,
                    idempotencyKeyRef.current
                );


            /*
             * Only a newly-created order is added to browser
             * history. Updating the same pending order must not
             * create another history entry.
             */
            addOrderToHistory({
                orderNumber:
                    response.orderNumber,

                createdAt:
                    response.createdAt
            });


            if (
                response.orderStatus ===
                "PENDING_PAYMENT"
            ) {

                savePendingOrder({
                    orderId:
                        response.id,

                    orderNumber:
                        response.orderNumber,

                    orderStatus:
                        response.orderStatus,

                    branchId:
                        response.branchId,

                    pickupSlotId:
                        response.pickupSlotId,

                    totalAmount:
                        response.totalAmount,

                    reservationExpiresAt:
                        response.reservationExpiresAt,

                    createdAt:
                        response.createdAt,

                    cartFingerprint:
                        createCartFingerprint(
                            items
                        )
                });



                return;
            }


            setOrderError(
                `Order cannot continue because its status is ${response.orderStatus}.`
            );

        } catch (exception) {

            setAcceptedQuote(null);
            const quoteChanged = quoteEnabled && exception instanceof Error &&
                /quote expired|price quote expired|price or pickup details changed|review the current price/i.test(exception.message);
            if (quoteChanged) {
                try {
                    let refreshOrderNumber = quoteOrderNumber;
                    if (refreshOrderNumber) {
                        const latest = await getCustomerOrder(refreshOrderNumber);
                        const expiry = parseBusinessTimestamp(latest.reservationExpiresAt).getTime();
                        const action = pendingCheckoutAction(latest, expiry, Date.now());
                        if (action === "payment") {
                            router.push(`/checkout/payment/${encodeURIComponent(refreshOrderNumber)}`);
                            return;
                        }
                        if (action === "paid") {
                            if (pendingOrder?.cartFingerprint === currentCartFingerprint) {
                                router.push(`/orders/${encodeURIComponent(refreshOrderNumber)}`);
                                return;
                            }
                        }
                        if (action === "replace" || action === "paid") {
                            clearPendingOrder();
                            refreshOrderNumber = undefined;
                            idempotencyKeyRef.current = null;
                        }
                    }
                    const refreshed = await previewCheckoutQuote(quoteRequest, refreshOrderNumber);
                    const refreshedKey = JSON.stringify([quoteRequest, refreshOrderNumber]);
                    setAcceptedQuote({key: refreshedKey, quote: refreshed});
                    setQuoteNotice(acceptedQuote?.key === refreshedKey
                        ? {oldTotal: acceptedQuote.quote.totalAmount, newTotal: refreshed.totalAmount} : null);
                    setOrderError(null);
                    return;
                } catch (refreshError) {
                    console.warn("Unable to refresh checkout quote:", refreshError);
                    setOrderError("We couldn't refresh your total. Your cart is saved; please try again.");
                    return;
                }
            }
            setQuoteNotice(null);
            // A slot can cease to fit the cart between the initial preview and reservation.
            // Offer newly checked times here, while keeping all checkout fields intact.
            if (exception instanceof Error && /preparation time|ready later|later pickup|pickup time is no longer available/i.test(exception.message)) {
                setPickupRecovery(true);setAdjustingCheckout(true);
            }

            console.error(
                "Unable to prepare order:",
                exception
            );


            setOrderError(
                checkoutErrorMessage(
                    exception
                )
            );

        } finally {

            setSubmitting(
                false
            );
        }
    }


    /*
     * =========================================================
     * EMPTY CART
     * =========================================================
     */

    if (isEmpty) {

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
                            🛍️
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            <T text="Your cart is empty" /></h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            <T text="Add some items before reviewing your order." /></p>


                        <Link
                            href="/menu"
                            className="
                                mt-6
                                inline-flex
                                min-h-12
                                items-center
                                justify-center
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-semibold
                                text-white
                            "
                        >
                            <T text="Explore Menu" /><LinkFeedback /></Link>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * INVALID BRANCH
     * =========================================================
     */

    if (invalidBranch) {

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
                            📍
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            <T text="Branch mismatch" /></h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            <T text="Please select the branch associated with your cart." /></p>


                        <Link
                            href="/"
                            className="
                                mt-6
                                inline-flex
                                min-h-12
                                items-center
                                justify-center
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-semibold
                                text-white
                            "
                        >
                            <T text="Select Branch" /><LinkFeedback /></Link>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * MISSING PICKUP
     * =========================================================
     */

    if (!pickupSelection) {

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
                            🕐
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            <T text="Pickup time required" /></h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            <T text="Select a pickup date and time before reviewing your order." /></p>


                        <Link
                            href="/checkout/pickup"
                            className="
                                mt-6
                                inline-flex
                                min-h-12
                                items-center
                                justify-center
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-semibold
                                text-white
                            "
                        >
                            <T text="Choose Pickup" /><LinkFeedback /></Link>

                    </div>

                </section>

            </AppShell>
        );
    }


    /*
     * =========================================================
     * MISSING CUSTOMER
     * =========================================================
     */

    if (!customer) {

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
                            👤
                        </div>


                        <h1
                            className="
                                mt-4
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            <T text="Customer details required" /></h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            <T text="Enter your name and phone number before reviewing your order." /></p>


                        <Link
                            href="/checkout/customer"
                            className="
                                mt-6
                                inline-flex
                                min-h-12
                                items-center
                                justify-center
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                font-semibold
                                text-white
                            "
                        >
                            <T text="Enter Details" /><LinkFeedback /></Link>

                    </div>

                </section>

            </AppShell>
        );
    }


    const selectedSlot =
        pickupSelection.slot;


    const priorityPickup =
        pickupSelection.pickupType ===
        "PRIORITY";


    const preparedOrderNumber =
        pendingOrder
        &&
        pendingOrder.fulfillmentType !== "DELIVERY"
        &&
        pendingOrder.orderStatus ===
            "PENDING_PAYMENT"
        &&
        pendingOrder.branchId ===
            branch.id
        &&
        pendingOrder.pickupSlotId ===
            pickupSelection.slot.id
        &&
        (pendingOrder.cartFingerprint ===
            currentCartFingerprint || addonBusy)
            ? pendingOrder.orderNumber
            : null;


    /*
     * =========================================================
     * REVIEW PAGE
     * =========================================================
     */

    return (
        <AppShell>
            <CheckoutExperienceFrame enabled={storefrontFeatures?.checkoutExperienceV2 === true} stage="review" allowBranchChange>

            <section
                className="
                    mx-auto
                    w-full
                    min-w-0
                    max-w-2xl
                    px-4
                    pb-28
                    pt-5
                    sm:px-6
                    sm:pt-7
                "
            >

                {/* Header */}

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
                            <T text="Pickup" /></span>


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
                            <T text="Your details" /></span>


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
                            <T text="Review & offers" /></span>

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
                        <T text="Almost there" /></p>


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
                        <T text="Review your pickup order" /></h1>


                    <p
                        className="
                            mt-2
                            max-w-xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        <T text="Check your pickup time, contact details and items. Then check eligible offers or enter an exclusive code without leaving this page." /></p>

                </div>


                {/* Pickup */}

                <div
                    className="
                        min-w-0
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

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#c88a20]
                                "
                            >
                                <T text="Pickup details" /></p>


                            <p
                                className="
                                    mt-2
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {branch.name}
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                {
                                    formatPickupDate(
                                        pickupSelection.date
                                    )
                                }
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    font-semibold
                                    text-[#7a1625]
                                "
                            >
                                {
                                    formatTime(
                                        selectedSlot.startTime
                                    )
                                }

                                {" – "}

                                {
                                    formatTime(
                                        selectedSlot.endTime
                                    )
                                }
                            </p>


                            <div
                                className="
                                    mt-3
                                "
                            >

                                <span
                                    className={
                                        priorityPickup
                                            ? "inline-flex rounded-full bg-[#fff4e5] px-3 py-1 text-xs font-bold text-[#7a1625]"
                                            : "inline-flex rounded-full bg-green-50 px-3 py-1 text-xs font-semibold text-green-700"
                                    }
                                >
                                    {
                                        priorityPickup
                                            ? translate("Priority Pickup")
                                            : translate("Normal Pickup")
                                    }
                                </span>

                            </div>


                            {
                                priorityPickup
                                && (

                                    <div
                                        className="
                                            mt-3
                                            rounded-xl
                                            border
                                            border-[#f0d6aa]
                                            bg-[#fffaf3]
                                            px-3
                                            py-2
                                        "
                                    >

                                        <p
                                            className="
                                                text-xs
                                                text-[#756763]
                                            "
                                        >
                                            <T text="Normal capacity is full for this pickup time." /></p>


                                        <p
                                            className="
                                                mt-1
                                                text-sm
                                                font-bold
                                                text-[#7a1625]
                                            "
                                        >
                                            <T text="Priority charge:" />{" "}
                                            {
                                                formatCurrency(
                                                    selectedSlot
                                                        .priorityCharge
                                                )
                                            }
                                        </p>

                                    </div>

                                )
                            }

                        </div>


                        <div className="flex flex-wrap items-center justify-end gap-2">
                            {inPlaceBranchSwitch && !storefrontFeatures?.checkoutExperienceV2 && <BranchSelector compact />}
                        {storefrontFeatures?.checkoutExperienceV2
                            ? <button type="button" onClick={() => setAdjustingCheckout(true)}
                                aria-expanded={adjustingCheckout}
                                className="min-h-11 rounded-lg px-3 py-2 text-sm font-semibold text-[#7a1625] hover:bg-[#fff0dc]">
                                <T text="Change time" /></button>
                            : <Link href="/checkout/pickup"
                                className="shrink-0 rounded-lg px-2 py-1 text-sm font-semibold text-[#7a1625] transition hover:bg-[#fff0dc]">
                                {inPlaceBranchSwitch ? translate("Change time") : translate("Change")}
                            <LinkFeedback /></Link>}
                        </div>

                    </div>

                </div>


                {/* Customer */}

                <div
                    className="
                        mt-4
                        min-w-0
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-5
                        shadow-[0_6px_24px_rgba(60,30,20,0.05)]
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
                                <T text="Pickup contact" /></p>


                            <p
                                className="
                                    mt-2
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {customer.name}
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                +91 {customer.phone}
                            </p>

                        </div>


                        <Link
                            href="/checkout/customer"
                            className="
                                shrink-0
                                rounded-lg
                                px-2
                                py-1
                                text-sm
                                font-semibold
                                text-[#7a1625]
                                transition
                                hover:bg-[#fff0dc]
                            "
                        >
                            <T text="Change" /><LinkFeedback /></Link>

                    </div>

                </div>


                {/* Items */}

                <div
                    className="
                        mt-4
                        min-w-0
                        overflow-hidden
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        shadow-[0_6px_24px_rgba(60,30,20,0.05)]
                    "
                >

                    <div
                        className="
                            flex
                            items-center
                            justify-between
                            gap-4
                            border-b
                            border-[#eadfd6]
                            px-5
                            py-4
                        "
                    >

                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-[0.12em]
                                    text-[#c88a20]
                                "
                            >
                                <T text="Your order" /></p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                {itemCount} {translate(itemCount === 1 ? "item" : "items")}
                            </p>

                        </div>


                        <button type="button" onClick={()=>setAdjustingCheckout(true)}
                            className="
                                shrink-0
                                rounded-lg
                                px-2
                                py-1
                                text-sm
                                font-semibold
                                text-[#7a1625]
                                transition
                                hover:bg-[#fff0dc]
                            "
                        >
                            <T text="Edit" /></button>

                    </div>


                    <div
                        className="
                            divide-y
                            divide-[#eadfd6]
                        "
                    >

                        {
                            items.map(
                                item => (

                                    <div
                                        key={
                                            item.product.id
                                        }
                                        className="
                                            flex
                                            min-w-0
                                            items-start
                                            justify-between
                                            gap-4
                                            px-5
                                            py-4
                                        "
                                    >

                                        <div
                                            className="
                                                min-w-0
                                            "
                                        >

                                            <p
                                                className="
                                                    font-semibold
                                                    text-[#241715]
                                                "
                                            >
                                                {
                                                    item.product.name
                                                }
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    text-xs
                                                    text-[#756763]
                                                "
                                            >
                                                {cartItemSelection(item)}
                                            </p>

                                        </div>


                                        <p
                                            className="
                                                shrink-0
                                                font-semibold
                                                text-[#241715]
                                            "
                                        >
                                            {
                                                formatCurrency(
                                                    cartItemSubtotal(item)
                                                )
                                            }
                                        </p>

                                    </div>

                                )
                            )
                        }

                    </div>


                    {/* Summary */}

                    <div
                        className="
                            border-t
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-5
                            py-4
                        "
                    >

                        <div
                            className="
                                flex
                                items-center
                                justify-between
                            "
                        >

                            <span
                                className="
                                    text-sm
                                    font-medium
                                    text-[#756763]
                                "
                            >
                                {
                                    itemCount
                                }
                                {" "}
                                {
                                    translate(itemCount === 1 ? "item" : "items")
                                }
                            </span>


                            <span
                                className="
                                    text-lg
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


                        {
                            priorityPickup
                            && (

                                <div
                                    className="
                                        mt-3
                                        flex
                                        items-center
                                        justify-between
                                        border-t
                                        border-[#eadfd6]
                                        pt-3
                                    "
                                >

                                    <span
                                        className="
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        <T text="Priority pickup" /></span>


                                    <span
                                        className="
                                            text-sm
                                            font-bold
                                            text-[#7a1625]
                                        "
                                    >
                                        +
                                        {
                                            formatCurrency(
                                                selectedSlot
                                                    .priorityCharge
                                            )
                                        }
                                    </span>

                                </div>

                            )
                        }


                        <div
                            className="
                                mt-4
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-3
                            "
                        >

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                <T text="No surprises at payment" /></p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                <T text="See item prices, GST, any pickup charge and your savings together before you pay." /></p>

                        </div>

                    </div>

                </div>


                {/* Error */}

                {
                    orderError
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
                                {pickupRecovery ? "Let’s find a better pickup time" : "We couldn’t continue"}
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-red-600
                                "
                            >
                                {pickupRecovery ? "That time no longer gives us enough time to prepare every item. Choose another available time below." : orderError}
                            </p>

                        </div>

                    )
                }

                {(pickupRecovery || editingPickup) && branch && pickupSelection && storefrontFeatures &&
                    <ReviewPickupRecovery mode={editingPickup ? "all" : "later"} branchId={branch.id} items={items} today={storefrontFeatures.today}
                        days={storefrontFeatures.futureOrderingDays} rejectedSlotId={pickupSelection.slot.id}
                        rejectedDate={pickupSelection.date} rejectedTime={pickupSelection.slot.startTime}
                        onSelected={() => {setOrderError(null); setPickupRecovery(false); setEditingPickup(false); setAcceptedQuote(null);
                            idempotencyKeyRef.current = null;}} />}


                {accessible && !online && <div role="alert" className="mt-4 rounded-xl border border-[#c88a20] bg-[#fff4e5] p-4 text-sm">
                    <T text="You're offline. Your cart is saved. Reconnect and check your final price again before continuing." /></div>}

                {
                    inventoryIssue
                    && (
                        <ReviewInventoryIssue
                            issue={inventoryIssue}
                            branchPhone={branch.phone} onAdjust={()=>setAdjustingCheckout(true)}
                        />
                    )
                }


                {storefrontFeatures?.pickupAddOns && pickupSelection?.pickupType==="NORMAL" && branch && !preparedOrderNumber && <PickupAddOns key={`${branch.id}:${pickupSelection.date}`} branchId={branch.id} date={pickupSelection.date} disabled={submitting || pickupRecovery} onBusy={setAddonBusy} onAdjust={()=>setAdjustingCheckout(true)} onAdded={async()=>{setAcceptedQuote(null);setQuoteNotice(null);setOrderError(null);setInventoryIssue(null);if(!quoteEnabled)return;const cart=parseCart(getCartSnapshot()),customer=parseCustomerDetails(getCustomerSnapshot());if(!customer)throw new Error("Check your contact details.");const request={branchId:branch.id,pickupSlotId:pickupSelection.slot.id,pickupType:pickupSelection.pickupType,customerName:customer.name,customerPhone:customer.phone,items:cart.items.map(i=>({productId:i.product.id,quantity:i.product.saleMode==="UNIT"?i.quantity:null,weightGrams:i.weightGrams}))};const number=pendingOrder?.orderStatus==="PENDING_PAYMENT"&&pendingOrder.branchId===branch.id?pendingOrder.orderNumber:undefined;const quote=await previewCheckoutQuote(request,number);setAcceptedQuote({key:JSON.stringify([request,number]),quote});}}/>}
                {/* Offers + final price */}

                {quoteEnabled && !preparedOrderNumber && acceptedQuote && (
                    <div className="mt-5 rounded-2xl border border-[#eadfd6] bg-white p-4" role="status">
                        <p className="font-bold"><T text="Your price, before offers" /></p>
                        {quoteExpired && <p className="mt-2 rounded-xl bg-[#fff4e5] p-3 text-sm" role="status">
                            <T text="This price needs refreshing. Your cart and pickup details are saved." /></p>}
                        {quoteNotice && <p className="mt-2 rounded-xl bg-[#fff4e5] p-3 text-sm" role="alert">
                            {quoteNotice.oldTotal === quoteNotice.newTotal
                                ? "Your price has been refreshed. Please review it once more before continuing."
                                : `Your total changed from ${formatCurrency(Number(quoteNotice.oldTotal))} to ${formatCurrency(Number(quoteNotice.newTotal))}. Please review the new amount before continuing.`}
                        </p>}
                        {acceptedQuote.quote.items.map((line, index) => (
                            <p className="mt-2 text-sm" key={`${line.name}-${index}`}>
                                {line.name}: ₹{line.total} (unit ₹{line.unitPrice}, tax {line.taxRate}%)
                            </p>
                        ))}
                        <p className="mt-3 text-sm"><T text="Items" /> ₹{acceptedQuote.quote.subtotal} · <T text="Tax" /> ₹{acceptedQuote.quote.taxAmount} · <T text="Pickup charge" /> ₹{acceptedQuote.quote.priorityCharge}</p>
                        {Number(acceptedQuote.quote.convenienceFee ?? 0)>0 && <p className="mt-2 text-sm">Convenience fee ₹{acceptedQuote.quote.convenienceFee} (includes ₹{acceptedQuote.quote.convenienceFeeTax} tax)</p>}
                        <p className="mt-2 font-bold"><T text="Total before optional offers" /> ₹{acceptedQuote.quote.totalAmount}</p>
                        {!quoteExpired && <p className="mt-2 text-xs"><T text="This price is available until" /> {new Date(acceptedQuote.quote.expiresAt).toLocaleTimeString("en-IN", {timeZone: "Asia/Kolkata"})} IST. <Link className="underline" href="/about#cancellation-policy"><T text="See the cancellation policy" /><LinkFeedback /></Link> <T text="before paying." /></p>}
                    </div>
                )}

                {
                    preparedOrderNumber
                        ? (
                            <CheckoutOffersPanel key={`${preparedOrderNumber}:${priceRevision}`} reviewRequired={priceRevision>0} onCartMutationBusy={setAddonBusy} onUpdateError={setOrderError}
                                orderNumber={
                                    preparedOrderNumber
                                }
                            />
                        )
                        : (
                            <div
                                className={`
                                    checkout-review-desktop-action
                                    mt-5
                                    rounded-3xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-4
                                    shadow-[0_6px_24px_rgba(60,30,20,0.06)]
                                `}
                            >

                                <div
                                    className="
                                        mb-4
                                    "
                                >

                                    <p
                                        className="
                                            text-sm
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        <T text="Check your final price" /></p>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            leading-5
                                            text-[#756763]
                                        "
                                    >
                                        <T text="Review your total, then explore any available offers before paying." /></p>

                                </div>


                                <button
                                    type="button"
                                    disabled={
                                        priceChecking || submitting || addonBusy || pickupRecovery || (accessible && !online)
                                    }
                                    onClick={
                                        handlePlaceOrder
                                    }
                                    className="
                                        flex
                                        min-h-14
                                        w-full
                                        items-center
                                        justify-center
                                        rounded-xl
                                        bg-[#7a1625]
                                        px-6
                                        font-bold
                                        text-white!
                                        transition

                                        focus-visible:outline-none
                                        focus-visible:ring-2
                                        focus-visible:ring-[#c88a20]
                                        focus-visible:ring-offset-2

                                        hover:bg-[#5d0f1b]
                                        active:scale-[0.99]

                                        disabled:cursor-not-allowed
                                        disabled:bg-[#c9b9b4]
                                    "
                                >
                                    {
                                        priceChecking || submitting
                                            ? translate("Preparing your order...")
                                            : quoteExpired
                                                ? translate("Refresh your total")
                                            : quoteEnabled && acceptedQuote
                                                ? translate("Accept price and reserve pickup")
                                                : translate("Check Final Price & Offers")
                                    }
                                </button>


                                <button
                                    type="button"
                                    disabled={
                                        submitting
                                    }
                                    onClick={
                                        () =>
                                            router.push(
                                                "/cart"
                                            )
                                    }
                                    className="
                                        mt-3
                                        flex
                                        min-h-11
                                        w-full
                                        items-center
                                        justify-center
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]

                                        disabled:opacity-50
                                    "
                                >
                                    <T text="← Back to Cart" /></button>

                            </div>
                        )
                }

                {adjustingCheckout && <CheckoutAdjustmentDialog items={items} pickup={pickupSelection} branchId={branch.id} days={storefrontFeatures?.futureOrderingDays??30} message={inventoryIssue?inventoryIssue.items.filter(i=>!i.orderable).map(i=>`${i.productName}: requested ${i.inventoryUnit==="GRAM"?formatWeight(i.requestedQuantity):`${i.requestedQuantity} pieces`}; available ${i.inventoryUnit==="GRAM"?formatWeight(i.availableQuantity):`${i.availableQuantity} pieces`}. Reduce the quantity or choose another pickup.`).join(" "):orderError??undefined} onClose={()=>setAdjustingCheckout(false)} onApply={async(changedItems,changedPickup)=>{const customer=parseCustomerDetails(getCustomerSnapshot());if(!customer)throw new Error("Check your contact details first.");const request={branchId:branch.id,pickupSlotId:changedPickup.slot.id,pickupType:changedPickup.pickupType,customerName:customer.name,customerPhone:customer.phone,items:changedItems.map(i=>({productId:i.product.id,quantity:i.product.saleMode==="UNIT"?i.quantity:null,weightGrams:i.weightGrams}))};const cartSnapshot=getCartSnapshot(),pickupSnapshot=getPickupSlotSnapshot();const number=pendingOrder?.orderStatus==="PENDING_PAYMENT"&&pendingOrder.branchId===branch.id?pendingOrder.orderNumber:undefined;if(number){const current=await getCustomerOrder(number);if(current.orderStatus!=="PENDING_PAYMENT"||current.paymentStatus!=null)throw new Error("Review this order’s payment status before changing it.");}const quote=quoteEnabled?await previewCheckoutQuote(request,number):null;if(cartSnapshot!==getCartSnapshot()||pickupSnapshot!==getPickupSlotSnapshot())throw new Error("Your cart or pickup changed elsewhere. Reopen adjustments.");const updated=number?await updatePendingCheckout(number,{pickupSlotId:changedPickup.slot.id,pickupType:changedPickup.pickupType,items:request.items,quoteToken:quote?.token}):null;saveCart({branchId:branch.id,items:changedItems});savePickupSlot(changedPickup);if(updated&&pendingOrder)savePendingOrder({...pendingOrder,pickupSlotId:updated.pickupSlotId,totalAmount:updated.totalAmount,reservationExpiresAt:updated.reservationExpiresAt,cartFingerprint:createCartFingerprint(changedItems)});setPriceRevision(value=>value+1);setAcceptedQuote(updated||!quote?null:{key:JSON.stringify([request,number]),quote});setInventoryIssue(null);setOrderError(null);}}/>}
                {!preparedOrderNumber && <CheckoutMobileAction label={priceChecking ? translate("Checking your total…") : submitting ? translate("Checking…") : quoteEnabled && acceptedQuote ? translate("Review & reserve") : translate("Check price & offers")} amount={acceptedQuote ? Number(acceptedQuote.quote.totalAmount) : undefined} disabled={priceChecking || submitting || addonBusy || pickupRecovery || (accessible && !online)} onContinue={()=>void handlePlaceOrder()} />}
            </section>

            </CheckoutExperienceFrame>
        </AppShell>
    );
}
