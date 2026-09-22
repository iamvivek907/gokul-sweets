"use client";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import {
    useParams,
    useRouter
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminKotByOrderNumber,
    recordAdminKotPrint
} from "@/services/adminKotApi";

import type {
    AdminKot
} from "@/types/adminKot";


function formatPickupType(
    value: AdminKot["pickupType"]
) {

    switch (value) {

        case "NORMAL":
            return "NORMAL";

        case "PRIORITY":
            return "PRIORITY";

        case "ADMIN_OVERRIDE":
            return "ADMIN OVERRIDE";

        default:
            return value;
    }
}


function formatPickupDate(
    value: string
) {

    const parts =
        value.split("-");


    if (
        parts.length
        !== 3
    ) {

        return value;
    }


    const [
        year,
        month,
        day
    ] =
        parts;


    const date =
        new Date(
            Number(year),
            Number(month) - 1,
            Number(day)
        );


    if (
        Number.isNaN(
            date.getTime()
        )
    ) {

        return value;
    }


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            day: "2-digit",
            month: "short",
            year: "numeric"
        }
    ).format(
        date
    );
}


function formatTime(
    value: string
) {

    const parts =
        value.split(":");


    if (
        parts.length
        < 2
    ) {

        return value;
    }


    const hours =
        Number(
            parts[0]
        );


    const minutes =
        Number(
            parts[1]
        );


    if (
        Number.isNaN(hours)
        ||
        Number.isNaN(minutes)
    ) {

        return value;
    }


    const date =
        new Date();


    date.setHours(
        hours,
        minutes,
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


function formatDateTime(
    value: string
) {

    const date =
        new Date(
            value
        );


    if (
        Number.isNaN(
            date.getTime()
        )
    ) {

        return value;
    }


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            day: "2-digit",
            month: "short",
            year: "numeric",
            hour: "numeric",
            minute: "2-digit"
        }
    ).format(
        date
    );
}


export default function AdminKotPrintPage() {

    const params =
        useParams<{
            orderNumber: string;
        }>();


    const router =
        useRouter();


    const {
        authorization,
        ready,
        isAuthenticated,
        hasPermission
    } =
        useAdminAuth();


    const [
        kot,
        setKot
    ] =
        useState<AdminKot | null>(
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
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    const [
        printing,
        setPrinting
    ] =
        useState(
            false
        );


    const [
        printError,
        setPrintError
    ] =
        useState<string | null>(
            null
        );


    const orderNumber =
        useMemo(
            () =>
                decodeURIComponent(
                    params.orderNumber
                ),
            [
                params.orderNumber
            ]
        );


    const canView =
        hasPermission(
            "ORDER_VIEW"
        );


    useEffect(
        () => {

            if (
                !ready
                ||
                !isAuthenticated
                ||
                authorization === null
                ||
                !canView
            ) {

                return;
            }


            const adminAuthorization =
                authorization;


            const controller =
                new AbortController();


            async function loadKot() {

                setLoading(
                    true
                );


                setError(
                    null
                );


                try {

                    const result =
                        await getAdminKotByOrderNumber(
                            orderNumber,
                            adminAuthorization,
                            controller.signal
                        );


                    setKot(
                        result
                    );

                } catch (exception) {

                    if (
                        exception
                        instanceof DOMException
                        &&
                        exception.name
                        === "AbortError"
                    ) {

                        return;
                    }


                    setError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to load the KOT."
                    );

                } finally {

                    if (
                        !controller.signal.aborted
                    ) {

                        setLoading(
                            false
                        );
                    }
                }
            }


            void loadKot();


            return () => {

                controller.abort();
            };

        },
        [
            ready,
            isAuthenticated,
            authorization,
            canView,
            orderNumber
        ]
    );


    async function handlePrint() {

        if (
            kot === null
            ||
            authorization === null
            ||
            printing
        ) {

            return;
        }


        const currentKot =
            kot;


        const adminAuthorization =
            authorization;


        /*
         * Capture whether THIS print operation is a reprint
         * before incrementing the backend print count.
         *
         * Example:
         *
         * printCount 0 -> first print
         * printCount 1 -> reprint
         */
        const currentPrintIsReprint =
            currentKot.printCount > 0;


        setPrinting(
            true
        );


        setPrintError(
            null
        );


        try {

            /*
             * Record that this staff member initiated a print.
             *
             * Stage-1 browser printing cannot guarantee that
             * the physical printer successfully produced paper.
             * The audit therefore represents print initiation.
             */
            const updatedKot =
                await recordAdminKotPrint(
                    currentKot.kotNumber,
                    adminAuthorization
                );


            /*
             * IMPORTANT:
             *
             * Do not update KOT state before window.print().
             *
             * On the very first print the backend response has
             * printCount = 1. Updating state before printing
             * would incorrectly make the first physical ticket
             * display REPRINT.
             *
             * window.print() is called while the page still
             * represents the state that existed immediately
             * before this print attempt.
             */
            window.print();


            /*
             * Once the browser print workflow closes, update
             * local state with authoritative backend audit data.
             */
            setKot(
                updatedKot
            );


            /*
             * currentPrintIsReprint is intentionally evaluated
             * before recording the print. Keeping this reference
             * documents the distinction between first print and
             * subsequent reprints.
             */
            if (currentPrintIsReprint) {

                console.debug(
                    "KOT reprint initiated:",
                    currentKot.kotNumber
                );

            } else {

                console.debug(
                    "KOT first print initiated:",
                    currentKot.kotNumber
                );
            }

        } catch (exception) {

            setPrintError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to start KOT printing."
            );

        } finally {

            setPrinting(
                false
            );
        }
    }


    const totalQuantity =
        kot
            ? kot.items.reduce(
                (
                    total,
                    item
                ) =>
                    total
                    +
                    item.quantity,
                0
            )
            : 0;


    const waitingForKot =
        ready
        &&
        isAuthenticated
        &&
        canView
        &&
        authorization !== null
        &&
        kot === null
        &&
        error === null;


    if (!ready) {

        return null;
    }


    if (!isAuthenticated) {

        return (
            <PrintPageMessage
                title="Admin login required"
                message="Please sign in to the admin portal before viewing this KOT."
                onBack={
                    () =>
                        router.push(
                            "/admin/login"
                        )
                }
                backLabel="Go to Admin Login"
            />
        );
    }


    if (!canView) {

        return (
            <PrintPageMessage
                title="Access denied"
                message="You do not have permission to view KOTs."
                onBack={
                    () =>
                        router.push(
                            "/admin/orders"
                        )
                }
                backLabel="Back to Live Orders"
            />
        );
    }


    if (
        loading
        ||
        waitingForKot
    ) {

        return (
            <PrintPageMessage
                title="Loading KOT"
                message={`Loading kitchen ticket for ${orderNumber}...`}
                onBack={
                    () =>
                        router.push(
                            "/admin/orders"
                        )
                }
                backLabel="Back to Live Orders"
            />
        );
    }


    if (
        error
        ||
        kot === null
    ) {

        return (
            <PrintPageMessage
                title="Unable to load KOT"
                message={
                    error
                    ??
                    "KOT data is unavailable."
                }
                onBack={
                    () =>
                        router.push(
                            "/admin/orders"
                        )
                }
                backLabel="Back to Live Orders"
            />
        );
    }


    return (
        <>
            <style>
                {`
                    @media print {

                        @page {
                            margin: 0;
                        }

                        html,
                        body {
                            margin: 0 !important;
                            padding: 0 !important;
                            background: white !important;
                        }

                        body * {
                            visibility: hidden !important;
                        }

                        #kot-print-root,
                        #kot-print-root * {
                            visibility: visible !important;
                        }

                        #kot-print-root {
                            position: absolute !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 72mm !important;
                            max-width: 72mm !important;
                            margin: 0 !important;
                            padding: 3mm !important;
                            box-shadow: none !important;
                            border: 0 !important;
                            background: white !important;
                            color: black !important;
                        }

                        .kot-no-print {
                            display: none !important;
                        }
                    }
                `}
            </style>


            <div
                className="
                    min-h-screen
                    bg-[#fffaf3]
                    px-4
                    py-6

                    sm:px-6
                    sm:py-8
                "
            >

                <div
                    className="
                        kot-no-print

                        mx-auto
                        mb-5
                        max-w-xl
                    "
                >

                    <div
                        className="
                            flex
                            flex-col
                            gap-3

                            sm:flex-row
                            sm:items-center
                            sm:justify-between
                        "
                    >

                        <button
                            type="button"
                            disabled={
                                printing
                            }
                            onClick={
                                () =>
                                    router.push(
                                        "/admin/orders"
                                    )
                            }
                            className="
                                min-h-11
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-5
                                text-sm
                                font-semibold
                                text-[#7a1625]

                                hover:bg-[#fff1e9]

                                disabled:cursor-not-allowed
                                disabled:opacity-50
                            "
                        >
                            ← Back to Live Orders
                        </button>


                        <button
                            type="button"
                            disabled={
                                printing
                            }
                            onClick={
                                () => {

                                    void handlePrint();
                                }
                            }
                            className="
                                min-h-11
                                rounded-xl
                                bg-[#7a1625]
                                px-6
                                text-sm
                                font-semibold
                                text-white

                                hover:bg-[#5d0f1b]

                                disabled:cursor-not-allowed
                                disabled:opacity-60
                            "
                        >
                            {
                                printing
                                    ? "Preparing Print..."
                                    : kot.printCount > 0
                                        ? "Reprint KOT"
                                        : "Print KOT"
                            }
                        </button>

                    </div>


                    {
                        printError
                        && (

                            <div
                                role="alert"
                                className="
                                    mt-4
                                    rounded-xl
                                    border
                                    border-red-200
                                    bg-red-50
                                    px-4
                                    py-3
                                    text-sm
                                    font-medium
                                    text-red-700
                                "
                            >
                                {
                                    printError
                                }
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
                            px-4
                            py-3
                            text-xs
                            leading-5
                            text-[#756763]
                        "
                    >

                        <p>
                            Print count:{" "}

                            <strong
                                className="
                                    text-[#241715]
                                "
                            >
                                {
                                    kot.printCount
                                }
                            </strong>
                        </p>


                        {
                            kot.firstPrintedAt
                            && (

                                <p
                                    className="
                                        mt-1
                                    "
                                >
                                    First print initiated:{" "}

                                    <strong
                                        className="
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatDateTime(
                                                kot.firstPrintedAt
                                            )
                                        }
                                    </strong>
                                </p>

                            )
                        }


                        {
                            kot.lastPrintedAt
                            && (

                                <p
                                    className="
                                        mt-1
                                    "
                                >
                                    Last print initiated:{" "}

                                    <strong
                                        className="
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatDateTime(
                                                kot.lastPrintedAt
                                            )
                                        }
                                    </strong>
                                </p>

                            )
                        }

                    </div>

                </div>


                <main
                    id="kot-print-root"
                    className="
                        mx-auto
                        w-full
                        max-w-[80mm]
                        bg-white
                        p-[4mm]
                        text-black
                        shadow-lg
                    "
                >

                    <header
                        className="
                            text-center
                        "
                    >

                        <h1
                            className="
                                text-[20px]
                                font-black
                                uppercase
                                leading-tight
                            "
                        >
                            Gokul Sweets
                        </h1>


                        <p
                            className="
                                mt-1
                                text-[18px]
                                font-black
                                uppercase
                                tracking-[0.2em]
                            "
                        >
                            KOT
                        </p>


                        <p
                            className="
                                mt-1
                                text-[11px]
                                font-semibold
                            "
                        >
                            {
                                kot.branchName
                            }
                        </p>


                        {
                            kot.branchAddress
                            && (

                                <p
                                    className="
                                        mt-0.5
                                        text-[10px]
                                        leading-tight
                                    "
                                >
                                    {
                                        kot.branchAddress
                                    }
                                </p>

                            )
                        }

                    </header>


                    <TicketDivider />


                    <div
                        className="
                            space-y-1
                            text-[12px]
                            leading-tight
                        "
                    >

                        <TicketRow
                            label="KOT"
                            value={
                                kot.kotNumber
                            }
                            strong
                        />


                        <TicketRow
                            label="Order"
                            value={
                                kot.orderNumber
                            }
                            strong
                        />


                        <TicketRow
                            label="Pickup"
                            value={
                                formatPickupDate(
                                    kot.pickupDate
                                )
                            }
                        />


                        <TicketRow
                            label="Time"
                            value={
                                `${formatTime(
                                    kot.pickupStartTime
                                )} - ${formatTime(
                                    kot.pickupEndTime
                                )}`
                            }
                        />

                    </div>


                    {
                        kot.pickupType
                        !== "NORMAL"
                        && (

                            <>
                                <TicketDivider />


                                <div
                                    className="
                                        border-2
                                        border-black
                                        px-2
                                        py-2
                                        text-center
                                        text-[17px]
                                        font-black
                                        uppercase
                                    "
                                >
                                    ***
                                    {" "}
                                    {
                                        formatPickupType(
                                            kot.pickupType
                                        )
                                    }
                                    {" "}
                                    ***
                                </div>
                            </>

                        )
                    }


                    <TicketDivider />


                    <div
                        className="
                            grid
                            grid-cols-[10mm_1fr]
                            gap-2
                            text-[11px]
                            font-black
                            uppercase
                        "
                    >

                        <span>
                            Qty
                        </span>


                        <span>
                            Item
                        </span>

                    </div>


                    <div
                        className="
                            my-1
                            border-t
                            border-dashed
                            border-black
                        "
                    />


                    <div
                        className="
                            space-y-2
                        "
                    >

                        {
                            kot.items.map(
                                item => (

                                    <div
                                        key={
                                            item.id
                                        }
                                        className="
                                            grid
                                            grid-cols-[10mm_1fr]
                                            gap-2
                                            text-[14px]
                                            font-bold
                                            leading-tight
                                        "
                                    >

                                        <span
                                            className="
                                                text-center
                                                text-[16px]
                                                font-black
                                            "
                                        >
                                            {
                                                item.quantity
                                            }
                                        </span>


                                        <span
                                            className="
                                                wrap-break-word
                                            "
                                        >
                                            {
                                                item.productName
                                            }
                                        </span>

                                    </div>

                                )
                            )
                        }

                    </div>


                    <TicketDivider />


                    <div
                        className="
                            flex
                            items-center
                            justify-between
                            text-[13px]
                            font-black
                        "
                    >

                        <span>
                            TOTAL QTY
                        </span>


                        <span>
                            {
                                totalQuantity
                            }
                        </span>

                    </div>


                    <TicketDivider />


                    <div
                        className="
                            space-y-1
                            text-[10px]
                            leading-tight
                        "
                    >

                        <TicketRow
                            label="Started by"
                            value={
                                kot.startedByStaffName
                            }
                        />


                        <TicketRow
                            label="Started"
                            value={
                                formatDateTime(
                                    kot.createdAt
                                )
                            }
                        />

                    </div>


                    {
                        kot.printCount
                        > 0
                        && (

                            <>
                                <TicketDivider />


                                <p
                                    className="
                                        text-center
                                        text-[13px]
                                        font-black
                                        uppercase
                                    "
                                >
                                    *** REPRINT ***
                                </p>
                            </>

                        )
                    }


                    <div
                        className="
                            mt-5
                            border-t
                            border-dashed
                            border-black
                            pt-2
                            text-center
                            text-[9px]
                            font-semibold
                        "
                    >
                        Kitchen Order Ticket
                    </div>

                </main>

            </div>
        </>
    );
}


function TicketDivider() {

    return (
        <div
            className="
                my-3
                border-t-2
                border-dashed
                border-black
            "
        />
    );
}


function TicketRow({
    label,
    value,
    strong = false
}: {
    label: string;
    value: string;
    strong?: boolean;
}) {

    return (
        <div
            className="
                grid
                grid-cols-[21mm_1fr]
                gap-2
            "
        >

            <span
                className="
                    font-semibold
                "
            >
                {label}:
            </span>


            <span
                className={
                    strong
                        ? "wrap-break-word font-black"
                        : "wrap-break-word font-semibold"
                }
            >
                {
                    value
                }
            </span>

        </div>
    );
}


function PrintPageMessage({
    title,
    message,
    onBack,
    backLabel
}: {
    title: string;
    message: string;
    onBack: () => void;
    backLabel: string;
}) {

    return (
        <div
            className="
                flex
                min-h-[70vh]
                items-center
                justify-center
                px-4
                py-10
            "
        >

            <div
                className="
                    w-full
                    max-w-lg
                    rounded-2xl
                    border
                    border-[#eadfd6]
                    bg-white
                    p-6
                "
            >

                <h1
                    className="
                        text-xl
                        font-bold
                        text-[#241715]
                    "
                >
                    {
                        title
                    }
                </h1>


                <p
                    className="
                        mt-3
                        text-sm
                        leading-6
                        text-[#756763]
                    "
                >
                    {
                        message
                    }
                </p>


                <button
                    type="button"
                    onClick={
                        onBack
                    }
                    className="
                        mt-5
                        min-h-11
                        rounded-xl
                        bg-[#7a1625]
                        px-5
                        text-sm
                        font-semibold
                        text-white

                        hover:bg-[#5d0f1b]
                    "
                >
                    {
                        backLabel
                    }
                </button>

            </div>

        </div>
    );
}