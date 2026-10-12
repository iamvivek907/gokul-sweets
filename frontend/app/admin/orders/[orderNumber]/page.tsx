"use client";
import {orderDisplayNumber} from "@/lib/orderDisplayNumber";
import OrderCorrectionPanel from "@/components/admin/OrderCorrectionPanel";
import PickupHandoverAction from "@/components/admin/PickupHandoverAction";
import {T} from "@/lib/language";


import {use, useCallback, useEffect, useState} from "react";
import Link from "next/link";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {getAdminOrderDetail, reportAdminOrderDelay, updateAdminOrderStatus} from "@/services/adminOrdersApi";
import {rememberAdminBranchId} from "@/lib/adminBranchSelection";
import type {AdminOrderDetail, OrderStatus} from "@/types/adminOrders";

export default function StaffAlertOrderPage({params}: {params: Promise<{orderNumber: string}>}) {
    const {orderNumber} = use(params);
    const {authorization, profile, hasPermission} = useAdminAuth();
    const [order, setOrder] = useState<AdminOrderDetail | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [estimate, setEstimate] = useState("");
    const [reason, setReason] = useState("");
    const load = useCallback(async (signal?: AbortSignal) => {
        if (!authorization) return;
        const value = await getAdminOrderDetail(orderNumber, authorization, signal);
        if (!signal?.aborted) {setOrder(value); setError("");}
    }, [authorization, orderNumber]);
    useEffect(() => {
        const controller = new AbortController();
        void Promise.resolve().then(() => load(controller.signal)).catch(() => {if (!controller.signal.aborted) setError("This order could not load. Check branch access or refresh when connected.");});
        return () => controller.abort();
    }, [load]);
    async function perform(action: () => Promise<AdminOrderDetail>) {
        setBusy(true); setError("");
        try {setOrder(await action()); window.dispatchEvent(new Event("gokul-staff-inbox-changed"));}
        catch (error) {setError(error instanceof Error ? error.message : "Could not confirm this order action. Refresh before trying again.");}
        finally {setBusy(false);}
    }
    const next: {status: OrderStatus; label: string; permission: string} | null = !order ? null : order.orderStatus === "CONFIRMED"
        ? {status: "PREPARING", label: "Start preparation", permission: "ORDER_START_PREPARATION"}
        : order.orderStatus === "PREPARING" ? {status: order.fulfillmentType === "DELIVERY" ? "READY_FOR_DELIVERY" : "READY_FOR_PICKUP", label: "Mark ready", permission: "ORDER_MARK_READY"} : null;
    return <main className="mx-auto max-w-4xl p-4 sm:p-8">
        <Link href="/admin/staff-notifications" className="inline-flex min-h-11 items-center text-sm font-semibold underline">Back to staff notifications</Link>
        <h1 className="mt-2 break-all text-2xl font-bold text-[#173c39]"><T text="Order" />{" "}{order ? orderDisplayNumber(order) : "details"}</h1>
        {error && <p role="alert" className="mt-4 rounded-xl border border-[#c76752] bg-white p-4 text-sm">{error}</p>}
        {!order && !error && <p role="status" className="mt-4">Loading order…</p>}
        {order && <section className="mt-5 rounded-3xl border border-[#eadfd6] bg-white p-5 sm:p-7">
            <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-xl font-bold">{order.branchName}</h2><span className="rounded-full bg-[#fff0dc] px-3 py-2 text-xs font-bold">{order.orderStatus.replaceAll("_", " ")}</span></div>
            <p className="mt-3 text-sm">{order.fulfillmentType === "DELIVERY" ? `Delivery ${order.deliveryDate ?? ""}, ${order.deliveryStartTime ?? ""}–${order.deliveryEndTime ?? ""}` : `Pickup ${order.pickupDate ?? ""}, ${order.pickupStartTime ?? ""}–${order.pickupEndTime ?? ""}`} IST</p>
            <p className="mt-2 text-sm">Payment: {order.paymentStatus?.replaceAll("_", " ") ?? "Not recorded"}</p>
            <p className="mt-2 text-sm">Customer: {order.customerName} · {order.customerPhone}</p>
            <ul className="mt-5 space-y-2">{order.items.map((item, index) => <li key={index} className="rounded-xl bg-[#fffaf2] p-3 text-sm"><strong>{item.productName}</strong> · {item.saleMode === "WEIGHT" ? `${(item.weightGrams ?? 0) / 1000} kg` : `${item.quantity} units`}</li>)}</ul>
            <OrderCorrectionPanel orderNumber={orderNumber} branchId={order.branchId} pickupDate={order.pickupDate??null} onChanged={load}/>
            <p className="mt-4 text-sm leading-6 text-[#756763]">Start preparation only when its window opens. Mark ready only after preparation is complete. Existing eligibility, inventory and bulk-readiness checks still apply.</p>
            <div className="mt-4 flex flex-wrap gap-3">
                {order.orderStatus === "READY_FOR_PICKUP" && authorization && hasPermission("ORDER_MARK_PICKED_UP") && <PickupHandoverAction orderNumber={orderNumber} customerOrderNumber={order.customerOrderNumber} authorization={authorization} disabled={busy} onCompleted={load} />}
                {next && authorization && hasPermission(next.permission) && <button type="button" disabled={busy} onClick={() => void perform(() => updateAdminOrderStatus(orderNumber, {status: next.status}, authorization))} className="min-h-11 rounded-xl bg-[#173c39] px-4 text-sm font-bold text-white disabled:opacity-50">{next.label}</button>}
                <button type="button" disabled={busy} onClick={() => void load().catch(() => setError("Could not refresh this order. Check your connection."))} className="min-h-11 rounded-xl border px-4 text-sm">Refresh order</button>
                <Link href="/admin/orders" onClick={() => {if (profile) rememberAdminBranchId(profile.staffId, order.branchId);}} className="flex min-h-11 items-center rounded-xl border px-4 text-sm">Open branch queue</Link>
            </div>
            {order.orderStatus === "CONFIRMED" || order.orderStatus === "PREPARING" ? <div className="mt-6 border-t border-[#eadfd6] pt-5">
                <h3 className="font-bold">Keep the customer informed</h3>
                <p className="mt-2 text-sm leading-6">If preparation is delayed, publish a realistic ready estimate and a clear reason. The original booked slot remains visible.</p>
                <label className="mt-3 block text-sm">Revised ready time (IST)<input type="datetime-local" value={estimate} onChange={event => setEstimate(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>
                <label className="mt-3 block text-sm">Customer explanation<textarea value={reason} onChange={event => setReason(event.target.value)} maxLength={300} className="mt-2 block min-h-24 w-full rounded-xl border p-3" /></label>
                {authorization && hasPermission("ORDER_MARK_READY") && <button type="button" disabled={busy || !estimate || !reason.trim()} onClick={() => void perform(() => reportAdminOrderDelay(orderNumber, estimate, reason.trim(), authorization))} className="mt-3 min-h-11 rounded-xl border px-4 text-sm font-bold disabled:opacity-50">Publish revised estimate</button>}
            </div> : null}
        </section>}
    </main>;
}
