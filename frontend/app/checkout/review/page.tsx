"use client";

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
import {parseBusinessTimestamp} from "@/lib/businessTime";
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


function formatWeight(
    weightGrams: number
): string {

    if (weightGrams >= 1000) {
        const kilograms =
            weightGrams / 1000;

        return `${new Intl.NumberFormat(
            "en-IN",
            {
                maximumFractionDigits: 3
            }
        ).format(kilograms)} kg`;
    }

    return `${new Intl.NumberFormat(
        "en-IN"
    ).format(weightGrams)} g`;
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
    branchPhone
}: {
    issue: InventoryCheckResponse;
    branchPhone: string | null;
}) {

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
                Quantity confirmation required
            </p>

            <h2
                className="
                    mt-2
                    text-xl
                    font-extrabold
                    text-[#7a1625]
                "
            >
                We cannot confirm the complete order
                for this pickup date
            </h2>

            <p
                className="
                    mt-2
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                Some products need more preparation or
                stock than is currently available online.
            </p>

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
                                            You requested
                                        </p>

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
                                            Available online
                                        </p>

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
                                    Please reduce the quantity,
                                    choose another date, or contact
                                    the branch for a large-order
                                    confirmation.
                                </p>
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
                            Earliest available date
                        </p>

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
                            Choose This Pickup Date
                        </Link>
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
                    Need this quantity specially prepared?
                </p>

                <p
                    className="
                        mt-1
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    Please call the selected branch. Our team
                    can check production capacity and confirm
                    whether this large order can be prepared.
                </p>

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
                            Call {branchPhone}
                        </a>
                    )
                }
            </div>

            <Link
                href="/cart"
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
                Adjust Cart Quantity
            </Link>
        </div>
    );
}

export default function ReviewPage() {

    const storefrontFeatures = useStorefrontFeatures();
    const accessible = storefrontFeatures?.accessibleOrderingV2 === true;
    const online = useOnlineStatus();
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


    const pickupSelection =
        useMemo(
            () =>
                parsePickupSlot(
                    pickupSnapshot
                ),
            [
                pickupSnapshot
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

    async function handlePlaceOrder() {

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
                "Please select a pickup date and time."
            );

            return;
        }


        if (!customer) {

            setOrderError(
                "Please enter your customer details."
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
                "Please enter a valid 10-digit Indian mobile number."
            );

            return;
        }


        const customerName =
            customer.name.trim();


        if (
            customerName.length < 2
        ) {

            setOrderError(
                "Please enter a valid customer name."
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

    const inventory =
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
        inventory.enforcementEnabled
        &&
        !inventory.orderable
    ) {

        setInventoryIssue(
            inventory
        );

        return;
    }

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
                setPickupRecovery(true);
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
                            Your cart is empty
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            Add some items before reviewing
                            your order.
                        </p>


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
                            Explore Menu
                        </Link>

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
                            Branch mismatch
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            Please select the branch
                            associated with your cart.
                        </p>


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
                            Select Branch
                        </Link>

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
                            Pickup time required
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            Select a pickup date and time
                            before reviewing your order.
                        </p>


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
                            Choose Pickup
                        </Link>

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
                            Customer details required
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-[#756763]
                            "
                        >
                            Enter your name and phone number
                            before reviewing your order.
                        </p>


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
                            Enter Details
                        </Link>

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
        pendingOrder.orderStatus ===
            "PENDING_PAYMENT"
        &&
        pendingOrder.branchId ===
            branch.id
        &&
        pendingOrder.pickupSlotId ===
            pickupSelection.slot.id
        &&
        pendingOrder.cartFingerprint ===
            currentCartFingerprint
            ? pendingOrder.orderNumber
            : null;


    /*
     * =========================================================
     * REVIEW PAGE
     * =========================================================
     */

    return (
        <AppShell>

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
                                text-[#756763]
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
                                text-[#7a1625]
                            "
                        >
                            Review & offers
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
                        Almost there
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
                        Review your pickup order
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
                        Check your pickup time, contact details and items.
                        Then check eligible offers or enter an exclusive code
                        without leaving this page.
                    </p>

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
                                Pickup details
                            </p>


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
                                            ? "Priority Pickup"
                                            : "Normal Pickup"
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
                                            Normal capacity is full
                                            for this pickup time.
                                        </p>


                                        <p
                                            className="
                                                mt-1
                                                text-sm
                                                font-bold
                                                text-[#7a1625]
                                            "
                                        >
                                            Priority charge:{" "}
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
                            {inPlaceBranchSwitch && <BranchSelector compact />}
                        <Link
                            href="/checkout/pickup"
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
                            {inPlaceBranchSwitch ? "Change time" : "Change"}
                        </Link>
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
                                Pickup contact
                            </p>


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
                            Change
                        </Link>

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
                                Your order
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                {itemCount} {itemCount === 1 ? "item" : "items"}
                            </p>

                        </div>


                        <Link
                            href="/cart"
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
                            Edit
                        </Link>

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
                                    itemCount === 1
                                        ? "item"
                                        : "items"
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
                                        Priority pickup
                                    </span>


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
                                No surprises at payment
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                See item prices, GST, any pickup charge and
                                your savings together before you pay.
                            </p>

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

                {pickupRecovery && branch && pickupSelection && storefrontFeatures &&
                    <ReviewPickupRecovery branchId={branch.id} items={items} today={storefrontFeatures.today}
                        days={storefrontFeatures.futureOrderingDays} rejectedSlotId={pickupSelection.slot.id}
                        rejectedDate={pickupSelection.date} rejectedTime={pickupSelection.slot.startTime}
                        onSelected={() => {setOrderError(null); setPickupRecovery(false); setAcceptedQuote(null);
                            idempotencyKeyRef.current = null;}} />}


                {accessible && !online && <div role="alert" className="mt-4 rounded-xl border border-[#c88a20] bg-[#fff4e5] p-4 text-sm">
                    You&apos;re offline. Your cart is saved. Reconnect and check your final price again before continuing.
                </div>}

                {
                    inventoryIssue
                    && (
                        <ReviewInventoryIssue
                            issue={inventoryIssue}
                            branchPhone={branch.phone}
                        />
                    )
                }


                {/* Offers + final price */}

                {quoteEnabled && !preparedOrderNumber && acceptedQuote && (
                    <div className="mt-5 rounded-2xl border border-[#eadfd6] bg-white p-4" role="status">
                        <p className="font-bold">Your price, before offers</p>
                        {quoteExpired && <p className="mt-2 rounded-xl bg-[#fff4e5] p-3 text-sm" role="status">
                            This price needs refreshing. Your cart and pickup details are saved.
                        </p>}
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
                        <p className="mt-3 text-sm">Items ₹{acceptedQuote.quote.subtotal} · Tax ₹{acceptedQuote.quote.taxAmount} · Pickup charge ₹{acceptedQuote.quote.priorityCharge}</p>
                        <p className="mt-2 font-bold">Total before optional offers ₹{acceptedQuote.quote.totalAmount}</p>
                        {!quoteExpired && <p className="mt-2 text-xs">This price is available until {new Date(acceptedQuote.quote.expiresAt).toLocaleTimeString("en-IN", {timeZone: "Asia/Kolkata"})} IST. <Link className="underline" href="/about#cancellation-policy">See the cancellation policy</Link> before paying.</p>}
                    </div>
                )}

                {
                    preparedOrderNumber
                        ? (
                            <CheckoutOffersPanel
                                orderNumber={
                                    preparedOrderNumber
                                }
                            />
                        )
                        : (
                            <div
                                className={`${accessible ? "max-sm:sticky max-sm:bottom-[env(safe-area-inset-bottom)] max-sm:z-30" : ""}
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
                                        Check your final price
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            leading-5
                                            text-[#756763]
                                        "
                                    >
                                        Review your total, then explore any available
                                        offers before paying.
                                    </p>

                                </div>


                                <button
                                    type="button"
                                    disabled={
                                        submitting || pickupRecovery || (accessible && !online)
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
                                        submitting
                                            ? "Preparing your order..."
                                            : quoteExpired
                                                ? "Refresh your total"
                                            : quoteEnabled && acceptedQuote
                                                ? "Accept price and reserve pickup"
                                                : "Check Final Price & Offers"
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
                                    ← Back to Cart
                                </button>

                            </div>
                        )
                }

            </section>

        </AppShell>
    );
}
