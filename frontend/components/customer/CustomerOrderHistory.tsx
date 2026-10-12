"use client";
import { Suspense, useCallback, useEffect, useRef, useState } from "react";
import { usePathname, useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { ApiError } from "@/services/apiClient";
import { getVerifiedOrderPage, type VerifiedOrderPage } from "@/services/orderApi";
import { formatOrderCurrency, formatOrderDate, getOrderStatusPresentation } from "@/lib/orderTracking";
import { orderDisplayNumber } from "@/lib/orderDisplayNumber";
import { useStorefrontFeatures } from "@/hooks/useStorefrontFeatures";
import { usePhoneViewport } from "@/hooks/usePhoneViewport";
import { subscribeCustomerIdentityChanges } from "@/lib/customerIdentityEvents";
import BrandLoading from "@/components/common/BrandLoading";
import OrderRatingLink from "@/components/order/OrderRatingLink";
import CustomerIcon from "./CustomerIcon";
import ReorderDialog from "./ReorderDialog";
import OrderRewardGuidance from "./OrderRewardGuidance";
import type { CustomerOrderSummaryResponse } from "@/types/order";
import styles from "./CustomerOrderHistory.module.css";
type Props = {
    phone?: string;
    branchId?: number;
    branchName?: string;
};
export default function CustomerOrderHistory(props: Props) { return <Suspense fallback={<BrandLoading label="Loading your orders…"/>}><History {...props}/></Suspense>; }
function History({ phone, branchId, branchName }: Props) {
    const search = useSearchParams(), path = usePathname(), router = useRouter(), features = useStorefrontFeatures(), compact = usePhoneViewport() === true;
    const scope = branchId == null ? "all" : String(branchId), matches = search.get("scope") == null || search.get("scope") === scope;
    const status = matches ? search.get("status") ?? "ALL" : "ALL", query = matches ? search.get("q") ?? "" : "", from = matches ? search.get("from") ?? "" : "", to = matches ? search.get("to") ?? "" : "";
    const page = matches ? Math.max(1, Math.min(100000, Math.floor(Number(search.get("historyPage"))) || 1)) : 1;
    const before = matches ? search.get("before") : null, after = matches ? search.get("after") : null;
    const [revision, setRevision] = useState(0), [busy, setBusy] = useState(false), [error, setError] = useState("");
    const [loaded, setLoaded] = useState<{
        key: string;
        data: VerifiedOrderPage;
    } | null>(null), [reorder, setReorder] = useState<CustomerOrderSummaryResponse | null>(null);
    const closeReorder = useCallback(() => setReorder(null), [setReorder]);
    const key = JSON.stringify([phone, scope, status, query, from, to, before, after]);
    const current = loaded?.key === key ? loaded.data : null;
    const request = useRef<AbortController | null>(null);
    useEffect(() => { const update = (event: Event) => { const detail = (event as CustomEvent<{
        orderNumber: string;
        rating: number;
    }>).detail; if (!detail || !Number.isInteger(detail.rating) || detail.rating < 1 || detail.rating > 5)
        return; setLoaded(current => current ? { ...current, data: { ...current.data, orders: current.data.orders.map(order => order.orderNumber === detail.orderNumber ? { ...order, reviewRating: detail.rating } : order) } } : current); }; window.addEventListener("gokul-order-review-updated", update); return () => window.removeEventListener("gokul-order-review-updated", update); }, []);
    useEffect(() => subscribeCustomerIdentityChanges(() => { setLoaded(null); setRevision(value => value + 1); }, { revalidateOnResume: false }), []);
    useEffect(() => {
        const controller = new AbortController();
        request.current = controller;
        void Promise.resolve().then(() => { if (!controller.signal.aborted) {
            setBusy(true);
            setError("");
        } });
        const filters: Record<string, string> = { limit: "12", status };
        if (branchId != null)
            filters.branchId = String(branchId);
        if (query)
            filters.q = query;
        if (from)
            filters.from = from;
        if (to)
            filters.to = to;
        if (after)
            filters.after = after;
        void getVerifiedOrderPage(before, AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]), filters).then(data => { if (controller.signal.aborted)
            return; if (!Array.isArray(data.orders))
            throw new Error("Invalid history response"); setLoaded({ key, data }); }).catch(error => { if (!controller.signal.aborted) {
            if (error instanceof ApiError && [401, 403, 404].includes(error.status))
                setLoaded(null);
            setError("Order history could not refresh. Your current position is kept. Try again.");
        } }).finally(() => { if (request.current === controller) {
            request.current = null;
            setBusy(false);
        } });
        return () => controller.abort();
    }, [key, revision, branchId, status, query, from, to, after, before]);
    useEffect(() => { const resume = () => { if (document.visibilityState === "visible" && navigator.onLine && !request.current)
        setRevision(value => value + 1); }; window.addEventListener("online", resume); document.addEventListener("visibilitychange", resume); return () => { window.removeEventListener("online", resume); document.removeEventListener("visibilitychange", resume); }; }, []);
    useEffect(() => { if (!current?.orders.some(order => ["PENDING_PAYMENT", "CONFIRMED", "PREPARING", "READY_FOR_PICKUP", "READY_FOR_DELIVERY", "OUT_FOR_DELIVERY"].includes(order.orderStatus)))
        return; const timer = window.setInterval(() => { if (document.visibilityState === "visible" && navigator.onLine && !request.current)
        setRevision(value => value + 1); }, 15000); return () => window.clearInterval(timer); }, [current]);
    function historyParams() {
        const params = new URLSearchParams(search.toString());
        if (!matches) {
            for (const name of ["status", "q", "from", "to", "before", "after", "historyPage"])
                params.delete(name);
        }
        params.set("scope", scope);
        return params;
    }
    function navigate(changes: Record<string, string | null>, reset = false) { const params = historyParams(); if (reset) {
        params.delete("before");
        params.delete("after");
        params.set("historyPage", "1");
    } for (const [name, value] of Object.entries(changes)) {
        if (value)
            params.set(name, value);
        else
            params.delete(name);
    } router.push(`${path}?${params}`, { scroll: false }); }
    const Heading = path === "/orders" ? "h1" : "h2";
    const returnTo = `${path === "/profile" ? "/profile/orders" : path}?${historyParams()}`;
    return <div className={styles.history} data-modern={features?.futuristicStorefrontV2 || features?.checkoutExperienceV2 ? "true" : undefined}>
  <header className={styles.hero}><div><span>Your Gokul moments</span><Heading>Every order. Easy to find.</Heading><p>{branchName ?? "All your branches, together."}</p></div><CustomerIcon kind="receipt"/></header>
  {path === "/orders" && <OrderRewardGuidance/>}
  <form className={styles.search} onSubmit={event => {
      event.preventDefault();
      // Read the submitted field, including input events not yet committed to React state.
      const submitted = new FormData(event.currentTarget).get("q");
      navigate({ q: typeof submitted === "string" ? submitted.trim() || null : null }, true);
  }}><CustomerIcon kind="receipt"/><input name="q" aria-label="Search order history" maxLength={100} placeholder="Order number or dish name" key={query} defaultValue={query}/><button type="submit">Search</button></form>
  <div className={styles.filters} aria-label="Order status">{[["ALL", "All"], ["ACTIVE", "Active"], ["COMPLETED", "Completed"], ["ISSUES", "Cancelled / Failed"]].map(([value, label]) => <button key={value} type="button" aria-pressed={status === value} onClick={() => navigate({ status: value }, true)}>{label}</button>)}</div>
  <div className={styles.dates}><label>From<input type="date" value={from} max={to || undefined} onChange={event => navigate({ from: event.target.value || null }, true)}/></label><label>To<input type="date" value={to} min={from || undefined} onChange={event => navigate({ to: event.target.value || null }, true)}/></label></div>
  {error && <div className={styles.error} role="alert"><p>{error}</p><button type="button" onClick={() => setRevision(value => value + 1)}>Retry</button><button type="button" onClick={() => navigate({}, true)}>Latest orders</button></div>}
  {!current && !error && <BrandLoading label="Loading your orders…"/>}
  {current && <><div className={styles.heading}><strong>Page {page}</strong><span aria-live="polite">{busy ? "Updating…" : `${current.orders.length} orders shown`}</span>{(!compact || !features?.simplifiedCheckout) && <button type="button" disabled={busy} onClick={() => setRevision(value => value + 1)}>Refresh</button>}</div><div className={styles.cards}>{current.orders.map(order => {
                const presentation = getOrderStatusPresentation(order.orderStatus, order.fulfillmentType);
                const href = `/orders/${encodeURIComponent(order.orderNumber)}?from=${encodeURIComponent(returnTo)}`;
                return <article key={order.orderNumber} className={`profile-order-card ${styles.order}`} data-status={order.orderStatus}>
   <div className={styles.top}><strong>{order.customerOrderNumber ? `Order ${orderDisplayNumber(order)}` : `Reference …${order.orderNumber.slice(-6)}`}</strong><span data-tone={presentation.tone}>{presentation.label}</span></div><p>{order.branchName}</p><div className={styles.meta}><span>{formatOrderDate(order.createdAt.slice(0, 10))} · {order.fulfillmentType === "DELIVERY" ? "Delivery" : "Pickup"}</span><strong>{formatOrderCurrency(order.totalAmount)}</strong></div>
   {["PICKED_UP", "DELIVERED"].includes(order.orderStatus) && <OrderRatingLink orderNumber={order.orderNumber} rating={order.reviewRating ?? null} returnTo={returnTo}/>}
   <div className={styles.actions}><Link href={href} className={styles.primary}>View Order</Link><button type="button" onClick={() => setReorder(order)}>Reorder</button></div>
  </article>;
            })}</div>{!current.orders.length && <div className={styles.empty}><p>No matching orders. Try a different branch, status, date or search.</p>{!error && (page > 1 || before || after) && <button type="button" disabled={busy} onClick={() => navigate({}, true)}>Latest orders</button>}</div>}
  <nav className={styles.pagination} aria-label="Order history pages"><button type="button" disabled={busy || !current.previousAfter} onClick={() => navigate({ before: null, after: current.previousAfter ?? null, historyPage: String(Math.max(1, page - 1)) })}>Previous</button><span>Page {page}</span><button type="button" disabled={busy || !current.nextBefore} onClick={() => navigate({ after: null, before: current.nextBefore, historyPage: String(page + 1) })}>Next</button></nav></>}
  {reorder && features && <ReorderDialog key={`${phone}:${reorder.orderNumber}`} order={reorder} phone={phone} features={features} compact={compact} onClose={closeReorder}/>}
 </div>;
}
