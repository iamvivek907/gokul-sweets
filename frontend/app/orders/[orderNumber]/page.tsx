"use client";

import {
    useCallback,
    useEffect,
    useMemo,
    useState
} from "react";

import {
    useParams,
    useRouter
} from "next/navigation";

import AppShell
    from "@/components/layout/AppShell";
import {formatBusinessTimestamp} from "@/lib/businessTime";

import OrderReviewCard
    from "@/components/order/OrderReviewCard";

import {
    formatOrderCurrency,
    formatOrderDate,
    formatOrderTime,
    getOrderStatusPresentation,
    getStatusClasses,
    isActiveOrderStatus
} from "@/lib/orderTracking";

import {
    getCustomerOrder
} from "@/services/orderApi";

import type {
    CustomerOrderResponse,
    OrderStatus
} from "@/types/order";


interface LoadedOrder {
    orderNumber: string;
    order: CustomerOrderResponse | null;
    error: string | null;
}


interface TimelineStep {
    status: OrderStatus;
    label: string;
    description: string;
}


const TIMELINE_STEPS: TimelineStep[] = [
    {
        status: "CONFIRMED",
        label: "Confirmed",
        description: "The shop has received your order."
    },
    {
        status: "PREPARING",
        label: "Preparing",
        description: "Your sweets and food are being freshly prepared."
    },
    {
        status: "READY_FOR_PICKUP",
        label: "Ready for pickup",
        description: "Please collect your order from the selected branch."
    },
    {
        status: "PICKED_UP",
        label: "Picked up",
        description: "Your order has been collected."
    }
];


const TIMELINE_INDEX:
    Partial<Record<OrderStatus, number>> = {
        CONFIRMED: 0,
        PREPARING: 1,
        READY_FOR_PICKUP: 2,
        PICKED_UP: 3
    };


function formatUpdatedAt(value: string): string {
    return formatBusinessTimestamp(value, {
        day: "numeric", month: "short", year: "numeric",
        hour: "numeric", minute: "2-digit"
    });
}


export default function OrderDetailPage() {

    const router = useRouter();

    const params =
        useParams<{
            orderNumber: string;
        }>();

    const orderNumber =
        decodeURIComponent(params.orderNumber);

    const [loadedOrder, setLoadedOrder] =
        useState<LoadedOrder | null>(null);

    const [refreshing, setRefreshing] =
        useState(false);

    const currentOrder =
        loadedOrder?.orderNumber === orderNumber
            ? loadedOrder
            : null;

    const order = currentOrder?.order ?? null;

    const fetchOrder =
        useCallback(
            async (
                signal?: AbortSignal
            ): Promise<LoadedOrder | null> => {
                try {
                    const response =
                        await getCustomerOrder(
                            orderNumber,
                            signal
                        );

                    if (signal?.aborted) {
                        return null;
                    }

                    return {
                        orderNumber,
                        order: response,
                        error: null
                    };

                } catch (exception) {

                    if (signal?.aborted) {
                        return null;
                    }

                    console.error(
                        "Unable to load order:",
                        exception
                    );

                    return {
                        orderNumber,
                        order: null,
                        error:
                            exception instanceof Error
                                ? exception.message
                                : "Unable to load this order."
                    };
                }
            },
            [orderNumber]
        );

    async function handleRefresh(): Promise<void> {

        if (refreshing) {
            return;
        }

        setRefreshing(true);

        try {
            const result =
                await fetchOrder();

            if (result) {
                setLoadedOrder(result);
            }
        } finally {
            setRefreshing(false);
        }
    }

    useEffect(() => {
        const controller = new AbortController();

        void fetchOrder(controller.signal)
            .then(result => {

                if (
                    result
                    &&
                    !controller.signal.aborted
                ) {
                    setLoadedOrder(result);
                }
            });

        return () => {
            controller.abort();
        };
    }, [fetchOrder]);

    const shouldPoll =
        order !== null
        &&
        isActiveOrderStatus(order.orderStatus)
        &&
        order.orderStatus !== "PENDING_PAYMENT";

    useEffect(() => {

        if (!shouldPoll) {
            return;
        }

        const timer = window.setInterval(
            () => {
                void fetchOrder()
                    .then(result => {

                        if (result) {
                            setLoadedOrder(result);
                        }
                    });
            },
            15000
        );

        return () => {
            window.clearInterval(timer);
        };

    }, [fetchOrder, shouldPoll]);

    const currentTimelineIndex =
        order
            ? TIMELINE_INDEX[order.orderStatus] ?? -1
            : -1;

    const status =
        useMemo(
            () =>
                order
                    ? getOrderStatusPresentation(order.orderStatus)
                    : null,
            [order]
        );

    if (!currentOrder) {
        return (
            <AppShell>
                <section className="mx-auto max-w-2xl px-4 pb-28 pt-5 sm:px-6">
                    <div className="rounded-3xl border border-[#eadfd6] bg-white py-16 text-center">
                        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-[#eadfd6] border-t-[#7a1625]" />
                        <p className="mt-4 font-semibold text-[#241715]">Loading your order...</p>
                    </div>
                </section>
            </AppShell>
        );
    }

    if (currentOrder.error || !order || !status) {
        return (
            <AppShell>
                <section className="mx-auto max-w-xl px-4 pb-28 pt-5 sm:px-6">
                    <div className="rounded-3xl border border-red-200 bg-white p-8 text-center">
                        <h1 className="text-xl font-bold text-[#241715]">Unable to load order</h1>
                        <p className="mt-2 text-sm text-red-700">
                            {currentOrder.error ?? "Order was not found."}
                        </p>
                        <div className="mt-6 flex flex-col gap-3 sm:flex-row">
                            <button
                                type="button"
                                onClick={() => router.push("/orders")}
                                className="min-h-11 flex-1 rounded-xl border border-[#eadfd6] font-bold text-[#7a1625]"
                            >
                                My Orders
                            </button>
                            <button
                                type="button"
                                disabled={refreshing}
                                onClick={() => void handleRefresh()}
                                className="min-h-11 flex-1 rounded-xl bg-[#7a1625] font-bold text-white! disabled:opacity-50"
                            >
                                {refreshing ? "Trying again..." : "Try again"}
                            </button>
                        </div>
                    </div>
                </section>
            </AppShell>
        );
    }

    const showOperationalTimeline =
        [
            "CONFIRMED",
            "PREPARING",
            "READY_FOR_PICKUP",
            "PICKED_UP"
        ].includes(order.orderStatus);

    return (
        <AppShell>
            <section className="mx-auto w-full max-w-3xl px-4 pb-28 pt-5 sm:px-6 sm:pt-7">

                <button
                    type="button"
                    onClick={() => router.push("/orders")}
                    className="mb-4 text-sm font-bold text-[#7a1625]"
                >
                    ← My Orders
                </button>

                <div className="rounded-3xl border border-[#eadfd6] bg-white p-5 shadow-sm sm:p-7">
                    <div className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
                        <div className="min-w-0">
                            <p className="text-xs font-bold uppercase tracking-wide text-[#c88a20]">Order tracking</p>
                            <h1 className="mt-2 break-all text-xl font-bold text-[#241715] sm:text-2xl">
                                {order.orderNumber}
                            </h1>
                            <p className="mt-2 text-xs text-[#756763]">
                                Last updated {formatUpdatedAt(order.updatedAt)}
                            </p>
                        </div>

                        <span className={`w-fit rounded-full border px-4 py-2 text-xs font-bold ${getStatusClasses(status.tone)}`}>
                            {status.label}
                        </span>
                    </div>

                    <div className={`mt-5 rounded-2xl border p-4 ${getStatusClasses(status.tone)}`}>
                        <p className="font-bold">{status.label}</p>
                        <p className="mt-1 text-sm leading-6">{status.message}</p>
                    </div>

                    {showOperationalTimeline && (
                        <div className="mt-7">
                            <h2 className="text-lg font-bold text-[#241715]">Order progress</h2>
                            <div className="mt-5 space-y-0">
                                {TIMELINE_STEPS.map((step, index) => {
                                    const completed = index < currentTimelineIndex || order.orderStatus === "PICKED_UP";
                                    const current = index === currentTimelineIndex && order.orderStatus !== "PICKED_UP";

                                    return (
                                        <div key={step.status} className="relative flex gap-4 pb-7 last:pb-0">
                                            {index < TIMELINE_STEPS.length - 1 && (
                                                <div className={`absolute left-[15px] top-8 h-[calc(100%-1rem)] w-0.5 ${index < currentTimelineIndex ? "bg-green-500" : "bg-[#eadfd6]"}`} />
                                            )}
                                            <div className={`relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-full border-2 text-xs font-bold ${
                                                completed
                                                    ? "border-green-600 bg-green-600 text-white!"
                                                    : current
                                                        ? "border-[#7a1625] bg-[#fff4e5] text-[#7a1625]"
                                                        : "border-[#d8cbc3] bg-white text-[#9b8b84]"
                                            }`}>
                                                {completed ? "✓" : index + 1}
                                            </div>
                                            <div className="pt-1">
                                                <p className={`text-sm font-bold ${completed || current ? "text-[#241715]" : "text-[#9b8b84]"}`}>
                                                    {step.label}
                                                </p>
                                                <p className="mt-1 text-xs leading-5 text-[#756763]">{step.description}</p>
                                            </div>
                                        </div>
                                    );
                                })}
                            </div>
                        </div>
                    )}

                    <div className="mt-7 grid gap-4 border-t border-[#eadfd6] pt-6 sm:grid-cols-2">
                        <div className="rounded-2xl bg-[#fffaf3] p-4">
                            <p className="text-xs font-bold uppercase tracking-wide text-[#756763]">Pickup from</p>
                            <p className="mt-2 font-bold text-[#241715]">{order.branchName}</p>
                            <p className="mt-1 text-sm leading-6 text-[#756763]">{order.branchAddress}</p>
                        </div>
                        <div className="rounded-2xl bg-[#fffaf3] p-4">
                            <p className="text-xs font-bold uppercase tracking-wide text-[#756763]">Pickup time</p>
                            <p className="mt-2 font-bold text-[#241715]">{formatOrderDate(order.pickupDate)}</p>
                            <p className="mt-1 text-sm text-[#756763]">
                                {formatOrderTime(order.pickupStartTime)} – {formatOrderTime(order.pickupEndTime)}
                            </p>
                            <p className="mt-2 text-xs font-semibold text-[#7a1625]">
                                {order.pickupType === "PRIORITY" ? "Priority pickup" : "Normal pickup"}
                            </p>
                        </div>
                    </div>

                    <div className="mt-7 border-t border-[#eadfd6] pt-6">
                        <h2 className="text-lg font-bold text-[#241715]">Items</h2>
                        <div className="mt-3 divide-y divide-[#eadfd6]">
                            {order.items.map(item => (
                                <div key={item.id} className="flex items-start justify-between gap-4 py-4">
                                    <div>
                                        <p className="font-semibold text-[#241715]">{item.productName}</p>
                                        <p className="mt-1 text-xs text-[#756763]">
                                            {item.saleMode === "WEIGHT"
                                                ? `${item.weightGrams ?? 0} g × ${formatOrderCurrency(item.unitPrice)}/kg`
                                                : `${item.quantity} × ${formatOrderCurrency(item.unitPrice)}`}
                                        </p>
                                    </div>
                                    <p className="shrink-0 font-semibold text-[#241715]">
                                        {formatOrderCurrency(item.lineTotal)}
                                    </p>
                                </div>
                            ))}
                        </div>
                    </div>

                    <div className="mt-6 rounded-2xl bg-[#fffaf3] p-4">
                        <div className="flex justify-between text-sm text-[#756763]">
                            <span>Subtotal</span>
                            <span className="font-semibold text-[#241715]">{formatOrderCurrency(order.subtotal)}</span>
                        </div>
                        <div className="mt-2 flex justify-between text-sm text-[#756763]">
                            <span>Tax</span>
                            <span className="font-semibold text-[#241715]">{formatOrderCurrency(order.taxAmount)}</span>
                        </div>
                        {order.priorityCharge > 0 && (
                            <div className="mt-2 flex justify-between text-sm text-[#756763]">
                                <span>Priority charge</span>
                                <span className="font-semibold text-[#241715]">{formatOrderCurrency(order.priorityCharge)}</span>
                            </div>
                        )}
                        <div className="mt-4 flex justify-between border-t border-[#eadfd6] pt-4 text-lg font-bold text-[#241715]">
                            <span>Total</span>
                            <span>{formatOrderCurrency(order.totalAmount)}</span>
                        </div>
                    </div>

                    {order.orderStatus === "PICKED_UP" && (
                        <OrderReviewCard
                            orderNumber={order.orderNumber}
                        />
                    )}

                    <div className="mt-6 flex flex-col gap-3 sm:flex-row">
                        <button
                            type="button"
                            disabled={refreshing}
                            onClick={() => void handleRefresh()}
                            className="min-h-12 flex-1 rounded-xl border border-[#eadfd6] bg-white px-4 font-bold text-[#7a1625] disabled:opacity-50"
                        >
                            {refreshing ? "Refreshing..." : "Refresh Status"}
                        </button>
                        <button
                            type="button"
                            onClick={() => router.push("/menu")}
                            className="min-h-12 flex-1 rounded-xl bg-[#7a1625] px-4 font-bold text-white!"
                        >
                            Browse Menu
                        </button>
                    </div>
                </div>
            </section>
        </AppShell>
    );
}
