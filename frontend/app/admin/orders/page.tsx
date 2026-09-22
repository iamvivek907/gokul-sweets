"use client";

import {
    useCallback,
    useEffect,
    useMemo,
    useState
} from "react";

import {
    createPortal
} from "react-dom";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminOrderDetail,
    getAdminOrders,
    getAdminPreparationQueue,
    getAdminPreparationQueueCounts,
    getNextOrderStatus,
    getRequiredPermissionForTransition,
    startNextAdminOrders,
    startSelectedAdminOrders,
    updateAdminOrderStatus
} from "@/services/adminOrdersApi";

import type {
    AdminBatchPreparationResponse,
    AdminOrderDetail,
    AdminOrderPageResponse,
    AdminOrderQueueCounts,
    AdminOrderQueueItem,
    AdminOrderQueueResponse,
    AdminOrderSummary,
    OrderStatus
} from "@/types/adminOrders";

import {
    queueAdminKotReprintForOrder
} from "@/services/adminKotApi";

import {
    useRouter
} from "next/navigation";


interface Branch {
    id: number;
    code: string;
    name: string;
    address: string;
    city: string;
    state: string;
    pincode: string;
    phone: string;
    latitude: number | null;
    longitude: number | null;
    openingTime: string;
    closingTime: string;
    active: boolean;
}


type OrderFilter =
    | "ALL"
    | OrderStatus;


const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


const PAGE_SIZE =
    20;


const AUTO_REFRESH_MS =
    30_000;


const ORDER_DETAIL_AUTO_REFRESH_MS =
    15_000;


const STATUS_TABS: {
    value: OrderFilter;
    label: string;
}[] = [
    {
        value: "ALL",
        label: "All"
    },
    {
        value: "CONFIRMED",
        label: "Confirmed"
    },
    {
        value: "PREPARING",
        label: "Preparing"
    },
    {
        value: "READY_FOR_PICKUP",
        label: "Ready"
    },
    {
        value: "PICKED_UP",
        label: "Picked Up"
    },
    {
        value: "PENDING_PAYMENT",
        label: "Pending Payment"
    },
    {
        value: "PAYMENT_FAILED",
        label: "Payment Failed"
    },
    {
        value: "CANCELLED",
        label: "Cancelled"
    },
    {
        value: "NO_SHOW",
        label: "No Show"
    },
    {
        value: "PICKUP_WINDOW_EXPIRED",
        label: "Expired"
    }
];


function formatPrice(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 2
        }
    ).format(
        value
    );
}


function formatPickupType(
    pickupType: string
) {

    switch (
        pickupType
    ) {

        case "NORMAL":
            return "Normal";

        case "PRIORITY":
            return "Priority";

        case "ADMIN_OVERRIDE":
            return "Admin Override";

        default:
            return pickupType;
    }
}


function formatStatus(
    status: OrderStatus
) {

    switch (
        status
    ) {

        case "PENDING_PAYMENT":
            return "Pending Payment";

        case "CONFIRMED":
            return "Confirmed";

        case "PREPARING":
            return "Preparing";

        case "READY_FOR_PICKUP":
            return "Ready for Pickup";

        case "PICKED_UP":
            return "Picked Up";

        case "PAYMENT_FAILED":
            return "Payment Failed";

        case "CANCELLED":
            return "Cancelled";

        case "NO_SHOW":
            return "No Show";

        case "PICKUP_WINDOW_EXPIRED":
            return "Pickup Window Expired";

        default:
            return status;
    }
}


function formatPaymentStatus(
    status: string
) {

    return status
        .split("_")
        .map(
            part =>
                part.charAt(0)
                +
                part
                    .slice(1)
                    .toLowerCase()
        )
        .join(" ");
}


function formatCreatedAt(
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
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(
        date
    );
}


function formatLastUpdated(
    value: Date | null
) {

    if (!value) {
        return "Not refreshed yet";
    }


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            hour: "numeric",
            minute: "2-digit",
            second: "2-digit"
        }
    ).format(
        value
    );
}


function statusClasses(
    status: OrderStatus
) {

    switch (
        status
    ) {

        case "CONFIRMED":
            return "bg-blue-50 text-blue-800 border-blue-200";

        case "PREPARING":
            return "bg-amber-50 text-amber-800 border-amber-200";

        case "READY_FOR_PICKUP":
            return "bg-green-50 text-green-800 border-green-200";

        case "PICKED_UP":
            return "bg-emerald-50 text-emerald-800 border-emerald-200";

        case "PENDING_PAYMENT":
            return "bg-yellow-50 text-yellow-800 border-yellow-200";

        case "PAYMENT_FAILED":
        case "CANCELLED":
            return "bg-red-50 text-red-700 border-red-200";

        case "NO_SHOW":
        case "PICKUP_WINDOW_EXPIRED":
            return "bg-gray-100 text-gray-700 border-gray-200";

        default:
            return "bg-gray-100 text-gray-700 border-gray-200";
    }
}


function paymentStatusClasses(
    status: string | null
) {

    switch (
        status
    ) {

        case "PAID":
            return "bg-green-50 text-green-800";

        case "PENDING":
            return "bg-yellow-50 text-yellow-800";

        case "FAILED":
        case "EXPIRED":
        case "REFUND_FAILED":
            return "bg-red-50 text-red-700";

        case "REFUND_PENDING":
            return "bg-amber-50 text-amber-800";

        case "REFUNDED":
            return "bg-purple-50 text-purple-800";

        default:
            return "bg-gray-100 text-gray-700";
    }
}


function formatMinutesUntilPickup(
    minutesUntilPickup: number
) {

    if (minutesUntilPickup < 0) {

        const overdueMinutes =
            Math.abs(
                minutesUntilPickup
            );


        return overdueMinutes
            === 1
                ? "1 minute overdue"
                : `${overdueMinutes} minutes overdue`;
    }


    if (minutesUntilPickup === 0) {
        return "Pickup time now";
    }


    if (minutesUntilPickup < 60) {
        return `${minutesUntilPickup} min until pickup`;
    }


    const hours =
        Math.floor(
            minutesUntilPickup / 60
        );


    const minutes =
        minutesUntilPickup % 60;


    if (minutes === 0) {
        return `${hours} hr until pickup`;
    }


    return `${hours} hr ${minutes} min until pickup`;
}


function preparationStatusClasses(
    status: AdminOrderQueueItem["preparationStatus"]
) {

    switch (status) {

        case "OVERDUE":
            return "border-red-200 bg-red-50 text-red-700";

        case "ELIGIBLE":
            return "border-amber-200 bg-amber-50 text-amber-800";

        default:
            return "border-[#eadfd6] bg-[#fffaf3] text-[#756763]";
    }
}


function getBatchSummaryMessage(
    result: AdminBatchPreparationResponse
) {

    if (
        result.started === 0
    ) {

        return result.attempted === 0
            ? "No eligible orders were available to start."
            : `No orders were started. ${result.skipped} skipped.`;
    }


    if (result.skipped === 0) {

        return result.started === 1
            ? "1 order moved to preparation and its KOT was created."
            : `${result.started} orders moved to preparation and their KOTs were created.`;
    }


    return `${result.started} started, ${result.skipped} skipped.`;
}


function getTransitionLabel(
    currentStatus: OrderStatus
) {

    switch (
        currentStatus
    ) {

        case "CONFIRMED":
            return "Start Preparation";

        case "PREPARING":
            return "Mark Ready for Pickup";

        case "READY_FOR_PICKUP":
            return "Mark Picked Up";

        default:
            return null;
    }
}


export default function AdminOrdersPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const [
        branches,
        setBranches
    ] =
        useState<Branch[]>(
            []
        );


    const [
        selectedBranchId,
        setSelectedBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        statusFilter,
        setStatusFilter
    ] =
        useState<OrderFilter>(
            "ALL"
        );


    const [
        search,
        setSearch
    ] =
        useState(
            ""
        );


    const [
        page,
        setPage
    ] =
        useState(
            0
        );


    const [
        orderPage,
        setOrderPage
    ] =
        useState<AdminOrderPageResponse | null>(
            null
        );


    const [
        branchesLoading,
        setBranchesLoading
    ] =
        useState(
            true
        );


    const [
        ordersLoading,
        setOrdersLoading
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
        lastUpdated,
        setLastUpdated
    ] =
        useState<Date | null>(
            null
        );


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    const [
        selectedOrderNumber,
        setSelectedOrderNumber
    ] =
        useState<string | null>(
            null
        );


    const [
        selectedOrder,
        setSelectedOrder
    ] =
        useState<AdminOrderDetail | null>(
            null
        );


    const [
        orderDetailLoading,
        setOrderDetailLoading
    ] =
        useState(
            false
        );


    const [
        orderDetailRefreshing,
        setOrderDetailRefreshing
    ] =
        useState(
            false
        );


    const [
        orderDetailLastUpdated,
        setOrderDetailLastUpdated
    ] =
        useState<Date | null>(
            null
        );


    const [
        orderDetailError,
        setOrderDetailError
    ] =
        useState<string | null>(
            null
        );


    const [
        orderActionLoading,
        setOrderActionLoading
    ] =
        useState(
            false
        );


    const [
        orderActionError,
        setOrderActionError
    ] =
        useState<string | null>(
            null
        );


    const [
        orderActionSuccess,
        setOrderActionSuccess
    ] =
        useState<string | null>(
            null
        );


    const [
        preparationQueue,
        setPreparationQueue
    ] =
        useState<AdminOrderQueueResponse | null>(
            null
        );


    const [
        preparationQueueCounts,
        setPreparationQueueCounts
    ] =
        useState<AdminOrderQueueCounts | null>(
            null
        );


    const [
        preparationQueueLoading,
        setPreparationQueueLoading
    ] =
        useState(
            false
        );


    const [
        preparationQueueRefreshing,
        setPreparationQueueRefreshing
    ] =
        useState(
            false
        );


    const [
        preparationQueueLastUpdated,
        setPreparationQueueLastUpdated
    ] =
        useState<Date | null>(
            null
        );


    const [
        preparationQueueError,
        setPreparationQueueError
    ] =
        useState<string | null>(
            null
        );


    const [
        selectedQueueOrderNumbers,
        setSelectedQueueOrderNumbers
    ] =
        useState<Set<string>>(
            () => new Set()
        );


    const [
        batchActionLoading,
        setBatchActionLoading
    ] =
        useState(
            false
        );


    const [
        batchActionError,
        setBatchActionError
    ] =
        useState<string | null>(
            null
        );


    const [
        batchActionSuccess,
        setBatchActionSuccess
    ] =
        useState<string | null>(
            null
        );


    /*
     * =========================================================
     * LOAD BRANCHES
     * =========================================================
     */

    useEffect(
        () => {

            if (!profile) {
                return;
            }


            const currentProfile =
                profile;


            const controller =
                new AbortController();


            async function loadBranches() {

                try {

                    const response =
                        await fetch(
                            `${API_BASE}/api/branches`,
                            {
                                signal:
                                    controller.signal,

                                cache:
                                    "no-store"
                            }
                        );


                    if (!response.ok) {

                        throw new Error(
                            "Unable to load branches."
                        );
                    }


                    const allBranches:
                        Branch[] =
                        await response.json();


                    const activeBranches =
                        allBranches.filter(
                            branch =>
                                branch.active
                        );


                    const allowedBranches =
                        currentProfile.roleName
                        === "OWNER_ADMIN"
                            ? activeBranches
                            : activeBranches.filter(
                                branch =>
                                    currentProfile.branchIds.includes(
                                        branch.id
                                    )
                            );


                    setBranches(
                        allowedBranches
                    );


                    if (
                        allowedBranches.length
                        > 0
                    ) {

                        setSelectedBranchId(
                            allowedBranches[0].id
                        );
                    }

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
                            : "Unable to load branches."
                    );

                } finally {

                    setBranchesLoading(
                        false
                    );
                }
            }


            void loadBranches();


            return () => {

                controller.abort();
            };

        },
        [
            profile
        ]
    );


    /*
     * =========================================================
     * LOAD ORDER LIST
     * =========================================================
     */

    const loadOrders =
        useCallback(
            async (
                branchId: number,
                currentPage: number,
                filter: OrderFilter,
                adminAuthorization: string,
                options?: {
                    signal?: AbortSignal;
                    background?: boolean;
                }
            ) => {

                if (
                    options?.background
                ) {

                    setRefreshing(
                        true
                    );

                } else {

                    setOrdersLoading(
                        true
                    );
                }


                setError(
                    null
                );


                try {

                    const result =
                        await getAdminOrders(
                            branchId,
                            adminAuthorization,
                            {
                                status:
                                    filter
                                    === "ALL"
                                        ? undefined
                                        : filter,

                                page:
                                    currentPage,

                                size:
                                    PAGE_SIZE,

                                signal:
                                    options?.signal
                            }
                        );


                    setOrderPage(
                        result
                    );


                    setLastUpdated(
                        new Date()
                    );

                } finally {

                    if (
                        options?.background
                    ) {

                        setRefreshing(
                            false
                        );

                    } else {

                        setOrdersLoading(
                            false
                        );
                    }
                }
            },
            []
        );


    /*
     * =========================================================
     * INITIAL / FILTER / PAGE LOAD
     * =========================================================
     */

    useEffect(
        () => {

            if (
                selectedBranchId
                === null
                ||
                authorization
                === null
            ) {

                return;
            }


            const branchId =
                selectedBranchId;


            const currentAuthorization =
                authorization;


            const currentPage =
                page;


            const currentStatus =
                statusFilter;


            const controller =
                new AbortController();


            async function run() {

                try {

                    await loadOrders(
                        branchId,
                        currentPage,
                        currentStatus,
                        currentAuthorization,
                        {
                            signal:
                                controller.signal
                        }
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
                            : "Unable to load orders."
                    );
                }
            }


            void run();


            return () => {

                controller.abort();
            };

        },
        [
            selectedBranchId,
            authorization,
            page,
            statusFilter,
            loadOrders
        ]
    );


    /*
     * =========================================================
     * BACKGROUND LIST REFRESH
     * =========================================================
     */

    const refreshOrders =
        useCallback(
            async () => {

                if (
                    selectedBranchId
                    === null
                    ||
                    authorization
                    === null
                    ||
                    refreshing
                ) {

                    return;
                }


                try {

                    await loadOrders(
                        selectedBranchId,
                        page,
                        statusFilter,
                        authorization,
                        {
                            background:
                                true
                        }
                    );

                } catch (exception) {

                    setError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to refresh orders."
                    );
                }
            },
            [
                selectedBranchId,
                authorization,
                refreshing,
                page,
                statusFilter,
                loadOrders
            ]
        );


    useEffect(
        () => {

            if (
                selectedBranchId
                === null
                ||
                authorization
                === null
            ) {

                return;
            }


            const interval =
                window.setInterval(
                    () => {

                        void refreshOrders();

                    },
                    AUTO_REFRESH_MS
                );


            return () => {

                window.clearInterval(
                    interval
                );
            };

        },
        [
            selectedBranchId,
            authorization,
            refreshOrders
        ]
    );


    /*
     * =========================================================
     * OPERATIONAL PREPARATION QUEUE
     * =========================================================
     */

    const loadPreparationQueue =
        useCallback(
            async (
                branchId: number,
                adminAuthorization: string,
                options?: {
                    signal?: AbortSignal;
                    background?: boolean;
                }
            ) => {

                if (options?.background) {

                    setPreparationQueueRefreshing(
                        true
                    );

                } else {

                    setPreparationQueueLoading(
                        true
                    );
                }


                setPreparationQueueError(
                    null
                );


                try {

                    const [
                        queueResult,
                        countsResult
                    ] =
                        await Promise.all([
                            getAdminPreparationQueue(
                                branchId,
                                adminAuthorization,
                                {
                                    limit: 50,
                                    signal: options?.signal
                                }
                            ),

                            getAdminPreparationQueueCounts(
                                branchId,
                                adminAuthorization,
                                options?.signal
                            )
                        ]);


                    setPreparationQueue(
                        queueResult
                    );


                    setPreparationQueueCounts(
                        countsResult
                    );


                    setSelectedQueueOrderNumbers(
                        current => {

                            const visibleNumbers =
                                new Set(
                                    queueResult.orders.map(
                                        order =>
                                            order.orderNumber
                                    )
                                );


                            return new Set(
                                Array.from(
                                    current
                                ).filter(
                                    orderNumber =>
                                        visibleNumbers.has(
                                            orderNumber
                                        )
                                )
                            );
                        }
                    );


                    setPreparationQueueLastUpdated(
                        new Date()
                    );

                } finally {

                    if (options?.background) {

                        setPreparationQueueRefreshing(
                            false
                        );

                    } else {

                        setPreparationQueueLoading(
                            false
                        );
                    }
                }
            },
            []
        );


    useEffect(
    () => {

        if (
            selectedBranchId === null
            ||
            authorization === null
        ) {
            return;
        }


        const branchId =
            selectedBranchId;


        const adminAuthorization =
            authorization;


        const controller =
            new AbortController();


        async function run() {

            try {

                await loadPreparationQueue(
                    branchId,
                    adminAuthorization,
                    {
                        signal:
                            controller.signal
                    }
                );

            } catch (exception) {

                if (
                    exception instanceof DOMException
                    &&
                    exception.name === "AbortError"
                ) {
                    return;
                }


                setPreparationQueueError(
                    exception instanceof Error
                        ? exception.message
                        : "Unable to load the preparation queue."
                );
            }
        }


        void run();


        return () => {

            controller.abort();
        };

    },
    [
        selectedBranchId,
        authorization,
        loadPreparationQueue
    ]
);


    const refreshPreparationQueue =
        useCallback(
            async () => {

                if (
                    selectedBranchId === null
                    ||
                    authorization === null
                    ||
                    preparationQueueRefreshing
                    ||
                    batchActionLoading
                ) {
                    return;
                }


                try {

                    await loadPreparationQueue(
                        selectedBranchId,
                        authorization,
                        {
                            background: true
                        }
                    );

                } catch (exception) {

                    setPreparationQueueError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to refresh the preparation queue."
                    );
                }
            },
            [
                selectedBranchId,
                authorization,
                preparationQueueRefreshing,
                batchActionLoading,
                loadPreparationQueue
            ]
        );


    useEffect(
        () => {

            if (
                selectedBranchId === null
                ||
                authorization === null
            ) {
                return;
            }


            const interval =
                window.setInterval(
                    () => {
                        void refreshPreparationQueue();
                    },
                    AUTO_REFRESH_MS
                );


            return () => {
                window.clearInterval(
                    interval
                );
            };

        },
        [
            selectedBranchId,
            authorization,
            refreshPreparationQueue
        ]
    );


    function toggleQueueOrderSelection(
        orderNumber: string
    ) {

        setSelectedQueueOrderNumbers(
            current => {

                const next =
                    new Set(
                        current
                    );


                if (next.has(orderNumber)) {
                    next.delete(orderNumber);
                } else {
                    next.add(orderNumber);
                }


                return next;
            }
        );
    }


    function selectAllLoadedQueueOrders() {

        const queueOrders =
            preparationQueue?.orders
            ?? [];


        setSelectedQueueOrderNumbers(
            new Set(
                queueOrders.map(
                    order =>
                        order.orderNumber
                )
            )
        );
    }


    function clearQueueSelection() {
        setSelectedQueueOrderNumbers(
            new Set()
        );
    }


    async function refreshAfterBatchAction() {

        if (
            selectedBranchId !== null
            &&
            authorization !== null
        ) {

            await Promise.all([
                loadPreparationQueue(
                    selectedBranchId,
                    authorization,
                    {
                        background: true
                    }
                ),
                refreshOrders()
            ]);
        }


        if (
            selectedOrderNumber !== null
            &&
            authorization !== null
        ) {

            try {
                await loadOrderDetail(
                    selectedOrderNumber,
                    authorization,
                    {
                        background: true
                    }
                );
            } catch {
                // Keep the batch result visible if drawer refresh fails.
            }
        }
    }


    async function handleStartNext() {

    if (
        selectedBranchId === null
        ||
        authorization === null
        ||
        batchActionLoading
    ) {
        return;
    }


    /*
     * Capture narrowed values before async work.
     *
     * This keeps TypeScript from treating the React state
     * values as number | null / string | null later.
     */
    const branchId =
        selectedBranchId;

    const adminAuthorization =
        authorization;


    const available =
        preparationQueueCounts?.actionableTotal
        ?? 0;


    if (available <= 0) {
        return;
    }


    const count =
        Math.min(
            10,
            available
        );


    const confirmed =
        window.confirm(
            `Start preparation for the next ${count} most urgent order${count === 1 ? "" : "s"}?`
        );


    if (!confirmed) {
        return;
    }


    setBatchActionLoading(
        true
    );

    setBatchActionError(
        null
    );

    setBatchActionSuccess(
        null
    );


    try {

        const result =
            await startNextAdminOrders(
                {
                    branchId,
                    count
                },
                adminAuthorization
            );


        setBatchActionSuccess(
            getBatchSummaryMessage(
                result
            )
        );


        clearQueueSelection();


        await refreshAfterBatchAction();

    } catch (exception) {

        setBatchActionError(
            exception instanceof Error
                ? exception.message
                : "Unable to start the next orders."
        );

    } finally {

        setBatchActionLoading(
            false
        );
    }
}


    async function handleStartSelected() {

    if (
        selectedBranchId === null
        ||
        authorization === null
        ||
        batchActionLoading
        ||
        selectedQueueOrderNumbers.size === 0
    ) {
        return;
    }


    const branchId =
        selectedBranchId;

    const adminAuthorization =
        authorization;


    const orderNumbers =
        Array.from(
            selectedQueueOrderNumbers
        );


    const confirmed =
        window.confirm(
            `Start preparation for ${orderNumbers.length} selected order${orderNumbers.length === 1 ? "" : "s"}?`
        );


    if (!confirmed) {
        return;
    }


    setBatchActionLoading(
        true
    );

    setBatchActionError(
        null
    );

    setBatchActionSuccess(
        null
    );


    try {

        const result =
            await startSelectedAdminOrders(
                {
                    branchId,
                    orderNumbers
                },
                adminAuthorization
            );


        setBatchActionSuccess(
            getBatchSummaryMessage(
                result
            )
        );


        clearQueueSelection();


        await refreshAfterBatchAction();

    } catch (exception) {

        setBatchActionError(
            exception instanceof Error
                ? exception.message
                : "Unable to start the selected orders."
        );

    } finally {

        setBatchActionLoading(
            false
        );
    }
}


    /*
     * =========================================================
     * ORDER DETAIL LOADER
     * =========================================================
     */

    const loadOrderDetail =
        useCallback(
            async (
                orderNumber: string,
                adminAuthorization: string,
                options?: {
                    signal?: AbortSignal;
                    background?: boolean;
                }
            ) => {

                if (
                    options?.background
                ) {

                    setOrderDetailRefreshing(
                        true
                    );

                } else {

                    setOrderDetailLoading(
                        true
                    );
                }


                setOrderDetailError(
                    null
                );


                try {

                    const result =
                        await getAdminOrderDetail(
                            orderNumber,
                            adminAuthorization,
                            options?.signal
                        );


                    setSelectedOrder(
                        result
                    );


                    setOrderDetailLastUpdated(
                        new Date()
                    );


                    return result;

                } finally {

                    if (
                        options?.background
                    ) {

                        setOrderDetailRefreshing(
                            false
                        );

                    } else {

                        setOrderDetailLoading(
                            false
                        );
                    }
                }
            },
            []
        );


    /*
     * =========================================================
     * OPEN ORDER DETAIL
     * =========================================================
     */

    async function openOrderDetail(
        orderNumber: string
    ) {

        if (
            authorization
            === null
        ) {

            return;
        }


        setSelectedOrderNumber(
            orderNumber
        );


        setSelectedOrder(
            null
        );


        setOrderDetailLastUpdated(
            null
        );


        setOrderDetailError(
            null
        );


        setOrderActionError(
            null
        );


        setOrderActionSuccess(
            null
        );


        try {

            await loadOrderDetail(
                orderNumber,
                authorization
            );

        } catch (exception) {

            setOrderDetailError(
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to load order details."
            );
        }
    }


    /*
     * =========================================================
     * MANUAL / BACKGROUND DETAIL REFRESH
     * =========================================================
     */

    const refreshSelectedOrder =
        useCallback(
            async () => {

                if (
                    selectedOrderNumber
                    === null
                    ||
                    authorization
                    === null
                    ||
                    orderDetailRefreshing
                    ||
                    orderActionLoading
                ) {

                    return;
                }


                try {

                    await loadOrderDetail(
                        selectedOrderNumber,
                        authorization,
                        {
                            background:
                                true
                        }
                    );

                } catch (exception) {

                    setOrderDetailError(
                        exception
                        instanceof Error
                            ? exception.message
                            : "Unable to refresh order details."
                    );
                }
            },
            [
                selectedOrderNumber,
                authorization,
                orderDetailRefreshing,
                orderActionLoading,
                loadOrderDetail
            ]
        );


    /*
     * Refresh the open drawer independently from
     * the main order list.
     *
     * This keeps the detail view synchronized when
     * another staff member changes the same order.
     */

    useEffect(
        () => {

            if (
                selectedOrderNumber
                === null
                ||
                authorization
                === null
            ) {

                return;
            }


            const interval =
                window.setInterval(
                    () => {

                        void refreshSelectedOrder();

                    },
                    ORDER_DETAIL_AUTO_REFRESH_MS
                );


            return () => {

                window.clearInterval(
                    interval
                );
            };

        },
        [
            selectedOrderNumber,
            authorization,
            refreshSelectedOrder
        ]
    );


    /*
     * =========================================================
     * CLOSE ORDER DETAIL
     * =========================================================
     */

    const closeOrderDetail =
        useCallback(
            () => {

                if (
                    orderActionLoading
                ) {

                    return;
                }


                setSelectedOrderNumber(
                    null
                );


                setSelectedOrder(
                    null
                );


                setOrderDetailLastUpdated(
                    null
                );


                setOrderDetailError(
                    null
                );


                setOrderActionError(
                    null
                );


                setOrderActionSuccess(
                    null
                );

            },
            [
                orderActionLoading
            ]
        );


    /*
     * =========================================================
     * ORDER WORKFLOW ACTION
     * =========================================================
     */

    async function handleOrderTransition() {

        if (
            selectedOrder
            === null
            ||
            authorization
            === null
            ||
            orderActionLoading
        ) {

            return;
        }


        const currentStatus =
            selectedOrder.orderStatus;


        const targetStatus =
            getNextOrderStatus(
                currentStatus
            );


        if (
            targetStatus
            === null
        ) {

            return;
        }


        if (
            targetStatus
            === "PICKED_UP"
        ) {

            const confirmed =
                window.confirm(
                    `Mark order ${selectedOrder.orderNumber} as picked up?`
                );


            if (!confirmed) {

                return;
            }
        }


        setOrderActionLoading(
            true
        );


        setOrderActionError(
            null
        );


        setOrderActionSuccess(
            null
        );


        try {

            const updated =
                await updateAdminOrderStatus(
                    selectedOrder.orderNumber,
                    {
                        status:
                            targetStatus
                    },
                    authorization
                );


            setSelectedOrder(
                updated
            );


            setOrderDetailLastUpdated(
                new Date()
            );


            setOrderActionSuccess(
                targetStatus
                === "PREPARING"
                    ? "Order preparation started successfully."
                    : targetStatus
                    === "READY_FOR_PICKUP"
                        ? "Order marked ready for pickup."
                        : "Order marked picked up successfully."
            );


            await refreshOrders();

        } catch (exception) {

            const message =
                exception
                instanceof Error
                    ? exception.message
                    : "Unable to update the order.";


            setOrderActionError(
                message
            );


            /*
             * Another device may have changed the order
             * after this drawer was loaded.
             *
             * Reload authoritative backend state before
             * allowing another action.
             */

            try {

                const latest =
                    await getAdminOrderDetail(
                        selectedOrder.orderNumber,
                        authorization
                    );


                setSelectedOrder(
                    latest
                );


                setOrderDetailLastUpdated(
                    new Date()
                );


                await refreshOrders();

            } catch {

                /*
                 * Keep the original action error visible.
                 * The staff member can retry Refresh Order.
                 */
            }

        } finally {

            setOrderActionLoading(
                false
            );
        }
    }


    /*
     * =========================================================
     * DRAWER BODY SCROLL LOCK
     * =========================================================
     */

    useEffect(
        () => {

            if (
                selectedOrderNumber
                === null
            ) {

                return;
            }


            const previousOverflow =
                document.body.style.overflow;


            document.body.style.overflow =
                "hidden";


            return () => {

                document.body.style.overflow =
                    previousOverflow;
            };

        },
        [
            selectedOrderNumber
        ]
    );


    /*
     * =========================================================
     * ESCAPE CLOSES DRAWER
     * =========================================================
     */

    useEffect(
        () => {

            if (
                selectedOrderNumber
                === null
            ) {

                return;
            }


            function handleKeyDown(
                event: KeyboardEvent
            ) {

                if (
                    event.key
                    === "Escape"
                ) {

                    closeOrderDetail();
                }
            }


            window.addEventListener(
                "keydown",
                handleKeyDown
            );


            return () => {

                window.removeEventListener(
                    "keydown",
                    handleKeyDown
                );
            };

        },
        [
            selectedOrderNumber,
            closeOrderDetail
        ]
    );


    /*
     * =========================================================
     * DERIVED STATE
     * =========================================================
     */

    const selectedBranch =
        branches.find(
            branch =>
                branch.id
                === selectedBranchId
        );


    const canViewOrders =
        hasPermission(
            "ORDER_VIEW"
        );


    const canStartPreparation =
        hasPermission(
            "ORDER_START_PREPARATION"
        );


    const orders =
        useMemo(
            () =>
                orderPage?.orders
                ?? [],
            [
                orderPage
            ]
        );


    const filteredOrders =
        useMemo(
            () => {

                const normalizedSearch =
                    search
                        .trim()
                        .toLowerCase();


                if (!normalizedSearch) {

                    return orders;
                }


                return orders.filter(
                    order => {

                        const searchable =
                            [
                                order.orderNumber,
                                order.customerName,
                                order.maskedCustomerPhone
                                    ?? "",
                                order.pickupDate,
                                order.pickupStartTime,
                                order.pickupEndTime
                            ]
                                .join(
                                    " "
                                )
                                .toLowerCase();


                        return searchable.includes(
                            normalizedSearch
                        );
                    }
                );
            },
            [
                orders,
                search
            ]
        );


    if (
        profile
        &&
        !canViewOrders
    ) {

        return (
            <div
                className="
                    px-4
                    py-6
                    sm:px-6
                    sm:py-8
                    lg:px-8
                "
            >

                <div
                    className="
                        mx-auto
                        max-w-7xl
                    "
                >

                    <div
                        className="
                            rounded-2xl
                            border
                            border-red-200
                            bg-red-50
                            p-6
                        "
                    >

                        <h1
                            className="
                                text-xl
                                font-bold
                                text-red-900
                            "
                        >
                            Access denied
                        </h1>


                        <p
                            className="
                                mt-2
                                text-sm
                                text-red-700
                            "
                        >
                            You do not have permission to view live orders.
                        </p>

                    </div>

                </div>

            </div>
        );
    }


    return (
        <>
            <div
                className="
                    px-4
                    py-6
                    sm:px-6
                    sm:py-8
                    lg:px-8
                "
            >

                <div
                    className="
                        mx-auto
                        w-full
                        max-w-7xl
                    "
                >

                    {/* PAGE HEADER */}

                    <div
                        className="
                            flex
                            flex-col
                            gap-5

                            lg:flex-row
                            lg:items-start
                            lg:justify-between
                        "
                    >

                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-[0.16em]
                                    text-[#c88a20]
                                "
                            >
                                Daily operations
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
                                Live Orders
                            </h1>


                            <p
                                className="
                                    mt-3
                                    max-w-3xl
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                Monitor customer pickup orders and move them through preparation and pickup.
                            </p>

                        </div>


                        <div
                            className="
                                w-full
                                shrink-0

                                lg:w-80
                            "
                        >

                            <label
                                htmlFor="orders-branch"
                                className="
                                    mb-2
                                    block
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                Branch
                            </label>


                            <select
                                id="orders-branch"
                                value={
                                    selectedBranchId
                                    ?? ""
                                }
                                disabled={
                                    branchesLoading
                                    ||
                                    branches.length
                                    === 0
                                }
                                onChange={
                                    event => {

                                        const value =
                                            Number(
                                                event.target.value
                                            );


                                        setSelectedBranchId(
                                            value
                                        );


                                        setPage(
                                            0
                                        );


                                        setSearch(
                                            ""
                                        );


                                        setSelectedQueueOrderNumbers(
                                            new Set()
                                        );


                                        setBatchActionError(
                                            null
                                        );


                                        setBatchActionSuccess(
                                            null
                                        );
                                    }
                                }
                                className="
                                    min-h-12
                                    w-full
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                    outline-none

                                    focus:border-[#c88a20]
                                    focus:ring-4
                                    focus:ring-[#f6dfad]/40

                                    disabled:bg-[#f8f4f1]
                                "
                            >

                                {
                                    branches.length
                                    === 0
                                    && (

                                        <option value="">
                                            No branch available
                                        </option>

                                    )
                                }


                                {
                                    branches.map(
                                        branch => (

                                            <option
                                                key={
                                                    branch.id
                                                }
                                                value={
                                                    branch.id
                                                }
                                            >
                                                {
                                                    branch.name
                                                }
                                            </option>

                                        )
                                    )
                                }

                            </select>

                        </div>

                    </div>


                    {/* BRANCH SUMMARY */}

                    {
                        selectedBranch
                        && (

                            <section
                                className="
                                    mt-6
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-5
                                    py-4
                                "
                            >

                                <div
                                    className="
                                        flex
                                        flex-col
                                        gap-4

                                        sm:flex-row
                                        sm:items-center
                                        sm:justify-between
                                    "
                                >

                                    <div>

                                        <p
                                            className="
                                                font-bold
                                                text-[#241715]
                                            "
                                        >
                                            {
                                                selectedBranch.name
                                            }
                                        </p>


                                        <p
                                            className="
                                                mt-1
                                                text-sm
                                                text-[#756763]
                                            "
                                        >
                                            {
                                                selectedBranch.code
                                            }

                                            {" • "}

                                            {
                                                selectedBranch.address
                                            }
                                        </p>

                                    </div>


                                    <div
                                        className="
                                            flex
                                            flex-col
                                            gap-3

                                            sm:items-end
                                        "
                                    >

                                        <div
                                            className="
                                                text-sm
                                                font-semibold
                                                text-[#7a1625]
                                            "
                                        >
                                            {
                                                orderPage
                                                    ? `${orderPage.totalElements} orders`
                                                    : "— orders"
                                            }
                                        </div>


                                        <div
                                            className="
                                                flex
                                                flex-wrap
                                                items-center
                                                gap-3
                                            "
                                        >

                                            <p
                                                className="
                                                    text-xs
                                                    text-[#756763]
                                                "
                                            >
                                                Last updated:{" "}

                                                <span
                                                    className="
                                                        font-semibold
                                                        text-[#241715]
                                                    "
                                                >
                                                    {
                                                        formatLastUpdated(
                                                            lastUpdated
                                                        )
                                                    }
                                                </span>
                                            </p>


                                            <button
                                                type="button"
                                                disabled={
                                                    refreshing
                                                    ||
                                                    ordersLoading
                                                }
                                                onClick={
                                                    () => {

                                                        void refreshOrders();
                                                    }
                                                }
                                                className="
                                                    min-h-9
                                                    rounded-lg
                                                    border
                                                    border-[#eadfd6]
                                                    bg-white
                                                    px-4
                                                    text-xs
                                                    font-semibold
                                                    text-[#7a1625]

                                                    hover:bg-[#fff1e9]

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-50
                                                "
                                            >
                                                {
                                                    refreshing
                                                        ? "Refreshing..."
                                                        : "Refresh"
                                                }
                                            </button>

                                        </div>

                                    </div>

                                </div>

                            </section>

                        )
                    }


                    {/* OPERATIONAL PREPARATION QUEUE */}

                    <section
                        className="
                            mt-6
                            overflow-hidden
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                        "
                    >

                        <div
                            className="
                                border-b
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                px-5
                                py-5

                                sm:px-6
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-4

                                    lg:flex-row
                                    lg:items-start
                                    lg:justify-between
                                "
                            >

                                <div>

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-[0.14em]
                                            text-[#c88a20]
                                        "
                                    >
                                        Kitchen operations
                                    </p>


                                    <h2
                                        className="
                                            mt-1
                                            text-xl
                                            font-bold
                                            text-[#241715]

                                            sm:text-2xl
                                        "
                                    >
                                        Preparation Queue
                                    </h2>


                                    <p
                                        className="
                                            mt-2
                                            max-w-3xl
                                            text-sm
                                            leading-6
                                            text-[#756763]
                                        "
                                    >
                                        Only confirmed orders whose preparation window is open appear here. Orders are ordered by pickup urgency.
                                    </p>

                                </div>


                                <div
                                    className="
                                        flex
                                        flex-wrap
                                        items-center
                                        gap-2
                                    "
                                >

                                    <button
                                        type="button"
                                        disabled={
                                            preparationQueueRefreshing
                                            ||
                                            preparationQueueLoading
                                            ||
                                            batchActionLoading
                                        }
                                        onClick={
                                            () => {
                                                void refreshPreparationQueue();
                                            }
                                        }
                                        className="
                                            min-h-10
                                            rounded-xl
                                            border
                                            border-[#eadfd6]
                                            bg-white
                                            px-4
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]

                                            hover:bg-[#fff1e9]

                                            disabled:cursor-not-allowed
                                            disabled:opacity-50
                                        "
                                    >
                                        {
                                            preparationQueueRefreshing
                                                ? "Refreshing..."
                                                : "Refresh Queue"
                                        }
                                    </button>

                                </div>

                            </div>


                            <p
                                className="
                                    mt-3
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                Queue updated:{" "}

                                <span
                                    className="
                                        font-semibold
                                        text-[#241715]
                                    "
                                >
                                    {
                                        formatLastUpdated(
                                            preparationQueueLastUpdated
                                        )
                                    }
                                </span>
                            </p>

                        </div>


                        <div
                            className="
                                p-5

                                sm:p-6
                            "
                        >

                            <div
                                className="
                                    grid
                                    gap-3

                                    sm:grid-cols-2
                                    xl:grid-cols-5
                                "
                            >

                                <QueueCountCard
                                    label="Overdue"
                                    value={
                                        preparationQueueCounts?.overdue
                                        ?? 0
                                    }
                                    emphasis="danger"
                                />


                                <QueueCountCard
                                    label="Needs preparation"
                                    value={
                                        preparationQueueCounts?.eligible
                                        ?? 0
                                    }
                                    emphasis="warning"
                                />


                                <QueueCountCard
                                    label="Scheduled future"
                                    value={
                                        preparationQueueCounts?.scheduled
                                        ?? 0
                                    }
                                />


                                <QueueCountCard
                                    label="Preparing"
                                    value={
                                        preparationQueueCounts?.preparing
                                        ?? 0
                                    }
                                    emphasis="warning"
                                />


                                <QueueCountCard
                                    label="Ready"
                                    value={
                                        preparationQueueCounts?.ready
                                        ?? 0
                                    }
                                    emphasis="success"
                                />

                            </div>


                            {
                                preparationQueueError
                                && (

                                    <div
                                        role="alert"
                                        className="
                                            mt-5
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
                                            preparationQueueError
                                        }
                                    </div>

                                )
                            }


                            {
                                batchActionError
                                && (

                                    <div
                                        role="alert"
                                        className="
                                            mt-5
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
                                            batchActionError
                                        }
                                    </div>

                                )
                            }


                            {
                                batchActionSuccess
                                && (

                                    <div
                                        role="status"
                                        className="
                                            mt-5
                                            rounded-xl
                                            border
                                            border-green-200
                                            bg-green-50
                                            px-4
                                            py-3
                                            text-sm
                                            font-medium
                                            text-green-800
                                        "
                                    >
                                        {
                                            batchActionSuccess
                                        }
                                    </div>

                                )
                            }


                            {
                                canStartPreparation
                                && (

                                    <div
                                        className="
                                            mt-5
                                            flex
                                            flex-col
                                            gap-3
                                            rounded-xl
                                            border
                                            border-[#eadfd6]
                                            bg-[#fffaf3]
                                            p-4

                                            lg:flex-row
                                            lg:items-center
                                            lg:justify-between
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

                                            <button
                                                type="button"
                                                disabled={
                                                    batchActionLoading
                                                    ||
                                                    (
                                                        preparationQueueCounts?.actionableTotal
                                                        ?? 0
                                                    )
                                                    <= 0
                                                }
                                                onClick={
                                                    () => {
                                                        void handleStartNext();
                                                    }
                                                }
                                                className="
                                                    min-h-11
                                                    rounded-xl
                                                    bg-[#7a1625]
                                                    px-5
                                                    text-sm
                                                    font-semibold
                                                    text-white

                                                    hover:bg-[#5d0f1b]

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-40
                                                "
                                            >
                                                {
                                                    batchActionLoading
                                                        ? "Processing..."
                                                        : `Start Next ${Math.min(
                                                            10,
                                                            preparationQueueCounts?.actionableTotal
                                                            ?? 0
                                                        )}`
                                                }
                                            </button>


                                            <button
                                                type="button"
                                                disabled={
                                                    batchActionLoading
                                                    ||
                                                    selectedQueueOrderNumbers.size === 0
                                                }
                                                onClick={
                                                    () => {
                                                        void handleStartSelected();
                                                    }
                                                }
                                                className="
                                                    min-h-11
                                                    rounded-xl
                                                    border
                                                    border-[#7a1625]
                                                    bg-white
                                                    px-5
                                                    text-sm
                                                    font-semibold
                                                    text-[#7a1625]

                                                    hover:bg-[#fff1e9]

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-40
                                                "
                                            >
                                                Start Selected ({
                                                    selectedQueueOrderNumbers.size
                                                })
                                            </button>

                                        </div>


                                        <div
                                            className="
                                                flex
                                                flex-wrap
                                                items-center
                                                gap-2
                                            "
                                        >

                                            <button
                                                type="button"
                                                disabled={
                                                    batchActionLoading
                                                    ||
                                                    (
                                                        preparationQueue?.orders.length
                                                        ?? 0
                                                    )
                                                    === 0
                                                }
                                                onClick={
                                                    selectAllLoadedQueueOrders
                                                }
                                                className="
                                                    min-h-9
                                                    rounded-lg
                                                    border
                                                    border-[#eadfd6]
                                                    bg-white
                                                    px-3
                                                    text-xs
                                                    font-semibold
                                                    text-[#7a1625]

                                                    hover:bg-[#fff1e9]

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-40
                                                "
                                            >
                                                Select loaded
                                            </button>


                                            <button
                                                type="button"
                                                disabled={
                                                    batchActionLoading
                                                    ||
                                                    selectedQueueOrderNumbers.size === 0
                                                }
                                                onClick={
                                                    clearQueueSelection
                                                }
                                                className="
                                                    min-h-9
                                                    rounded-lg
                                                    border
                                                    border-[#eadfd6]
                                                    bg-white
                                                    px-3
                                                    text-xs
                                                    font-semibold
                                                    text-[#756763]

                                                    hover:bg-[#fff1e9]

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-40
                                                "
                                            >
                                                Clear selection
                                            </button>

                                        </div>

                                    </div>

                                )
                            }


                            {
                                preparationQueueLoading
                                ? (

                                    <div
                                        className="
                                            mt-5
                                            rounded-xl
                                            border
                                            border-[#eadfd6]
                                            bg-white
                                            p-8
                                            text-center
                                            text-sm
                                            font-semibold
                                            text-[#756763]
                                        "
                                    >
                                        Loading preparation queue...
                                    </div>

                                )
                                : (
                                    preparationQueue?.orders.length
                                    ?? 0
                                )
                                === 0
                                    ? (

                                        <div
                                            className="
                                                mt-5
                                                rounded-xl
                                                border
                                                border-[#eadfd6]
                                                bg-white
                                                p-8
                                                text-center
                                            "
                                        >

                                            <p
                                                className="
                                                    font-semibold
                                                    text-[#241715]
                                                "
                                            >
                                                No orders need preparation right now.
                                            </p>


                                            <p
                                                className="
                                                    mt-2
                                                    text-sm
                                                    text-[#756763]
                                                "
                                            >
                                                Future confirmed orders remain scheduled until their preparation window opens.
                                            </p>

                                        </div>

                                    )
                                    : (

                                        <div
                                            className="
                                                mt-5
                                                space-y-3
                                            "
                                        >

                                            {
                                                preparationQueue?.orders.map(
                                                    order => (

                                                        <PreparationQueueCard
                                                            key={
                                                                order.orderNumber
                                                            }
                                                            order={
                                                                order
                                                            }
                                                            selected={
                                                                selectedQueueOrderNumbers.has(
                                                                    order.orderNumber
                                                                )
                                                            }
                                                            selectionEnabled={
                                                                canStartPreparation
                                                                &&
                                                                !batchActionLoading
                                                            }
                                                            onToggleSelection={
                                                                toggleQueueOrderSelection
                                                            }
                                                            onView={
                                                                openOrderDetail
                                                            }
                                                        />

                                                    )
                                                )
                                            }


                                            {
                                                preparationQueue?.hasMore
                                                && (

                                                    <p
                                                        className="
                                                            rounded-xl
                                                            border
                                                            border-[#eadfd6]
                                                            bg-[#fffaf3]
                                                            px-4
                                                            py-3
                                                            text-center
                                                            text-xs
                                                            font-medium
                                                            text-[#756763]
                                                        "
                                                    >
                                                        More eligible orders exist beyond the first {
                                                            preparationQueue.limit
                                                        }. Starting a batch and refreshing will bring the next urgent orders into view.
                                                    </p>

                                                )
                                            }

                                        </div>

                                    )
                            }

                        </div>

                    </section>


                    {/* GENERAL ORDERS */}

                    <div
                        className="
                            mt-8
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
                            Orders and history
                        </p>


                        <h2
                            className="
                                mt-1
                                text-2xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            General Orders
                        </h2>

                    </div>


                    {/* STATUS FILTERS */}

                    <section
                        className="
                            mt-6
                            overflow-x-auto
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-2
                        "
                    >

                        <div
                            className="
                                flex
                                min-w-max
                                gap-2
                            "
                        >

                            {
                                STATUS_TABS.map(
                                    tab => {

                                        const active =
                                            statusFilter
                                            === tab.value;


                                        return (

                                            <button
                                                key={
                                                    tab.value
                                                }
                                                type="button"
                                                onClick={
                                                    () => {

                                                        setStatusFilter(
                                                            tab.value
                                                        );


                                                        setPage(
                                                            0
                                                        );


                                                        setSearch(
                                                            ""
                                                        );
                                                    }
                                                }
                                                className={[
                                                    "min-h-10 rounded-xl px-4 text-sm font-semibold transition",

                                                    active
                                                        ? "bg-[#7a1625] text-white"
                                                        : "bg-[#fffaf3] text-[#756763] hover:bg-[#fff1e9] hover:text-[#7a1625]"
                                                ].join(
                                                    " "
                                                )}
                                            >
                                                {
                                                    tab.label
                                                }
                                            </button>

                                        );
                                    }
                                )
                            }

                        </div>

                    </section>


                    {/* SEARCH */}

                    <section
                        className="
                            mt-4
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-4
                        "
                    >

                        <div
                            className="
                                flex
                                flex-col
                                gap-2

                                sm:flex-row
                                sm:items-end
                                sm:justify-between
                            "
                        >

                            <div
                                className="
                                    w-full

                                    sm:max-w-xl
                                "
                            >

                                <label
                                    htmlFor="orders-search"
                                    className="
                                        mb-2
                                        block
                                        text-sm
                                        font-semibold
                                        text-[#241715]
                                    "
                                >
                                    Search loaded orders
                                </label>


                                <input
                                    id="orders-search"
                                    type="search"
                                    value={
                                        search
                                    }
                                    onChange={
                                        event =>
                                            setSearch(
                                                event.target.value
                                            )
                                    }
                                    placeholder="Order number, customer or phone"
                                    className="
                                        min-h-11
                                        w-full
                                        rounded-xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-4
                                        text-sm
                                        text-[#241715]
                                        outline-none

                                        focus:border-[#c88a20]
                                        focus:ring-4
                                        focus:ring-[#f6dfad]/40
                                    "
                                />

                            </div>


                            <p
                                className="
                                    text-xs
                                    leading-5
                                    text-[#756763]
                                "
                            >
                                Search applies to the current loaded page of up to{" "}
                                {PAGE_SIZE} orders.
                            </p>

                        </div>

                    </section>


                    {/* ERROR */}

                    {
                        error
                        && (

                            <div
                                role="alert"
                                className="
                                    mt-6
                                    rounded-2xl
                                    border
                                    border-red-200
                                    bg-red-50
                                    px-5
                                    py-4
                                    text-sm
                                    font-medium
                                    text-red-700
                                "
                            >
                                {
                                    error
                                }
                            </div>

                        )
                    }


                    {/* ORDERS */}

                    {
                        ordersLoading
                        ? (

                            <div
                                className="
                                    mt-6
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-10
                                    text-center
                                    text-sm
                                    font-semibold
                                    text-[#756763]
                                "
                            >
                                Loading orders...
                            </div>

                        )
                        : filteredOrders.length
                        === 0
                            ? (

                                <div
                                    className="
                                        mt-6
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-10
                                        text-center
                                    "
                                >

                                    <p
                                        className="
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            search.trim()
                                                ? "No loaded orders match your search."
                                                : "No orders found."
                                        }
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        {
                                            search.trim()
                                                ? "Clear the search or move to another page."
                                                : "There are no orders matching the selected branch and status."
                                        }
                                    </p>

                                </div>

                            )
                            : (

                                <div
                                    className="
                                        mt-6
                                        space-y-4
                                    "
                                >

                                    {
                                        filteredOrders.map(
                                            order => (

                                                <OrderCard
                                                    key={
                                                        order.orderNumber
                                                    }
                                                    order={
                                                        order
                                                    }
                                                    onView={
                                                        openOrderDetail
                                                    }
                                                />

                                            )
                                        )
                                    }

                                </div>

                            )
                    }


                    {/* PAGINATION */}

                    {
                        orderPage
                        &&
                        orderPage.totalPages
                        > 1
                        && (

                            <div
                                className="
                                    mt-6
                                    flex
                                    flex-col
                                    gap-3
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-5
                                    py-4

                                    sm:flex-row
                                    sm:items-center
                                    sm:justify-between
                                "
                            >

                                <p
                                    className="
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    Page{" "}

                                    <strong
                                        className="
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            orderPage.page
                                            + 1
                                        }
                                    </strong>

                                    {" "}of{" "}

                                    <strong
                                        className="
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            orderPage.totalPages
                                        }
                                    </strong>

                                    {" • "}

                                    {
                                        orderPage.totalElements
                                    } total orders
                                </p>


                                <div
                                    className="
                                        flex
                                        gap-2
                                    "
                                >

                                    <button
                                        type="button"
                                        disabled={
                                            orderPage.page
                                            <= 0
                                        }
                                        onClick={
                                            () => {

                                                setSearch(
                                                    ""
                                                );


                                                setPage(
                                                    current =>
                                                        Math.max(
                                                            current
                                                            - 1,
                                                            0
                                                        )
                                                );
                                            }
                                        }
                                        className="
                                            min-h-10
                                            rounded-xl
                                            border
                                            border-[#eadfd6]
                                            bg-white
                                            px-4
                                            text-sm
                                            font-semibold
                                            text-[#7a1625]

                                            hover:bg-[#fff1e9]

                                            disabled:cursor-not-allowed
                                            disabled:opacity-40
                                        "
                                    >
                                        Previous
                                    </button>


                                    <button
                                        type="button"
                                        disabled={
                                            orderPage.page
                                            >=
                                            orderPage.totalPages
                                            - 1
                                        }
                                        onClick={
                                            () => {

                                                setSearch(
                                                    ""
                                                );


                                                setPage(
                                                    current =>
                                                        current
                                                        + 1
                                                );
                                            }
                                        }
                                        className="
                                            min-h-10
                                            rounded-xl
                                            bg-[#7a1625]
                                            px-4
                                            text-sm
                                            font-semibold
                                            text-white

                                            hover:bg-[#5d0f1b]

                                            disabled:cursor-not-allowed
                                            disabled:opacity-40
                                        "
                                    >
                                        Next
                                    </button>

                                </div>

                            </div>

                        )
                    }

                </div>

            </div>


            {
                selectedOrderNumber
                !== null
                &&
                typeof document
                !== "undefined"
                &&
                createPortal(
                    <OrderDetailDrawer
                        orderNumber={
                            selectedOrderNumber
                        }
                        order={
                            selectedOrder
                        }
                        loading={
                            orderDetailLoading
                        }
                        refreshing={
                            orderDetailRefreshing
                        }
                        lastUpdated={
                            orderDetailLastUpdated
                        }
                        error={
                            orderDetailError
                        }
                        actionLoading={
                            orderActionLoading
                        }
                        actionError={
                            orderActionError
                        }
                        actionSuccess={
                            orderActionSuccess
                        }
                        authorization={
                            authorization ?? ""
                        }
                        hasPermission={
                            hasPermission
                        }
                        onRefresh={
                            refreshSelectedOrder
                        }
                        onTransition={
                            handleOrderTransition
                        }
                        onClose={
                            closeOrderDetail
                        }
                    />,
                    document.body
                )
            }

        </>
    );
}


function QueueCountCard({
    label,
    value,
    emphasis = "default"
}: {
    label: string;
    value: number;
    emphasis?:
        | "default"
        | "danger"
        | "warning"
        | "success";
}) {

    const classes =
        emphasis === "danger"
            ? "border-red-200 bg-red-50 text-red-800"
            : emphasis === "warning"
                ? "border-amber-200 bg-amber-50 text-amber-800"
                : emphasis === "success"
                    ? "border-green-200 bg-green-50 text-green-800"
                    : "border-[#eadfd6] bg-[#fffaf3] text-[#241715]";


    return (
        <div
            className={[
                "rounded-xl border p-4",
                classes
            ].join(
                " "
            )}
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    opacity-75
                "
            >
                {
                    label
                }
            </p>


            <p
                className="
                    mt-2
                    text-2xl
                    font-bold
                "
            >
                {
                    value
                }
            </p>

        </div>
    );
}


function PreparationQueueCard({
    order,
    selected,
    selectionEnabled,
    onToggleSelection,
    onView
}: {
    order: AdminOrderQueueItem;
    selected: boolean;
    selectionEnabled: boolean;
    onToggleSelection: (
        orderNumber: string
    ) => void;
    onView: (
        orderNumber: string
    ) => void;
}) {

    const overdue =
        order.preparationStatus
        === "OVERDUE";


    return (
        <article
            className={[
                "rounded-xl border p-4 transition",

                overdue
                    ? "border-red-200 bg-red-50/40"
                    : "border-[#eadfd6] bg-white",

                selected
                    ? "ring-2 ring-[#c88a20]/40"
                    : ""
            ].join(
                " "
            )}
        >

            <div
                className="
                    flex
                    flex-col
                    gap-4

                    lg:flex-row
                    lg:items-center
                    lg:justify-between
                "
            >

                <div
                    className="
                        flex
                        min-w-0
                        items-start
                        gap-3
                    "
                >

                    <input
                        type="checkbox"
                        aria-label={
                            `Select ${order.orderNumber}`
                        }
                        checked={
                            selected
                        }
                        disabled={
                            !selectionEnabled
                        }
                        onChange={
                            () =>
                                onToggleSelection(
                                    order.orderNumber
                                )
                        }
                        className="
                            mt-1
                            h-5
                            w-5
                            shrink-0
                            accent-[#7a1625]

                            disabled:cursor-not-allowed
                            disabled:opacity-40
                        "
                    />


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
                                className={[
                                    "rounded-full border px-3 py-1 text-xs font-bold",
                                    preparationStatusClasses(
                                        order.preparationStatus
                                    )
                                ].join(
                                    " "
                                )}
                            >
                                {
                                    order.preparationStatus
                                    === "OVERDUE"
                                        ? "OVERDUE"
                                        : "NEEDS PREPARATION"
                                }
                            </span>


                            <span
                                className="
                                    text-xs
                                    font-semibold
                                    text-[#756763]
                                "
                            >
                                {
                                    formatMinutesUntilPickup(
                                        order.minutesUntilPickup
                                    )
                                }
                            </span>

                        </div>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    onView(
                                        order.orderNumber
                                    )
                            }
                            className="
                                mt-2
                                break-all
                                text-left
                                text-base
                                font-bold
                                text-[#7a1625]
                                underline-offset-4

                                hover:underline
                            "
                        >
                            {
                                order.orderNumber
                            }
                        </button>


                        <p
                            className="
                                mt-1
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            {
                                order.customerName
                            }
                        </p>


                        <div
                            className="
                                mt-3
                                flex
                                flex-wrap
                                gap-x-5
                                gap-y-2
                                text-sm
                                text-[#756763]
                            "
                        >

                            <span>
                                <strong
                                    className="
                                        text-[#241715]
                                    "
                                >
                                    Pickup:
                                </strong>{" "}
                                {
                                    order.pickupDate
                                }{","}{" "}
                                {
                                    order.pickupStartTime
                                }
                                {" - "}
                                {
                                    order.pickupEndTime
                                }
                            </span>


                            <span>
                                <strong
                                    className="
                                        text-[#241715]
                                    "
                                >
                                    Type:
                                </strong>{" "}
                                {
                                    formatPickupType(
                                        order.pickupType
                                    )
                                }
                            </span>


                            <span>
                                <strong
                                    className="
                                        text-[#241715]
                                    "
                                >
                                    Total:
                                </strong>{" "}
                                {
                                    formatPrice(
                                        order.totalAmount
                                    )
                                }
                            </span>

                        </div>

                    </div>

                </div>


                <button
                    type="button"
                    onClick={
                        () =>
                            onView(
                                order.orderNumber
                            )
                    }
                    className="
                        min-h-10
                        shrink-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-sm
                        font-semibold
                        text-[#7a1625]

                        hover:bg-[#fff1e9]
                    "
                >
                    View Order
                </button>

            </div>

        </article>
    );
}


function OrderCard({
    order,
    onView
}: {
    order: AdminOrderSummary;

    onView: (
        orderNumber: string
    ) => void;
}) {

    return (
        <article
            className="
                overflow-hidden
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
            "
        >

            <div
                className="
                    grid
                    gap-5
                    p-5

                    sm:p-6

                    lg:grid-cols-[minmax(0,1fr)_auto]
                    lg:items-start
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

                        <h2
                            className="
                                text-lg
                                font-bold
                                text-[#241715]
                            "
                        >
                            {
                                order.orderNumber
                            }
                        </h2>


                        <span
                            className={[
                                "rounded-full border px-3 py-1 text-xs font-semibold",

                                statusClasses(
                                    order.orderStatus
                                )
                            ].join(
                                " "
                            )}
                        >
                            {
                                formatStatus(
                                    order.orderStatus
                                )
                            }
                        </span>


                        {
                            order.paymentStatus
                            && (

                                <span
                                    className={[
                                        "rounded-full px-3 py-1 text-xs font-semibold",

                                        paymentStatusClasses(
                                            order.paymentStatus
                                        )
                                    ].join(
                                        " "
                                    )}
                                >
                                    Payment:{" "}

                                    {
                                        formatPaymentStatus(
                                            order.paymentStatus
                                        )
                                    }
                                </span>

                            )
                        }

                    </div>


                    <p
                        className="
                            mt-3
                            font-semibold
                            text-[#241715]
                        "
                    >
                        {
                            order.customerName
                        }
                    </p>


                    {
                        order.maskedCustomerPhone
                        && (

                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                {
                                    order.maskedCustomerPhone
                                }
                            </p>

                        )
                    }


                    <div
                        className="
                            mt-4
                            grid
                            gap-3

                            sm:grid-cols-2
                            xl:grid-cols-4
                        "
                    >

                        <InfoItem
                            label="Pickup date"
                            value={
                                order.pickupDate
                            }
                        />


                        <InfoItem
                            label="Pickup time"
                            value={
                                `${order.pickupStartTime} - ${order.pickupEndTime}`
                            }
                        />


                        <InfoItem
                            label="Pickup type"
                            value={
                                formatPickupType(
                                    order.pickupType
                                )
                            }
                        />


                        <InfoItem
                            label="Total"
                            value={
                                formatPrice(
                                    order.totalAmount
                                )
                            }
                            strong
                        />

                    </div>

                </div>


                <div
                    className="
                        flex
                        flex-col
                        gap-2

                        lg:min-w-44
                        lg:items-end
                    "
                >

                    <p
                        className="
                            text-xs
                            font-semibold
                            uppercase
                            tracking-wide
                            text-[#9a8983]
                        "
                    >
                        Created
                    </p>


                    <p
                        className="
                            text-sm
                            font-medium
                            text-[#756763]
                        "
                    >
                        {
                            formatCreatedAt(
                                order.createdAt
                            )
                        }
                    </p>


                    <button
                        type="button"
                        onClick={
                            () =>
                                onView(
                                    order.orderNumber
                                )
                        }
                        className="
                            mt-2
                            min-h-10
                            w-full
                            rounded-xl
                            bg-[#7a1625]
                            px-4
                            text-sm
                            font-semibold
                            text-white
                            transition

                            hover:bg-[#5d0f1b]

                            lg:w-auto
                        "
                    >
                        View Order
                    </button>

                </div>

            </div>

        </article>
    );
}


function OrderDetailDrawer({
    orderNumber,
    order,
    loading,
    refreshing,
    lastUpdated,
    error,
    actionLoading,
    actionError,
    actionSuccess,
    authorization,
    hasPermission,
    onRefresh,
    onTransition,
    onClose
}: {
    orderNumber: string;

    order: AdminOrderDetail | null;

    loading: boolean;

    refreshing: boolean;

    lastUpdated: Date | null;

    error: string | null;

    actionLoading: boolean;

    actionError: string | null;

    actionSuccess: string | null;

    authorization: string;

    hasPermission: (
        permission: string
    ) => boolean;

    onRefresh: () => Promise<void>;

    onTransition: () => Promise<void>;

    onClose: () => void;
}) {

    const router =
    useRouter();

    const nextStatus =
        order
            ? getNextOrderStatus(
                order.orderStatus
            )
            : null;


    const requiredPermission =
        order
            ? getRequiredPermissionForTransition(
                order.orderStatus
            )
            : null;


    const actionLabel =
        order
            ? getTransitionLabel(
                order.orderStatus
            )
            : null;


    const hasActionPermission =
        requiredPermission
        === null
            ? false
            : hasPermission(
                requiredPermission
            );
    
    const canPrintKot =
    order !== null
    &&
    (
        order.orderStatus
        === "PREPARING"
        ||
        order.orderStatus
        === "READY_FOR_PICKUP"
        ||
        order.orderStatus
        === "PICKED_UP"
    );


    const [
        kotReprintLoading,
        setKotReprintLoading
    ] =
        useState(
            false
        );


    const [
        kotReprintError,
        setKotReprintError
    ] =
        useState<string | null>(
            null
        );


    const [
        kotReprintSuccess,
        setKotReprintSuccess
    ] =
        useState<string | null>(
            null
        );


    async function handleKotReprint() {

        if (
            order === null
            ||
            kotReprintLoading
        ) {

            return;
        }


        const confirmed =
            window.confirm(
                `Queue another kitchen print for order ${order.orderNumber}?`
            );


        if (!confirmed) {

            return;
        }


        setKotReprintLoading(
            true
        );


        setKotReprintError(
            null
        );


        setKotReprintSuccess(
            null
        );


        try {

            const result =
                await queueAdminKotReprintForOrder(
                    order.orderNumber,
                    authorization
                );


            setKotReprintSuccess(
                `Reprint queued successfully as print job #${result.printJobId}.`
            );

        } catch (exception) {

            setKotReprintError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to queue the KOT reprint."
            );

        } finally {

            setKotReprintLoading(
                false
            );
        }
    }


    return (
        <div
            className="
                fixed
                inset-0
                z-120
                bg-black/40
            "
            onMouseDown={
                event => {

                    if (
                        event.target
                        === event.currentTarget
                        &&
                        !actionLoading
                    ) {

                        onClose();
                    }
                }
            }
        >

            <aside
                role="dialog"
                aria-modal="true"
                aria-label={
                    `Order ${orderNumber}`
                }
                className="
                    absolute
                    inset-y-0
                    right-0
                    flex
                    w-full
                    max-w-2xl
                    flex-col
                    bg-[#fffaf3]
                    shadow-2xl
                "
            >

                {/* HEADER */}

                <div
                    className="
                        shrink-0
                        border-b
                        border-[#eadfd6]
                        bg-white
                        px-5
                        py-5

                        sm:px-6
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
                                    tracking-[0.14em]
                                    text-[#c88a20]
                                "
                            >
                                Order details
                            </p>


                            <h2
                                className="
                                    mt-1
                                    break-all
                                    text-xl
                                    font-bold
                                    text-[#241715]

                                    sm:text-2xl
                                "
                            >
                                {
                                    orderNumber
                                }
                            </h2>

                        </div>


                        <button
                            type="button"
                            aria-label="Close order details"
                            disabled={
                                actionLoading
                            }
                            onClick={
                                onClose
                            }
                            className="
                                flex
                                h-10
                                w-10
                                shrink-0
                                items-center
                                justify-center
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                text-xl
                                font-semibold
                                text-[#756763]

                                hover:bg-[#fff1e9]
                                hover:text-[#7a1625]

                                disabled:cursor-not-allowed
                                disabled:opacity-50
                            "
                        >
                            ×
                        </button>

                    </div>


                    {
                        order
                        && (

                            <div
                                className="
                                    mt-4
                                    flex
                                    flex-wrap
                                    items-center
                                    justify-between
                                    gap-3
                                "
                            >

                                <p
                                    className="
                                        text-xs
                                        text-[#756763]
                                    "
                                >
                                    Order refreshed:{" "}

                                    <span
                                        className="
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatLastUpdated(
                                                lastUpdated
                                            )
                                        }
                                    </span>
                                </p>


                                <button
                                    type="button"
                                    disabled={
                                        refreshing
                                        ||
                                        actionLoading
                                    }
                                    onClick={
                                        () => {

                                            void onRefresh();
                                        }
                                    }
                                    className="
                                        min-h-9
                                        rounded-lg
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-4
                                        text-xs
                                        font-semibold
                                        text-[#7a1625]

                                        hover:bg-[#fff1e9]

                                        disabled:cursor-not-allowed
                                        disabled:opacity-50
                                    "
                                >
                                    {
                                        refreshing
                                            ? "Refreshing..."
                                            : "Refresh Order"
                                    }
                                </button>

                            </div>

                        )
                    }

                </div>


                {/* CONTENT */}

                <div
                    className="
                        flex-1
                        overflow-y-auto
                        p-5

                        sm:p-6
                    "
                >

                    {
                        loading
                        && (

                            <div
                                className="
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-10
                                    text-center
                                    text-sm
                                    font-semibold
                                    text-[#756763]
                                "
                            >
                                Loading order details...
                            </div>

                        )
                    }


                    {
                        error
                        && !loading
                        && (

                            <div
                                role="alert"
                                className="
                                    mb-5
                                    rounded-2xl
                                    border
                                    border-red-200
                                    bg-red-50
                                    px-5
                                    py-4
                                    text-sm
                                    font-medium
                                    text-red-700
                                "
                            >
                                {
                                    error
                                }
                            </div>

                        )
                    }


                    {
                        order
                        && !loading
                        && (

                            <div
                                className="
                                    space-y-5
                                "
                            >

                                {/* STATUS */}

                                <section
                                    className="
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                    "
                                >

                                    <div
                                        className="
                                            flex
                                            flex-wrap
                                            gap-2
                                        "
                                    >

                                        <span
                                            className={[
                                                "rounded-full border px-3 py-1.5 text-xs font-semibold",

                                                statusClasses(
                                                    order.orderStatus
                                                )
                                            ].join(
                                                " "
                                            )}
                                        >
                                            {
                                                formatStatus(
                                                    order.orderStatus
                                                )
                                            }
                                        </span>


                                        {
                                            order.paymentStatus
                                            && (

                                                <span
                                                    className={[
                                                        "rounded-full px-3 py-1.5 text-xs font-semibold",

                                                        paymentStatusClasses(
                                                            order.paymentStatus
                                                        )
                                                    ].join(
                                                        " "
                                                    )}
                                                >
                                                    Payment:{" "}

                                                    {
                                                        formatPaymentStatus(
                                                            order.paymentStatus
                                                        )
                                                    }
                                                </span>

                                            )
                                        }


                                        {
                                            order.adminOverride
                                            && (

                                                <span
                                                    className="
                                                        rounded-full
                                                        border
                                                        border-purple-200
                                                        bg-purple-50
                                                        px-3
                                                        py-1.5
                                                        text-xs
                                                        font-semibold
                                                        text-purple-800
                                                    "
                                                >
                                                    Admin Override
                                                </span>

                                            )
                                        }

                                    </div>

                                </section>


                                {/* ORDER ACTION */}

                                <section
                                    className="
                                        rounded-2xl
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        p-5
                                    "
                                >

                                    <h3
                                        className="
                                            text-sm
                                            font-bold
                                            uppercase
                                            tracking-wide
                                            text-[#7a1625]
                                        "
                                    >
                                        Order action
                                    </h3>


                                    {
                                        actionSuccess
                                        && (

                                            <div
                                                role="status"
                                                className="
                                                    mt-4
                                                    rounded-xl
                                                    border
                                                    border-green-200
                                                    bg-green-50
                                                    px-4
                                                    py-3
                                                    text-sm
                                                    font-semibold
                                                    text-green-800
                                                "
                                            >
                                                ✓ {
                                                    actionSuccess
                                                }
                                            </div>

                                        )
                                    }


                                    {
                                        actionError
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
                                                    actionError
                                                }
                                            </div>

                                        )
                                    }

                                    {
                                        canPrintKot
                                        && order
                                        && (

                                            <div
                                                className="
                                                    mt-4
                                                    rounded-xl
                                                    border
                                                    border-[#eadfd6]
                                                    bg-[#fffaf3]
                                                    p-4
                                                "
                                            >

                                                <div
                                                    className="
                                                        flex
                                                        flex-col
                                                        gap-4
                                                    "
                                                >

                                                    <div>

                                                        <p
                                                            className="
                                                                text-sm
                                                                font-bold
                                                                text-[#241715]
                                                            "
                                                        >
                                                            Kitchen Order Ticket
                                                        </p>


                                                        <p
                                                            className="
                                                                mt-1
                                                                text-xs
                                                                leading-5
                                                                text-[#756763]
                                                            "
                                                        >
                                                            Queue a durable KOT reprint to the branch kitchen printer. Browser printing remains available only as an emergency fallback.
                                                        </p>

                                                    </div>


                                                    {
                                                        kotReprintSuccess
                                                        && (

                                                            <div
                                                                role="status"
                                                                className="
                                                                    rounded-xl
                                                                    border
                                                                    border-green-200
                                                                    bg-green-50
                                                                    px-4
                                                                    py-3
                                                                    text-sm
                                                                    font-semibold
                                                                    text-green-800
                                                                "
                                                            >
                                                                ✓ {
                                                                    kotReprintSuccess
                                                                }
                                                            </div>

                                                        )
                                                    }


                                                    {
                                                        kotReprintError
                                                        && (

                                                            <div
                                                                role="alert"
                                                                className="
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
                                                                    kotReprintError
                                                                }
                                                            </div>

                                                        )
                                                    }


                                                    <div
                                                        className="
                                                            flex
                                                            flex-col
                                                            gap-2

                                                            sm:flex-row
                                                            sm:flex-wrap
                                                        "
                                                    >

                                                        <button
                                                            type="button"
                                                            disabled={
                                                                actionLoading
                                                                ||
                                                                refreshing
                                                                ||
                                                                kotReprintLoading
                                                            }
                                                            onClick={
                                                                () => {

                                                                    void handleKotReprint();
                                                                }
                                                            }
                                                            className="
                                                                min-h-10
                                                                rounded-xl
                                                                bg-[#7a1625]
                                                                px-4
                                                                text-sm
                                                                font-semibold
                                                                text-white

                                                                hover:bg-[#5d0f1b]

                                                                disabled:cursor-not-allowed
                                                                disabled:opacity-50
                                                            "
                                                        >
                                                            {
                                                                kotReprintLoading
                                                                    ? "Queueing Reprint..."
                                                                    : "Queue Reprint"
                                                            }
                                                        </button>


                                                        <button
                                                            type="button"
                                                            disabled={
                                                                actionLoading
                                                                ||
                                                                refreshing
                                                                ||
                                                                kotReprintLoading
                                                            }
                                                            onClick={
                                                                () => {

                                                                    router.push(
                                                                        `/admin/kot/order/${encodeURIComponent(
                                                                            order.orderNumber
                                                                        )}/print`
                                                                    );
                                                                }
                                                            }
                                                            className="
                                                                min-h-10
                                                                rounded-xl
                                                                border
                                                                border-[#7a1625]
                                                                bg-white
                                                                px-4
                                                                text-sm
                                                                font-semibold
                                                                text-[#7a1625]

                                                                hover:bg-[#fff1e9]

                                                                disabled:cursor-not-allowed
                                                                disabled:opacity-50
                                                            "
                                                        >
                                                            Browser Fallback
                                                        </button>

                                                    </div>

                                                </div>

                                            </div>

                                        )
                                    }


                                    {
                                        nextStatus
                                        &&
                                        actionLabel
                                        &&
                                        hasActionPermission
                                        && (

                                            <div
                                                className="
                                                    mt-4
                                                "
                                            >

                                                <p
                                                    className="
                                                        text-sm
                                                        leading-6
                                                        text-[#756763]
                                                    "
                                                >
                                                    Current status:{" "}

                                                    <strong
                                                        className="
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {
                                                            formatStatus(
                                                                order.orderStatus
                                                            )
                                                        }
                                                    </strong>

                                                    {" → "}

                                                    <strong
                                                        className="
                                                            text-[#7a1625]
                                                        "
                                                    >
                                                        {
                                                            formatStatus(
                                                                nextStatus
                                                            )
                                                        }
                                                    </strong>
                                                </p>


                                                <button
                                                    type="button"
                                                    disabled={
                                                        actionLoading
                                                        ||
                                                        refreshing
                                                    }
                                                    onClick={
                                                        () => {

                                                            void onTransition();
                                                        }
                                                    }
                                                    className="
                                                        mt-4
                                                        min-h-12
                                                        w-full
                                                        rounded-xl
                                                        bg-[#7a1625]
                                                        px-5
                                                        text-sm
                                                        font-semibold
                                                        text-white
                                                        transition

                                                        hover:bg-[#5d0f1b]

                                                        disabled:cursor-not-allowed
                                                        disabled:opacity-60
                                                    "
                                                >
                                                    {
                                                        actionLoading
                                                            ? "Updating order..."
                                                            : actionLabel
                                                    }
                                                </button>

                                            </div>

                                        )
                                    }


                                    {
                                        nextStatus
                                        &&
                                        requiredPermission
                                        &&
                                        !hasActionPermission
                                        && (

                                            <div
                                                className="
                                                    mt-4
                                                    rounded-xl
                                                    border
                                                    border-amber-200
                                                    bg-amber-50
                                                    px-4
                                                    py-3
                                                "
                                            >

                                                <p
                                                    className="
                                                        text-sm
                                                        font-semibold
                                                        text-amber-900
                                                    "
                                                >
                                                    Action unavailable
                                                </p>


                                                <p
                                                    className="
                                                        mt-1
                                                        text-sm
                                                        text-amber-800
                                                    "
                                                >
                                                    Your staff account does not have permission to perform the next order action.
                                                </p>

                                            </div>

                                        )
                                    }


                                    {
                                        nextStatus
                                        === null
                                        && (

                                            <div
                                                className="
                                                    mt-4
                                                    rounded-xl
                                                    bg-[#fffaf3]
                                                    px-4
                                                    py-3
                                                "
                                            >

                                                <p
                                                    className="
                                                        text-sm
                                                        font-semibold
                                                        text-[#241715]
                                                    "
                                                >
                                                    {
                                                        order.orderStatus
                                                        === "PICKED_UP"
                                                            ? "Order completed"
                                                            : "No workflow action available"
                                                    }
                                                </p>


                                                <p
                                                    className="
                                                        mt-1
                                                        text-sm
                                                        leading-6
                                                        text-[#756763]
                                                    "
                                                >
                                                    {
                                                        order.orderStatus
                                                        === "PICKED_UP"
                                                            ? "This order has already been collected by the customer."
                                                            : "The current backend workflow does not provide a manual transition from this status."
                                                    }
                                                </p>

                                            </div>

                                        )
                                    }

                                </section>


                                {/* CUSTOMER */}

                                <DetailSection
                                    title="Customer"
                                >

                                    <div
                                        className="
                                            grid
                                            gap-3

                                            sm:grid-cols-2
                                        "
                                    >

                                        <DetailValue
                                            label="Customer name"
                                            value={
                                                order.customerName
                                            }
                                        />


                                        <DetailValue
                                            label="Phone"
                                            value={
                                                order.customerPhone
                                            }
                                        />

                                    </div>

                                </DetailSection>


                                {/* PICKUP */}

                                <DetailSection
                                    title="Pickup"
                                >

                                    <div
                                        className="
                                            grid
                                            gap-3

                                            sm:grid-cols-2
                                        "
                                    >

                                        <DetailValue
                                            label="Branch"
                                            value={
                                                order.branchName
                                            }
                                        />


                                        <DetailValue
                                            label="Branch address"
                                            value={
                                                order.branchAddress
                                            }
                                        />


                                        <DetailValue
                                            label="Pickup date"
                                            value={
                                                order.pickupDate
                                            }
                                        />


                                        <DetailValue
                                            label="Pickup time"
                                            value={
                                                `${order.pickupStartTime} - ${order.pickupEndTime}`
                                            }
                                        />


                                        <DetailValue
                                            label="Pickup type"
                                            value={
                                                formatPickupType(
                                                    order.pickupType
                                                )
                                            }
                                        />

                                    </div>

                                </DetailSection>


                                {/* ITEMS */}

                                <DetailSection
                                    title={
                                        `Items (${order.items.length})`
                                    }
                                >

                                    <div
                                        className="
                                            space-y-3
                                        "
                                    >

                                        {
                                            order.items.map(
                                                item => (

                                                    <div
                                                        key={
                                                            item.id
                                                        }
                                                        className="
                                                            rounded-xl
                                                            border
                                                            border-[#eadfd6]
                                                            bg-[#fffaf3]
                                                            p-4
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
                                                                        item.productName
                                                                    }
                                                                </p>


                                                                <p
                                                                    className="
                                                                        mt-1
                                                                        text-sm
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    {
                                                                        item.quantity
                                                                    } ×{" "}
                                                                    {
                                                                        formatPrice(
                                                                            item.unitPrice
                                                                        )
                                                                    }
                                                                </p>

                                                            </div>


                                                            <p
                                                                className="
                                                                    shrink-0
                                                                    font-bold
                                                                    text-[#7a1625]
                                                                "
                                                            >
                                                                {
                                                                    formatPrice(
                                                                        item.lineTotal
                                                                    )
                                                                }
                                                            </p>

                                                        </div>


                                                        <div
                                                            className="
                                                                mt-3
                                                                flex
                                                                flex-wrap
                                                                gap-x-5
                                                                gap-y-1
                                                                text-xs
                                                                text-[#756763]
                                                            "
                                                        >

                                                            <span>
                                                                Tax rate:{" "}
                                                                {
                                                                    item.taxRate
                                                                }%
                                                            </span>


                                                            <span>
                                                                Tax:{" "}
                                                                {
                                                                    formatPrice(
                                                                        item.taxAmount
                                                                    )
                                                                }
                                                            </span>

                                                        </div>

                                                    </div>

                                                )
                                            )
                                        }

                                    </div>

                                </DetailSection>


                                {/* PAYMENT SUMMARY */}

                                <DetailSection
                                    title="Payment summary"
                                >

                                    <div
                                        className="
                                            space-y-3
                                        "
                                    >

                                        <AmountRow
                                            label="Subtotal"
                                            value={
                                                order.subtotal
                                            }
                                        />


                                        <AmountRow
                                            label="Tax"
                                            value={
                                                order.taxAmount
                                            }
                                        />


                                        {
                                            order.priorityCharge
                                            > 0
                                            && (

                                                <AmountRow
                                                    label="Priority charge"
                                                    value={
                                                        order.priorityCharge
                                                    }
                                                />

                                            )
                                        }


                                        <div
                                            className="
                                                border-t
                                                border-[#eadfd6]
                                                pt-3
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
                                                        font-bold
                                                        text-[#241715]
                                                    "
                                                >
                                                    Total
                                                </span>


                                                <span
                                                    className="
                                                        text-lg
                                                        font-bold
                                                        text-[#7a1625]
                                                    "
                                                >
                                                    {
                                                        formatPrice(
                                                            order.totalAmount
                                                        )
                                                    }
                                                </span>

                                            </div>

                                        </div>

                                    </div>

                                </DetailSection>


                                {/* ADMIN OVERRIDE */}

                                {
                                    order.adminOverride
                                    && (

                                        <DetailSection
                                            title="Admin override"
                                        >

                                            <DetailValue
                                                label="Reason"
                                                value={
                                                    order.overrideReason
                                                    ?? "No reason recorded."
                                                }
                                            />

                                        </DetailSection>

                                    )
                                }


                                {/* HISTORY */}

                                <DetailSection
                                    title="Order history"
                                >

                                    <div
                                        className="
                                            grid
                                            gap-3

                                            sm:grid-cols-2
                                        "
                                    >

                                        <DetailValue
                                            label="Created"
                                            value={
                                                formatCreatedAt(
                                                    order.createdAt
                                                )
                                            }
                                        />


                                        <DetailValue
                                            label="Last updated"
                                            value={
                                                formatCreatedAt(
                                                    order.updatedAt
                                                )
                                            }
                                        />

                                    </div>

                                </DetailSection>

                            </div>

                        )
                    }

                </div>

            </aside>

        </div>
    );
}


function DetailSection({
    title,
    children
}: {
    title: string;

    children: React.ReactNode;
}) {

    return (
        <section
            className="
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                p-5
            "
        >

            <h3
                className="
                    text-sm
                    font-bold
                    uppercase
                    tracking-wide
                    text-[#7a1625]
                "
            >
                {
                    title
                }
            </h3>


            <div
                className="
                    mt-4
                "
            >
                {
                    children
                }
            </div>

        </section>
    );
}


function DetailValue({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div
            className="
                rounded-xl
                bg-[#fffaf3]
                px-4
                py-3
            "
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    text-[#9a8983]
                "
            >
                {
                    label
                }
            </p>


            <p
                className="
                    mt-1
                    wrap-break-word
                    text-sm
                    font-semibold
                    text-[#241715]
                "
            >
                {
                    value
                }
            </p>

        </div>
    );
}


function AmountRow({
    label,
    value
}: {
    label: string;
    value: number;
}) {

    return (
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
                {
                    label
                }
            </span>


            <span
                className="
                    font-semibold
                    text-[#241715]
                "
            >
                {
                    formatPrice(
                        value
                    )
                }
            </span>

        </div>
    );
}


function InfoItem({
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
                rounded-xl
                bg-[#fffaf3]
                px-4
                py-3
            "
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-wide
                    text-[#9a8983]
                "
            >
                {
                    label
                }
            </p>


            <p
                className={[
                    "mt-1 text-sm",

                    strong
                        ? "font-bold text-[#7a1625]"
                        : "font-semibold text-[#241715]"
                ].join(
                    " "
                )}
            >
                {
                    value
                }
            </p>

        </div>
    );
}