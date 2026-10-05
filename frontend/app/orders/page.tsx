"use client";
import OrderRatingLink from "@/components/order/OrderRatingLink";
import {orderDisplayNumber} from "@/lib/orderDisplayNumber";
import {T,useTranslation} from "@/lib/language";

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
import {formatBusinessTimestamp} from "@/lib/businessTime";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import Link from "next/link";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";

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
    getCustomerOrderHistory,
    getVerifiedCustomerOrders
} from "@/services/orderApi";
import {apiClient} from "@/services/apiClient";

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
    const translate = useTranslation();

    const phone = usePhoneViewport();
    const features = useStorefrontFeatures();
    const compact = phone && features?.simplifiedCheckout && features.checkoutExperienceV2 && features.acceptedCheckoutQuote;
    const trackingEnabled = features?.truthfulOrderTracking === true;

    const router = useRouter();
    const {branch} = useSelectedBranch();

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

    useEffect(() => {
        const refresh = () => setReloadVersion(value => value + 1);
        window.addEventListener("gokul-customer-identity-changed", refresh);
        return () => window.removeEventListener("gokul-customer-identity-changed", refresh);
    }, []);

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

        const controller = new AbortController();
        const activeRequestKey = requestKey;

        async function loadHistory(): Promise<void> {

            try {
                const localOrders = orderNumbers.length > 0
                    ? await getCustomerOrderHistory(orderNumbers, controller.signal)
                    : [];
                let verifiedOrders: CustomerOrderSummaryResponse[] = [];
                try {
                    const availability = await apiClient<{enabled: boolean}>("/api/storefront/customer-identity", {
                        signal: controller.signal
                    });
                    if (availability.enabled) {
                        const session = await apiClient<{authenticated: boolean}>("/api/customer/identity/me", {
                            credentials: "include", signal: controller.signal
                        });
                        if (session.authenticated) verifiedOrders = await getVerifiedCustomerOrders(controller.signal);
                    }
                } catch (identityError) {
                    if (controller.signal.aborted) return;
                    console.error("Unable to recover verified orders:", identityError);
                }

                const orders = Array.from(new Map([...localOrders, ...verifiedOrders]
                    .map(order => [order.orderNumber, order])).values())
                    .sort((left, right) => right.createdAt.localeCompare(left.createdAt));

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

    const loading = currentHistory === null;
    const branchOrders = useMemo(() => (currentHistory?.orders ?? [])
        .filter(order => branch !== null && order.branchId === branch.id), [currentHistory, branch]);
    const hasOrders = branchOrders.length > 0;

    const normalizedQuery =
        query.trim().toLowerCase();

    const filteredOrders =
        useMemo(
            () =>
                branchOrders
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
                            orderDisplayNumber(order)
                                .toLowerCase()
                                .includes(normalizedQuery)
                            ||
                            order.branchName
                                .toLowerCase()
                                .includes(normalizedQuery)
                        );
                    }),
            [branchOrders, filter, normalizedQuery]
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
            <section className={`${compact ? "mobile-orders-list " : ""}mx-auto w-full max-w-5xl px-4 pb-28 pt-5 sm:px-6 sm:pt-7`}>

                <div className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
                    <div>
                        <p className="text-xs font-semibold uppercase tracking-[0.14em] text-[#c88a20]">
                            Order history
                        </p>
                        <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                            <T text="My Orders" /></h1>
                        <p className="mt-2 max-w-xl text-sm leading-6 text-[#756763]">
                            {branch ? `Orders from ${branch.name}. Your Profile shows orders from every branch.`
                                : "Select a branch to see its orders. Your Profile shows orders from every branch."}
                        </p>
                    </div>

                    {hasOrders && (
                        <button
                            type="button"
                            disabled={loading}
                            onClick={() => setReloadVersion(value => value + 1)}
                            className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-5 text-sm font-bold text-[#7a1625] disabled:cursor-not-allowed disabled:opacity-50"
                        >
                            {loading ? "Refreshing..." : translate("Refresh")}
                        </button>
                    )}
                </div>

                {history.length > MAX_HISTORY_ORDERS && (
                    <div className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-800">
                        Checking your latest {MAX_HISTORY_ORDERS} orders for this branch.
                    </div>
                )}

                {hasOrders && (
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
                            <T text="Try again" /></button>
                    </div>
                )}

                {!loading && !hasOrders && !currentHistory?.error && (
                    <div className="mt-8 rounded-3xl border border-[#eadfd6] bg-white px-6 py-16 text-center">
                        <h2 className="text-xl font-bold text-[#241715]"><T text="No orders yet" /></h2>
                        <p className="mx-auto mt-2 max-w-md text-sm leading-6 text-[#756763]">
                            {branch ? `No orders from ${branch.name} yet. Visit Profile to see orders from all branches.`
                                : "Choose a branch to see its orders. Visit Profile for all branches."}
                        </p>
                        <button type="button" onClick={() => router.push("/profile#account-orders")}
                            className="mt-5 min-h-11 rounded-xl border border-[#eadfd6] px-5 text-sm font-bold text-[#7a1625]">
                            View all branches in Profile
                        </button>
                        <button
                            type="button"
                            onClick={() => router.push("/menu")}
                            className="mt-6 min-h-11 rounded-xl bg-[#7a1625] px-6 font-bold text-white!"
                        >
                            Browse Menu
                        </button>
                    </div>
                )}

                {!loading && currentHistory && !currentHistory.error && filteredOrders.length === 0 && hasOrders && (
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
                                const status = getOrderStatusPresentation(order.orderStatus, order.fulfillmentType);

                                if (compact) return <article className="mobile-order-card" data-order-tone={status.tone} key={order.orderNumber}>
                                    <div className="mobile-order-card-heading"><strong>{order.branchName}</strong><Link href="/menu">View menu</Link></div>
                                    <p>{formatBusinessTimestamp(order.createdAt, {day: "numeric", month: "short", hour: "numeric", minute: "2-digit"})} IST</p>
                                    <p>{order.fulfillmentType === "DELIVERY" ? "Delivery" : "Pickup"} · {order.pickupDate ? formatOrderDate(order.pickupDate) : order.deliveryDate ? formatOrderDate(order.deliveryDate) : "Window pending"}</p>
                                    <div><span className={getStatusClasses(status.tone)}>{status.label}</span><strong>{formatOrderCurrency(order.totalAmount)}</strong></div>
                                    {trackingEnabled && order.delayReportedAt && <p>Ready time updated — open for details.</p>}
                                    {["PICKED_UP","DELIVERED"].includes(order.orderStatus)&&<OrderRatingLink orderNumber={order.orderNumber}/>}
                                    <Link className="mobile-order-open" href={`/orders/${encodeURIComponent(order.orderNumber)}`}><span>View order</span>{" "}<span data-copyable>{orderDisplayNumber(order)}</span></Link>
                                </article>;
                                return (
                                    <article
                                        key={order.orderNumber}
                                        className="rounded-2xl border border-[#eadfd6] bg-white p-5 shadow-sm"
                                    >
                                        <div className="flex items-start justify-between gap-3">
                                            <div className="min-w-0">
                                                <p className="text-[10px] font-bold uppercase tracking-wide text-[#756763]"><T text="Order" /></p>
                                                <p className="mt-1 break-all text-sm font-bold text-[#241715]">{orderDisplayNumber(order)}</p>
                                            </div>
                                            <span className={`shrink-0 rounded-full border px-3 py-1 text-[11px] font-bold ${getStatusClasses(status.tone)}`}>
                                                {status.label}
                                            </span>
                                        </div>

                                        <p className="mt-3 text-xs leading-5 text-[#756763]">{status.message}</p>
                                        {trackingEnabled && order.fulfillmentType === "PICKUP" && order.estimatedReadyAt && order.delayReportedAt && (
                                            <p className="mt-2 rounded-xl bg-amber-50 p-3 text-xs text-[#6b3900]">
                                                Revised ready estimate {formatBusinessTimestamp(order.estimatedReadyAt, {day: "numeric", month: "short", hour: "numeric", minute: "2-digit"})} IST. Updated {formatBusinessTimestamp(order.delayReportedAt, {hour: "numeric", minute: "2-digit"})} IST. Open this order for help.
                                            </p>
                                        )}

                                        <div className="mt-4 grid grid-cols-2 gap-4 rounded-xl bg-[#fffaf3] p-4 text-sm">
                                            <div>
                                                <p className="text-xs text-[#756763]">{order.fulfillmentType === "DELIVERY" ? "Delivery" : translate("Pickup")}</p>
                                                <p className="mt-1 font-semibold text-[#241715]">{order.fulfillmentType === "DELIVERY" ? order.deliveryDate ? formatOrderDate(order.deliveryDate) : "Window pending" : order.pickupDate ? formatOrderDate(order.pickupDate) : "Time pending"}</p>
                                                <p className="mt-1 text-xs text-[#756763]">
                                                    {order.fulfillmentType === "DELIVERY"
                                                        ? order.deliveryStartTime && order.deliveryEndTime ? `${formatOrderTime(order.deliveryStartTime)} – ${formatOrderTime(order.deliveryEndTime)} IST` : ""
                                                        : order.pickupStartTime && order.pickupEndTime ? `${formatOrderTime(order.pickupStartTime)} – ${formatOrderTime(order.pickupEndTime)}` : ""}
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
                        >{" "}<T text="Previous" />{" "}</button>
                        <p className="text-xs font-semibold text-[#756763]">{" "}<T text="Page" />{" "}{safePage} of {totalPages} · {filteredOrders.length}{" "}<T text="orders" />{" "}</p>
                        <button
                            type="button"
                            disabled={safePage >= totalPages}
                            onClick={() => setPage(value => Math.min(totalPages, value + 1))}
                            className="min-h-10 rounded-xl border border-[#eadfd6] px-4 text-sm font-bold text-[#7a1625] disabled:cursor-not-allowed disabled:opacity-40"
                        >{" "}<T text="Next" />{" "}</button>
                    </nav>
                )}

            </section>
        </AppShell>
    );
}
