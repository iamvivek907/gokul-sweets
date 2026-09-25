"use client";

import Link from "next/link";
import SmartPickupSelection from "@/components/checkout/SmartPickupSelection";
import CartSwitchDialog from "@/components/cart/CartSwitchDialog";
import type {CartSwitchPreview} from "@/services/cartSwitchPreview";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {parseBusinessTimestamp} from "@/lib/businessTime";

import {
    useEffect,
    useMemo,
    useState,
    useSyncExternalStore
} from "react";

import {
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";

import CheckoutStateCard
    from "@/components/checkout/CheckoutStateCard";

import PickupSlotSkeleton
    from "@/components/checkout/PickupSlotSkeleton";

import PickupSlotDropdown
    from "@/components/checkout/PickupSlotDropdown";

import {
    useCart
} from "@/hooks/useCart";

import {
    useSelectedBranch
} from "@/hooks/useSelectedBranch";

import {
    getPickupSlots
} from "@/services/pickupApi";

import {
    getCustomerOrder
} from "@/services/orderApi";

import {
    savePickupSlot
} from "@/lib/checkoutStorage";

import {
    getPendingOrderSnapshot,
    getServerPendingOrderSnapshot,
    parsePendingOrder,
    subscribeToPendingOrder
} from "@/lib/pendingOrderStorage";

import type {
    PickupSlot,
    PickupType
} from "@/types/pickup";

import type {
    CustomerOrderResponse
} from "@/types/order";


interface LoadedCurrentReservation {

    lookupKey: string;

    order:
        CustomerOrderResponse
        | null;
}


function formatDateForApi(
    date: Date
): string {

    const year =
        date.getFullYear();

    const month =
        String(
            date.getMonth() + 1
        ).padStart(
            2,
            "0"
        );

    const day =
        String(
            date.getDate()
        ).padStart(
            2,
            "0"
        );


    return `${year}-${month}-${day}`;
}

function formatDateInTimeZone(
    date: Date,
    timeZone: string
): string {
    const formatter =
        new Intl.DateTimeFormat(
            "en-CA",
            {
                timeZone,
                year: "numeric",
                month: "2-digit",
                day: "2-digit"
            }
        );

    const parts =
        formatter.formatToParts(date);

    const year =
        parts.find(
            part => part.type === "year"
        )?.value;

    const month =
        parts.find(
            part => part.type === "month"
        )?.value;

    const day =
        parts.find(
            part => part.type === "day"
        )?.value;

    if (
        !year
        || !month
        || !day
    ) {
        return formatDateForApi(date);
    }

    return `${year}-${month}-${day}`;
}

function getToday():
    string {

    return formatDateInTimeZone(
        new Date(),
        "Asia/Kolkata"
    );
}


function getServerToday():
    string {

    return "";
}


function subscribeToDate():
    () => void {

    return () => {};
}


function getMaximumPickupDate(
    fromDate: string,
    numberOfDays: number
): string {
    const date =
        new Date(
            `${fromDate}T12:00:00+05:30`
        );

    date.setUTCDate(
        date.getUTCDate()
        + numberOfDays
    );

    return formatDateInTimeZone(
        date,
        "Asia/Kolkata"
    );
}


function formatDisplayDate(
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

function clampDateWithinBounds(
    value: string,
    minimum: string,
    maximum: string
): string {
    if (value < minimum) {
        return minimum;
    }
    if (maximum && value > maximum) {
        return maximum;
    }
    return value;
}


function isSlotAvailable(
    slot: PickupSlot
): boolean {

    if (!slot.active) {
        return false;
    }


    const normalAvailable =
        slot.remainingCapacity > 0;


    const priorityAvailable =
        slot.priorityEnabled
        &&
        slot.priorityRemainingCapacity > 0;


    return (
        normalAvailable
        ||
        priorityAvailable
    );
}



export default function PickupPage() {
    const {features, error: configurationError, retry} = useStorefrontConfiguration();
    const [fallback, setFallback] = useState(false);
    const pending = useSyncExternalStore(subscribeToPendingOrder, getPendingOrderSnapshot, getServerPendingOrderSnapshot);
    if (!features && !fallback) return <AppShell showSocialPopup={false}>
        {configurationError ? <div role="alert"><p>{configurationError}</p>
            <button className="min-h-11 p-3 underline" onClick={retry}>Retry settings</button>
            <button className="min-h-11 p-3 underline" onClick={() => setFallback(true)}>Use standard pickup selection</button></div>
            : <p role="status">Loading pickup options...</p>}
    </AppShell>;
    // Unauthenticated previews must not add back another order's private holds. Owned checkout edits keep their existing flow.
    if (features?.smartPickupSelection && !fallback && !parsePendingOrder(pending)) {
        return <>{configurationError && <p role="status" className="p-3 text-center text-sm">{configurationError} Keeping your pickup layout.
            <button onClick={retry} className="min-h-11 px-3 underline">Retry settings</button></p>}
            <SmartPickupSelection features={features} onFallback={() => setFallback(true)} /></>;
    }
    return <>{features?.smartPickupSelection && parsePendingOrder(pending) && <p className="p-4 text-center text-sm">
        Editing a reserved order: your existing pickup selector keeps its held stock.</p>}<LegacyPickupPage fallbackToday={features?.today ?? null} fallbackFutureOrderingDays={features?.futureOrderingDays ?? null}
        cartSwitchPreview={!!features?.cartSwitchPreview} /></>;
}

function LegacyPickupPage({
    fallbackToday,
    fallbackFutureOrderingDays,
    cartSwitchPreview
}: {
    fallbackToday: string | null;
    fallbackFutureOrderingDays: number | null;
    cartSwitchPreview: boolean;
}) {

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

    const [proposedDate, setProposedDate] = useState<string | null>(null);


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


    const pendingOrderNumber =
        pendingOrder?.orderNumber
        ?? null;


    const pendingOrderBranchId =
        pendingOrder?.branchId
        ?? null;


    const pendingOrderPickupSlotId =
        pendingOrder?.pickupSlotId
        ?? null;


    const currentReservationLookupKey =
        branch
        &&
        pendingOrderNumber
        &&
        pendingOrderBranchId ===
            branch.id
            ? `${branch.id}:${pendingOrderNumber}`
            : null;


    const today =
        useSyncExternalStore(
            subscribeToDate,
            () =>
                fallbackToday
                ?? getToday(),
            getServerToday
        );


    const maximumDate =
        today && fallbackFutureOrderingDays !== null
            ? getMaximumPickupDate(
                today,
                fallbackFutureOrderingDays
            )
            : today
                ? getMaximumPickupDate(
                    today,
                    7
                )
                : "";


    const [
        selectedDate,
        setSelectedDate
    ] =
        useState<string | null>(
            null
        );


    const [
        loadedCurrentReservation,
        setLoadedCurrentReservation
    ] =
        useState<LoadedCurrentReservation | null>(
            null
        );


    const [
        slots,
        setSlots
    ] =
        useState<PickupSlot[]>(
            []
        );


    const [
        selectedSlot,
        setSelectedSlot
    ] =
        useState<PickupSlot | null>(
            null
        );


    const [
        selectedPickupType,
        setSelectedPickupType
    ] =
        useState<PickupType | null>(
            null
        );


    const [
        loading,
        setLoading
    ] =
        useState(true);


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    const [
        validationError,
        setValidationError
    ] =
        useState<string | null>(
            null
        );


    const [
        reloadKey,
        setReloadKey
    ] =
        useState(0);


    const pickupDate =
        clampDateWithinBounds(
            selectedDate
            ?? today,
            today,
            maximumDate
        );


    /*
     * Ignore a response belonging to a previous order or
     * branch immediately, without synchronously resetting
     * state inside the loading effect.
     */
    const currentReservation =
        currentReservationLookupKey
        &&
        loadedCurrentReservation?.lookupKey ===
            currentReservationLookupKey
            ? loadedCurrentReservation.order
            : null;


    const currentReservedSlotId =
        currentReservation
            ? pendingOrderPickupSlotId
            : null;


    const currentReservationPickupDate =
        currentReservation?.pickupDate
        ?? null;


    const currentReservationPickupType =
        currentReservation?.pickupType
        ?? null;


    const ownsSelectedNormalReservation =
        selectedSlot?.id ===
            currentReservedSlotId
        &&
        currentReservationPickupType ===
            "NORMAL";


    const ownsSelectedPriorityReservation =
        selectedSlot?.id ===
            currentReservedSlotId
        &&
        currentReservationPickupType ===
            "PRIORITY";


    /*
     * Load the authoritative reservation so a returning
     * customer can see the exact pickup time they already
     * own instead of having to remember it.
     */
    useEffect(() => {

        if (
            !pendingOrderNumber
            ||
            !currentReservationLookupKey
        ) {
            return;
        }


        const reservedOrderNumber =
            pendingOrderNumber;


        const reservationLookupKey =
            currentReservationLookupKey;


        const controller =
            new AbortController();


        async function loadCurrentReservation() {

            try {

                const result =
                    await getCustomerOrder(
                        reservedOrderNumber,
                        controller.signal
                    );


                if (controller.signal.aborted) {
                    return;
                }


                const expiresAt =
                    parseBusinessTimestamp(
                        result.reservationExpiresAt
                    ).getTime();


                const reservationActive =
                    result.orderStatus ===
                        "PENDING_PAYMENT"
                    &&
                    result.paymentStatus ===
                        null
                    &&
                    Number.isFinite(
                        expiresAt
                    )
                    &&
                    expiresAt >
                        Date.now();


                if (!reservationActive) {

                    setLoadedCurrentReservation({
                        lookupKey:
                            reservationLookupKey,

                        order:
                            null
                    });

                    return;
                }


                setLoadedCurrentReservation({
                    lookupKey:
                        reservationLookupKey,

                    order:
                        result
                });


                setSelectedDate(
                    current =>
                        current
                        ?? result.pickupDate
                );

            } catch (exception) {

                if (controller.signal.aborted) {
                    return;
                }


                console.error(
                    "Unable to load the current pickup reservation:",
                    exception
                );


                setLoadedCurrentReservation({
                    lookupKey:
                        reservationLookupKey,

                    order:
                        null
                });
            }
        }


        void loadCurrentReservation();


        return () => {

            controller.abort();
        };

    }, [
        currentReservationLookupKey,
        pendingOrderNumber
    ]);


    /*
     * =========================================================
     * LOAD PICKUP SLOTS
     * =========================================================
     */
    useEffect(() => {

        if (
            !branch
            ||
            isEmpty
            ||
            branchId === null
            ||
            branch.id !== branchId
            ||
            !pickupDate
        ) {

            return;
        }


        const selectedBranch =
            branch;


        const controller =
            new AbortController();


        async function loadSlots() {

            try {

                const result =
                    await getPickupSlots(
                        selectedBranch.id,
                        pickupDate,
                        controller.signal
                    );


                if (
                    controller.signal.aborted
                ) {

                    return;
                }


                setSlots(
                    result
                );


                const reservedSlot =
                    currentReservationPickupDate
                    &&
                    pickupDate ===
                        currentReservationPickupDate
                    &&
                    currentReservedSlotId !==
                        null
                        ? result.find(
                            slot =>
                                slot.id ===
                                    currentReservedSlotId
                        )
                        ?? null
                        : null;


                setSelectedSlot(
                    reservedSlot
                );


                setSelectedPickupType(
                    reservedSlot
                        ? currentReservationPickupType
                        : null
                );


                setError(
                    null
                );


                setLoading(
                    false
                );

            } catch (exception) {

                if (
                    controller.signal.aborted
                ) {

                    return;
                }


                console.error(
                    "Unable to load pickup slots:",
                    exception
                );


                setSlots(
                    []
                );


                setSelectedSlot(
                    null
                );


                setSelectedPickupType(
                    null
                );


                setError(
                    exception instanceof Error
                        ? exception.message
                        : "Unable to load pickup slots."
                );


                setLoading(
                    false
                );
            }
        }


        void loadSlots();


        return () => {

            controller.abort();
        };

    }, [
        branch,
        branchId,
        currentReservationPickupDate,
        currentReservationPickupType,
        currentReservedSlotId,
        isEmpty,
        pickupDate,
        reloadKey
    ]);


    /*
     * =========================================================
     * DATE CHANGE
     * =========================================================
     */
    function handleDateChange(
        value: string
    ) {
        const nextDate =
            clampDateWithinBounds(
                value,
                today,
                maximumDate
            );

        if (cartSwitchPreview && !isEmpty && branch && nextDate && nextDate !== pickupDate) {
            setProposedDate(nextDate);
            return;
        }

        applyDateChange(nextDate);
    }

    function applyDateChange(nextDate: string) {

        setLoading(
            true
        );


        setError(
            null
        );


        setValidationError(
            null
        );


        setSlots(
            []
        );


        setSelectedSlot(
            null
        );


        setSelectedPickupType(
            null
        );


        setSelectedDate(
            nextDate
        );
    }


    /*
     * =========================================================
     * SLOT CHANGE
     * =========================================================
     */
    function handleSlotSelect(
        slot: PickupSlot
    ) {

        const keepsCurrentReservation =
            currentReservationPickupType !==
                null
            &&
            currentReservedSlotId ===
                slot.id;

        if (
            !keepsCurrentReservation
            &&
            !isSlotAvailable(
                slot
            )
        ) {

            return;
        }


        setSelectedSlot(
            slot
        );


        setSelectedPickupType(
            keepsCurrentReservation
                ? currentReservationPickupType
                : slot.remainingCapacity > 0
                    ? "NORMAL"
                    : "PRIORITY"
        );


        setValidationError(
            null
        );
    }



    /*
     * =========================================================
     * RETRY
     * =========================================================
     */
    function retrySlots() {

        setLoading(
            true
        );


        setError(
            null
        );


        setReloadKey(
            current =>
                current + 1
        );
    }


    /*
     * =========================================================
     * CONTINUE
     * =========================================================
     */
    function handleContinue() {

        if (
            !selectedSlot
            ||
            !pickupDate
            ||
            !selectedPickupType
        ) {

            setValidationError(
                "Select a pickup date and an available pickup time before continuing."
            );

            return;
        }


        savePickupSlot({
            date:
                pickupDate,

            slot:
                selectedSlot,

            pickupType:
                selectedPickupType
        });


        router.push(
            "/checkout/customer"
        );
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

                    <CheckoutStateCard
                        title="Your cart is empty"
                        message="Add items to your cart before choosing a pickup time."
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
                        message="The selected branch does not match the branch used by the items in your cart."
                        tone="warning"
                        detail="Choose the cart's branch again before selecting a pickup slot. Your cart items will stay available."
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
     * PAGE
     * =========================================================
     */
    return (
        <AppShell>

            {proposedDate && branch && <CartSwitchDialog branchId={branch.id} branchName={branch.name}
                date={proposedDate} items={items} onKeep={() => setProposedDate(null)}
                onSwitch={(preview: CartSwitchPreview) => {
                    if (preview.conflicts) return;
                    applyDateChange(proposedDate);
                    setProposedDate(null);
                }} />}

            <section
                className="
                    mx-auto
                    max-w-xl
                "
            >

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
                        Choose pickup
                    </h1>


                    <p
                        className="
                            mt-2
                            text-sm
                            text-[#756763]
                        "
                    >
                        Pick the date and time that works best for you.
                        Available capacity updates automatically.
                    </p>

                </div>


                {/* Branch */}

                <div
                    className="
                        mb-4
                        rounded-2xl
                        bg-[#fff4e5]
                        p-4
                    "
                >

                    <p
                        className="
                            text-[10px]
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#c88a20]
                        "
                    >
                        Pickup from
                    </p>


                    <p
                        className="
                            mt-1
                            font-bold
                            text-[#7a1625]
                        "
                    >
                        {branch.name}
                    </p>


                    {
                        branch.address
                        && (

                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                {branch.address}
                            </p>

                        )
                    }

                </div>


                {
                    currentReservation
                    && (

                        <div
                            className="
                                mb-4
                                rounded-2xl
                                border
                                border-green-200
                                bg-green-50
                                p-4
                            "
                        >

                            <p
                                className="
                                    text-[10px]
                                    font-bold
                                    uppercase
                                    tracking-wide
                                    text-green-700
                                "
                            >
                                Currently reserved for you
                            </p>


                            <p
                                className="
                                    mt-1
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {
                                    formatDisplayDate(
                                        currentReservation.pickupDate
                                    )
                                }

                                {" · "}

                                {
                                    formatTime(
                                        currentReservation.pickupStartTime
                                    )
                                }

                                {" – "}

                                {
                                    formatTime(
                                        currentReservation.pickupEndTime
                                    )
                                }
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-xs
                                    leading-5
                                    text-green-800
                                "
                            >
                                Keep this time or choose another available
                                time below. Your current reservation remains
                                safe until the change succeeds.
                            </p>

                        </div>

                    )
                }


                {/* Pickup controls */}

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

                    {/* Date */}

                    <div>

                        <label
                            htmlFor="pickup-date"
                            className="
                                block
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Pickup date
                        </label>


                        <input
                            id="pickup-date"
                            type="date"
                            value={
                                pickupDate
                            }
                            min={
                                today
                            }
                            max={
                                maximumDate
                            }
                            onChange={
                                event =>
                                    handleDateChange(
                                        event.target.value
                                    )
                            }
                            disabled={
                                !today
                            }
                            className="
                                mt-2
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                px-4
                                text-sm
                                font-semibold
                                text-[#241715]
                                outline-none

                                focus:border-[#7a1625]
                                focus:ring-2
                                focus:ring-[#7a1625]/10
                            "
                        />


                        {
                            pickupDate
                            && (

                                <p
                                    className="
                                        mt-2
                                        text-xs
                                        text-[#756763]
                                    "
                                >
                                    {
                                        formatDisplayDate(
                                            pickupDate
                                        )
                                    }
                                </p>

                            )
                        }

                    </div>


                    {/* Slot */}

                    <div
                        className="
                            mt-5
                        "
                    >

                        <label
                            htmlFor="pickup-slot"
                            className="
                                block
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Pickup time
                        </label>


                        <p
                            className="
                                mt-1
                                text-xs
                                leading-5
                                text-[#756763]
                            "
                        >
                            Open the list and choose the most convenient available time.
                        </p>


                        {
                            loading
                            && (

                                <div
                                    className="
                                        mt-2
                                    "
                                >
                                    <PickupSlotSkeleton />
                                </div>

                            )
                        }


                        {
                            !loading
                            &&
                            error
                            && (

                                <div
                                    className="
                                        mt-2
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
                                        Unable to load pickup times
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            text-red-600
                                        "
                                    >
                                        {error}
                                    </p>


                                    <button
                                        type="button"
                                        onClick={
                                            retrySlots
                                        }
                                        className="
                                            mt-3
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]
                                        "
                                    >
                                        Try again
                                    </button>

                                </div>

                            )
                        }


                        {
                            !loading
                            &&
                            !error
                            &&
                            slots.length === 0
                            && (

                                <div
                                    className="
                                        mt-2
                                        rounded-xl
                                        bg-[#fffaf3]
                                        p-4
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    No pickup times available
                                    for this date.
                                </div>

                            )
                        }


                        {
                            !loading
                            &&
                            !error
                            &&
                            slots.length > 0
                            && (

                                <PickupSlotDropdown
                                    slots={
                                        slots
                                    }
                                    selectedSlot={
                                        selectedSlot
                                    }
                                    reservedSlotId={
                                        currentReservedSlotId
                                    }
                                    onSelect={
                                        handleSlotSelect
                                    }
                                />

                            )
                        }

                    </div>


                    {/* Selected slot info */}

                    {
                        selectedSlot
                        && (

                            <div
                                className="
                                    mt-5
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-[#fff8ef]
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
                                                font-semibold
                                                uppercase
                                                tracking-wide
                                                text-[#c88a20]
                                            "
                                        >
                                            Selected time
                                        </p>


                                        <p
                                            className="
                                                mt-1
                                                font-bold
                                                text-[#241715]
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

                                    </div>


                                    <div
                                        className="
                                            text-right
                                        "
                                    >

                                        <p
                                            className="
                                                text-xs
                                                font-semibold
                                                text-green-700
                                            "
                                        >
                                            {
                                                selectedSlot.id ===
                                                    currentReservedSlotId
                                                    ? "Reserved for you"
                                                    : `${selectedSlot.remainingCapacity} normal left`
                                            }
                                        </p>


                                        {
                                            selectedSlot.priorityEnabled
                                            &&
                                            selectedSlot
                                                .priorityRemainingCapacity
                                                > 0
                                            && (

                                                <p
                                                    className="
                                                        mt-1
                                                        text-xs
                                                        text-[#7a1625]
                                                    "
                                                >
                                                    {
                                                        selectedSlot
                                                            .priorityRemainingCapacity
                                                    }
                                                    {" "}
                                                    priority left
                                                </p>

                                            )
                                        }

                                    </div>

                                </div>


                                {
                                    selectedSlot.priorityEnabled
                                    &&
                                    selectedSlot
                                        .priorityRemainingCapacity
                                        > 0
                                    && (

                                        <div
                                            className="
                                                mt-3
                                                border-t
                                                border-[#eadfd6]
                                                pt-3
                                                text-xs
                                                text-[#756763]
                                            "
                                        >
                                            Priority pickup available for{" "}
                                            <strong
                                                className="
                                                    text-[#7a1625]
                                                "
                                            >
                                                {
                                                    formatCurrency(
                                                        selectedSlot
                                                            .priorityCharge
                                                    )
                                                }
                                            </strong>
                                            {" "}
                                            extra.
                                        </div>

                                    )
                                }


                                <div
                                    className="
                                        mt-4
                                        border-t
                                        border-[#eadfd6]
                                        pt-4
                                    "
                                >

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-wide
                                            text-[#756763]
                                        "
                                    >
                                        Pickup type
                                    </p>


                                    <div
                                        className="
                                            mt-3
                                            grid
                                            gap-3
                                            sm:grid-cols-2
                                        "
                                    >

                                        <button
                                            type="button"
                                            disabled={
                                                selectedSlot
                                                    .remainingCapacity
                                                    <= 0
                                                &&
                                                !ownsSelectedNormalReservation
                                            }
                                            onClick={
                                                () => {

                                                    setSelectedPickupType(
                                                        "NORMAL"
                                                    );

                                                    setValidationError(
                                                        null
                                                    );
                                                }
                                            }
                                            className={`
                                                rounded-2xl
                                                border
                                                p-4
                                                text-left
                                                transition

                                                ${
                                                    selectedPickupType ===
                                                    "NORMAL"
                                                        ? "border-[#7a1625] bg-[#fff4e5] ring-2 ring-[#7a1625]/10"
                                                        : selectedSlot.remainingCapacity <= 0
                                                        &&
                                                        !ownsSelectedNormalReservation
                                                            ? "cursor-not-allowed border-[#eadfd6] bg-[#f7f3ef] opacity-60"
                                                            : "border-[#eadfd6] bg-white hover:border-[#c88a20]"
                                                }
                                            `}
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
                                                            text-sm
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        Normal Pickup
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            leading-5
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Included in your order.
                                                    </p>

                                                </div>


                                                {
                                                    selectedSlot.remainingCapacity <= 0
                                                    &&
                                                    !ownsSelectedNormalReservation
                                                        ? (
                                                            <span
                                                                className="
                                                                    shrink-0
                                                                    rounded-full
                                                                    bg-[#eee6e1]
                                                                    px-2.5
                                                                    py-1
                                                                    text-[10px]
                                                                    font-bold
                                                                    uppercase
                                                                    tracking-wide
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Full
                                                            </span>
                                                        )
                                                        : selectedPickupType === "NORMAL"
                                                            ? (
                                                                <span
                                                                    className="
                                                                        flex
                                                                        h-6
                                                                        w-6
                                                                        shrink-0
                                                                        items-center
                                                                        justify-center
                                                                        rounded-full
                                                                        bg-[#7a1625]
                                                                        text-xs
                                                                        font-bold
                                                                        text-white!
                                                                    "
                                                                >
                                                                    ✓
                                                                </span>
                                                            )
                                                            : (
                                                                <span
                                                                    className="
                                                                        h-6
                                                                        w-6
                                                                        shrink-0
                                                                        rounded-full
                                                                        border-2
                                                                        border-[#d7c7bd]
                                                                        bg-white
                                                                    "
                                                                />
                                                            )
                                                }

                                            </div>


                                            <p
                                                className="
                                                    mt-3
                                                    text-[11px]
                                                    font-semibold
                                                    text-[#4b7a44]
                                                "
                                            >
                                                {
                                                    ownsSelectedNormalReservation
                                                        ? "Reserved for you"
                                                        : selectedSlot.remainingCapacity > 0
                                                        ? `${selectedSlot.remainingCapacity} available`
                                                        : "No normal slots left"
                                                }
                                            </p>

                                        </button>


                                        <button
                                            type="button"
                                            disabled={
                                                !selectedSlot
                                                    .priorityEnabled
                                                ||
                                                selectedSlot
                                                    .priorityRemainingCapacity
                                                    <= 0
                                                &&
                                                !ownsSelectedPriorityReservation
                                            }
                                            onClick={
                                                () => {

                                                    setSelectedPickupType(
                                                        "PRIORITY"
                                                    );

                                                    setValidationError(
                                                        null
                                                    );
                                                }
                                            }
                                            className={`
                                                rounded-2xl
                                                border
                                                p-4
                                                text-left
                                                transition

                                                ${
                                                    selectedPickupType ===
                                                    "PRIORITY"
                                                        ? "border-[#7a1625] bg-[#fff4e5] ring-2 ring-[#7a1625]/10"
                                                        : (
                                                            !selectedSlot.priorityEnabled
                                                            ||
                                                            selectedSlot.priorityRemainingCapacity <= 0
                                                            &&
                                                            !ownsSelectedPriorityReservation
                                                        )
                                                            ? "cursor-not-allowed border-[#eadfd6] bg-[#f7f3ef] opacity-60"
                                                            : "border-[#eadfd6] bg-white hover:border-[#c88a20]"
                                                }
                                            `}
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

                                                    <div
                                                        className="
                                                            flex
                                                            flex-wrap
                                                            items-center
                                                            gap-2
                                                        "
                                                    >

                                                        <p
                                                            className="
                                                                text-sm
                                                                font-bold
                                                                text-[#241715]
                                                            "
                                                        >
                                                            Priority Pickup
                                                        </p>


                                                        {
                                                            selectedSlot.priorityEnabled
                                                            && (
                                                                <span
                                                                    className="
                                                                        text-xs
                                                                        font-bold
                                                                        text-[#7a1625]
                                                                    "
                                                                >
                                                                    +{
                                                                        formatCurrency(
                                                                            selectedSlot
                                                                                .priorityCharge
                                                                        )
                                                                    }
                                                                </span>
                                                            )
                                                        }

                                                    </div>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            leading-5
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Uses priority pickup capacity.
                                                    </p>

                                                </div>


                                                {
                                                    (
                                                        !selectedSlot.priorityEnabled
                                                        ||
                                                        selectedSlot.priorityRemainingCapacity <= 0
                                                        &&
                                                        !ownsSelectedPriorityReservation
                                                    )
                                                        ? (
                                                            <span
                                                                className="
                                                                    shrink-0
                                                                    rounded-full
                                                                    bg-[#eee6e1]
                                                                    px-2.5
                                                                    py-1
                                                                    text-[10px]
                                                                    font-bold
                                                                    uppercase
                                                                    tracking-wide
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Full
                                                            </span>
                                                        )
                                                        : selectedPickupType === "PRIORITY"
                                                            ? (
                                                                <span
                                                                    className="
                                                                        flex
                                                                        h-6
                                                                        w-6
                                                                        shrink-0
                                                                        items-center
                                                                        justify-center
                                                                        rounded-full
                                                                        bg-[#7a1625]
                                                                        text-xs
                                                                        font-bold
                                                                        text-white!
                                                                    "
                                                                >
                                                                    ✓
                                                                </span>
                                                            )
                                                            : (
                                                                <span
                                                                    className="
                                                                        h-6
                                                                        w-6
                                                                        shrink-0
                                                                        rounded-full
                                                                        border-2
                                                                        border-[#d7c7bd]
                                                                        bg-white
                                                                    "
                                                                />
                                                            )
                                                }

                                            </div>


                                            <p
                                                className="
                                                    mt-3
                                                    text-[11px]
                                                    font-semibold
                                                    text-[#7a1625]
                                                "
                                            >
                                                {
                                                    ownsSelectedPriorityReservation
                                                        ? "Reserved for you"
                                                        : selectedSlot.priorityEnabled
                                                    &&
                                                    selectedSlot.priorityRemainingCapacity > 0
                                                        ? `${selectedSlot.priorityRemainingCapacity} available`
                                                        : "No priority slots left"
                                                }
                                            </p>

                                        </button>

                                    </div>

                                </div>

                            </div>

                        )
                    }

                </div>


                {/* Order summary */}

                <div
                    className="
                        mt-5
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
                            text-sm
                        "
                    >

                        <span
                            className="
                                text-[#756763]
                            "
                        >
                            {itemCount}{" "}
                            {
                                itemCount === 1
                                    ? "item"
                                    : "items"
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


                    {
                        validationError
                        && (

                            <div
                                role="alert"
                                className="
                                    mt-5
                                    rounded-xl
                                    border
                                    border-amber-200
                                    bg-amber-50
                                    p-4
                                    text-sm
                                    font-semibold
                                    text-amber-800
                                "
                            >
                                {
                                    validationError
                                }
                            </div>

                        )
                    }


                    <button
                        type="button"
                        disabled={
                            !selectedSlot
                            ||
                            !selectedPickupType
                        }
                        onClick={
                            handleContinue
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
                            font-bold
                            text-white!
                            transition

                            active:scale-[0.98]

                            disabled:cursor-not-allowed
                            disabled:bg-[#c9b9b4]
                        "
                    >
                        {
                            selectedSlot
                            && selectedPickupType
                                ? "Continue"
                                : selectedSlot
                                    ? "Select Pickup Type"
                                    : "Select a Pickup Time"
                        }
                    </button>


                    <Link
                        href="/cart"
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
                        Back to Cart
                    </Link>

                </div>

            </section>

        </AppShell>
    );
}
