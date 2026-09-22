"use client";

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

import {
    getOrderHistorySnapshot,
    getServerOrderHistorySnapshot,
    parseOrderHistory,
    subscribeToOrderHistory
} from "@/lib/orderHistoryStorage";

import {
    formatOrderCurrency,
    formatOrderDate,
    formatOrderTime,
    getOrderMonthLabel,
    getOrderStatusPresentation,
    getStatusClasses,
    matchesOrderFilter
} from "@/lib/orderTracking";

import {
    getCustomerOrderHistory
} from "@/services/orderApi";

import type {
    OrderHistoryFilter
} from "@/lib/orderTracking";

import type {
    CustomerOrderSummaryResponse
} from "@/types/order";


const PAGE_SIZE = 12;
const MAX_HISTORY_ORDERS = 500;


interface LoadedHistory {
    requestKey: string;
    orders: CustomerOrderSummaryResponse[];
    error: string | null;
}


const FILTERS: Array<{
    value: OrderHistoryFilter;
    label: string;
}> = [
    { value: "ALL", label: "All" },
    { value: "ACTIVE", label: "Active" },
    { value: "COMPLETED", label: "Completed" },
    { value: "ISSUES", label: "Cancelled / Failed" }
];


export default function OrdersPage() {

    const router = useRouter();

    const historySnapshot =
        useSyncExternalStore(
            subscribeToOrderHistory,
            getOrderHistorySnapshot,
            getServerOrderHistorySnapshot
        );

    const history =
        useMemo(
            () =>
                parseOrderHistory(historySnapshot),
            [historySnapshot]
        );

    const orderNumbers =
        useMemo(
            () =>
                history
                    .slice(0, MAX_HISTORY_ORDERS)
                    .map(entry => entry.orderNumber),
            [history]
        );

    const [reloadVersion, setReloadVersion] =
        useState(0);

    const requestKey =
        useMemo(
            () =>
                `${reloadVersion}:${orderNumbers.join("|")}`,
            [orderNumbers, reloadVersion]
        );

    const [loadedHistory, setLoadedHistory] =
        useState<LoadedHistory | null>(null);

    const [query, setQuery] =
        useState("");

    const [filter, setFilter] =
        useState<OrderHistoryFilter>("ALL");

    const [page, setPage] =
        useState(1);

    useEffect(() => {

        if (orderNumbers.length === 0) {
            return;
        }

        const controller = new AbortController();
        const activeRequestKey = requestKey;

        async function loadHistory(): Promise<void> {

            try {
                const orders =
                    await getCustomerOrderHistory(
                        orderNumbers,
                        controller.signal
                    );

                if (controller.signal.aborted) {
                    return;
                }

                setLoadedHistory({
                    requestKey: activeRequestKey,
                    orders,
                    error: null
                });

            } catch (exception) {

                if (controller.signal.aborted) {
                    return;
                }

                console.error(
                    "Unable to load order history:",
                    exception
                );

                setLoadedHistory({
                    requestKey: activeRequestKey,
                    orders: [],
                    error:
                        exception instanceof Error
                            ? exception.message
                            : "Unable to load your orders."
                });
            }
        }

        void loadHistory();

        return () => {
            controller.abort();
        };

    }, [orderNumbers, requestKey]);

    const currentHistory =
        loadedHistory?.requestKey === requestKey
            ? loadedHistory
            : null;

    const loading =
        orderNumbers.length > 0
        &&
        currentHistory === null;

    const normalizedQuery =
        query.trim().toLowerCase();

    const filteredOrders =
        useMemo(
            () =>
                (currentHistory?.orders ?? [])
                    .filter(order =>
                        matchesOrderFilter(order, filter)
                    )
                    .filter(order => {

                        if (!normalizedQuery) {
                            return true;
                        }

                        return (
                            order.orderNumber
                                .toLowerCase()
                                .includes(normalizedQuery)
                            ||
                            order.branchName
                                .toLowerCase()
                                .includes(normalizedQuery)
                        );
                    }),
            [currentHistory, filter, normalizedQuery]
        );

    const totalPages =
        Math.max(
            1,
            Math.ceil(filteredOrders.length / PAGE_SIZE)
        );

    const safePage =
        Math.min(page, totalPages);

    const visibleOrders =
        filteredOrders.slice(
            (safePage - 1) * PAGE_SIZE,
            safePage * PAGE_SIZE
        );

    const groupedOrders =
        useMemo(() => {

            const groups =
                new Map<string, CustomerOrderSummaryResponse[]>();

            for (const order of visibleOrders) {
                const label = getOrderMonthLabel(order.createdAt);
                const existing = groups.get(label) ?? [];
                existing.push(order);
                groups.set(label, existing);
            }

            return Array.from(groups.entries());

        }, [visibleOrders]);

    function changeFilter(value: OrderHistoryFilter): void {
        setFilter(value);
        setPage(1);
    }

    function changeQuery(value: string): void {
        setQuery(value);
        setPage(1);
    }

    return (
        <AppShell>
            <section className="mx-auto w-full max-w-5xl px-4 pb-28 pt-5 sm:px-6 sm:pt-7">

                <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[#c88a20]">
                            Order history
                        </p>
                        <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                            My Orders
                        </h1>
                        <p className="mt-2 max-w-xl text-sm leading-6 text-[#756763]">
                            Track active pickups and find older completed, cancelled or failed orders.
                        </p>
                    </div>

                    {orderNumbers.length > 0 && (
                        <button
                            type="button"
                            disabled={loading}
                            onClick={() => setReloadVersion(value => value + 1)}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-5 text-sm font-bold text-[#7a1625] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {loading ? "Refreshing..." : "Refresh"}
                        </button>
                    )}
                </div>

                {history.length > MAX_HISTORY_ORDERS && (
                    <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                        Showing your latest {MAX_HISTORY_ORDERS} orders.
                    </div>
                )}

                {orderNumbers.length > 0 && (
                    <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4 shadow-sm">
                        <label htmlFor="order-search" className="text-xs font-bold uppercase tracking-wide text-[#756763]">
                            Find an order
                        </label>
                        <input
                            id="order-search"
                            type="search"
                            value={query}
                            onChange={event => changeQuery(event.target.value)}
                            placeholder="Search order number or branch"
                            className="mt-2 min-h-12 w-full rounded-xl border border-[#eadfd6] bg-[#fffaf3] px-4 text-sm text-[#241715] outline-none focus:border-[#7a1625] focus:ring-2 focus:ring-[#7a1625]/10"
                        />

                        <div className="mt-4 flex gap-2 overflow-x-auto pb-1" role="tablist" aria-label="Filter orders">
                            {FILTERS.map(option => (
                                <button
                                    key={option.value}
                                    type="button"
                                    role="tab"
                                    aria-selected={filter === option.value}
                                    onClick={() => changeFilter(option.value)}
                                    className={`shrink-0 rounded-full border px-4 py-2 text-xs font-bold transition ${
                                        filter === option.value
                                            ? "border-[#7a1625] bg-[#7a1625] text-white!"
                                            : "border-[#eadfd6] bg-white text-[#756763]"
                                    }`}
                                >
                                    {option.label}
                                </button>
                            ))}
                        </div>
                    </div>
                )}

                {loading && (
                    <div className="mt-8 rounded-3xl border border-[#eadfd6] bg-white py-16 text-center">
                        <div className="mx-auto h-10 w-10 animate-spin rounded-full border-4 border-[#eadfd6] border-t-[#7a1625]" />
                        <p className="mt-4 font-semibold text-[#241715]">Loading your orders...</p>
                    </div>
                )}

                {!loading && currentHistory?.error && (
                    <div className="mt-6 rounded-2xl border border-red-200 bg-red-50 p-5">
                        <p className="font-bold text-red-800">Unable to load orders</p>
                        <p className="mt-1 text-sm text-red-700">{currentHistory.error}</p>
                        <button
                            type="button"
                            onClick={() => setReloadVersion(value => value + 1)}
                            className="mt-4 text-sm font-bold text-[#7a1625]"
                        >
                            Try again
                        </button>
                    </div>
                )}

                {!loading && orderNumbers.length === 0 && (
                    <div className="mt-8 rounded-3xl border border-[#eadfd6] bg-white px-6 py-16 text-center">
                        <h2 className="text-xl font-bold text-[#241715]">No orders yet</h2>
                        <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-[#756763]">
                            Orders created from this browser will appear here.
                        </p>
                        <button
                            type="button"
                            onClick={() => router.push("/menu")}
                            className="mt-6 min-h-11 rounded-xl bg-[#7a1625] px-6 font-bold text-white!"
                        >
                            Browse Menu
                        </button>
                    </div>
                )}

                {!loading && currentHistory && !currentHistory.error && filteredOrders.length === 0 && orderNumbers.length > 0 && (
                    <div className="mt-8 rounded-3xl border border-[#eadfd6] bg-white px-6 py-14 text-center">
                        <h2 className="text-lg font-bold text-[#241715]">No matching orders</h2>
                        <p className="mt-2 text-sm text-[#756763]">Try another status or search term.</p>
                    </div>
                )}

                {!loading && groupedOrders.map(([month, monthOrders]) => (
                    <section key={month} className="mt-8">
                        <div className="flex items-center justify-between gap-4">
                            <h2 className="text-lg font-bold text-[#241715]">{month}</h2>
                            <span className="text-xs font-semibold text-[#756763]">
                                {monthOrders.length} {monthOrders.length === 1 ? "order" : "orders"}
                            </span>
                        </div>

                        <div className="mt-3 grid gap-4 md:grid-cols-2">
                            {monthOrders.map(order => {
                                const status = getOrderStatusPresentation(order.orderStatus);

                                return (
                                    <article
                                        key={order.orderNumber}
                                        className="rounded-2xl border border-[#eadfd6] bg-white p-5 shadow-sm"
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="min-w-0">
                                                <p className="text-[10px] font-bold uppercase tracking-wide text-[#756763]">Order</p>
                                                <p className="mt-1 break-all text-sm font-bold text-[#241715]">{order.orderNumber}</p>
                                            </div>
                                            <span className={`shrink-0 rounded-full border px-3 py-1 text-[11px] font-bold ${getStatusClasses(status.tone)}`}>
                                                {status.label}
                                            </span>
                                        </div>

                                        <p className="mt-3 text-xs leading-5 text-[#756763]">{status.message}</p>

                                        <div className="mt-4 grid grid-cols-2 gap-4 rounded-xl bg-[#fffaf3] p-4 text-sm">
                                            <div>
                                                <p className="text-xs text-[#756763]">Pickup</p>
                                                <p className="mt-1 font-semibold text-[#241715]">{formatOrderDate(order.pickupDate)}</p>
                                                <p className="mt-1 text-xs text-[#756763]">
                                                    {formatOrderTime(order.pickupStartTime)} – {formatOrderTime(order.pickupEndTime)}
                                                </p>
                                            </div>
                                            <div>
                                                <p className="text-xs text-[#756763]">Total</p>
                                                <p className="mt-1 font-bold text-[#241715]">{formatOrderCurrency(order.totalAmount)}</p>
                                                <p className="mt-1 truncate text-xs text-[#756763]">{order.branchName}</p>
                                            </div>
                                        </div>

                                        <button
                                            type="button"
                                            onClick={() => router.push(`/orders/${encodeURIComponent(order.orderNumber)}`)}
                                            className="mt-4 flex min-h-11 w-full items-center justify-center rounded-xl bg-[#7a1625] px-4 text-sm font-bold text-white!"
                                        >
                                            View Order
                                        </button>
                                    </article>
                                );
                            })}
                        </div>
                    </section>
                ))}

                {!loading && filteredOrders.length > PAGE_SIZE && (
                    <nav className="mt-8 flex items-center justify-between gap-4 rounded-2xl border border-[#eadfd6] bg-white p-4" aria-label="Order history pages">
                        <button
                            type="button"
                            disabled={safePage <= 1}
                            onClick={() => setPage(value => Math.max(1, value - 1))}
                            className="min-h-10 rounded-xl border border-[#eadfd6] px-4 text-sm font-bold text-[#7a1625] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Previous
                        </button>
                        <p className="text-xs font-semibold text-[#756763]">
                            Page {safePage} of {totalPages} · {filteredOrders.length} orders
                        </p>
                        <button
                            type="button"
                            disabled={safePage >= totalPages}
                            onClick={() => setPage(value => Math.min(totalPages, value + 1))}
                            className="min-h-10 rounded-xl border border-[#eadfd6] px-4 text-sm font-bold text-[#7a1625] disabled:cursor-not-allowed disabled:opacity-40"
                        >
                            Next
                        </button>
                    </nav>
                )}

            </section>
        </AppShell>
    );
}
