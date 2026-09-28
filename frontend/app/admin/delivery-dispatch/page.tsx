"use client";

import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {preferredAdminBranchId, rememberAdminBranchId} from "@/lib/adminBranchSelection";

type Row = {orderId: number; orderNumber: string; windowId: number; date: string;
    start: string; end: string; orderStatus: string; riderId: number | null; state: string | null;
    customerPhone: string; address: string};
type Rider = {id: number; displayName: string; active: boolean};

export default function DeliveryDispatchPage() {
    const {profile, authorization, hasPermission} = useAdminAuth();
    const [branchId, setBranchId] = useState<number | null>(null);
    const [rows, setRows] = useState<Row[]>([]);
    const [riders, setRiders] = useState<Rider[]>([]);
    const [chosen, setChosen] = useState<Record<number, string>>({});
    const [message, setMessage] = useState("");
    const [newRider, setNewRider] = useState("");
    const [busy, setBusy] = useState(false);
    const activeBranch = branchId ?? (profile ? preferredAdminBranchId(profile.staffId, profile.branchIds.map(id => ({id}))) : null);
    const path = activeBranch ? `/api/admin/branches/${activeBranch}/delivery-dispatch` : "";
    const refresh = useCallback(async (signal?: AbortSignal) => {
        if (!authorization || !path) return;
        const [ordersResponse, ridersResponse] = await Promise.all([
            adminFetch(path, authorization, {signal}), adminFetch(`${path}/riders`, authorization, {signal})]);
        if (!ordersResponse.ok || !ridersResponse.ok) throw new Error("Dispatch board is unavailable. Check pilot access and retry.");
        if (signal?.aborted) return;
        setRows(await ordersResponse.json() as Row[]);
        setRiders(await ridersResponse.json() as Rider[]);
    }, [authorization, path]);
    useEffect(() => {
        const controller = new AbortController();
        void Promise.resolve().then(() => refresh(controller.signal))
            .catch(error => {if (!controller.signal.aborted) setMessage(String(error));});
        return () => controller.abort();
    }, [refresh]);
    async function send(url: string, body: object) {
        if (!authorization) return;
        setBusy(true); setMessage("");
        try {
            const result = await adminFetch(url, authorization, {method: "POST",
                headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
            if (!result.ok) throw new Error("Action failed. Refresh the order, rider and window before trying again.");
            await refresh(); setMessage("Dispatch record updated.");
        } catch (error) {setMessage(error instanceof Error ? error.message : "Dispatch update failed.");}
        finally {setBusy(false);}
    }
    if (!hasPermission("ORDER_VIEW")) return <p>Delivery dispatch access is unavailable.</p>;
    return <main className="mx-auto max-w-6xl space-y-5 p-5">
        <h1 className="text-2xl font-bold">Delivery dispatch</h1>
        <p className="text-sm">Pilot rider assignments are limited to one order per rider and window. Window times are IST.</p>
        <label className="block">Branch <select className="ml-2 rounded border p-2" value={activeBranch ?? ""}
            onChange={event => {const next = Number(event.target.value); setBranchId(next); if (profile) rememberAdminBranchId(profile.staffId, next);}}>
            {profile?.branchIds.map(id => <option key={id} value={id}>{id}</option>)}
        </select></label>
        <button type="button" className="rounded border px-3 py-2" onClick={() => refresh().catch(error => setMessage(String(error)))}>Refresh</button>
        {message && <p role="status">{message}</p>}
        {hasPermission("BRANCH_MANAGE") && <form className="flex flex-wrap gap-2" onSubmit={event => {
            event.preventDefault(); if (!newRider.trim()) return;
            void send(`${path}/riders`, {name: newRider.trim()}).then(() => setNewRider(""));
        }}><input className="rounded border p-2" aria-label="New rider name" placeholder="New pilot rider"
            value={newRider} onChange={event => setNewRider(event.target.value)} />
            <button disabled={busy || !activeBranch} className="rounded border px-3 py-2">Add rider</button></form>}
        {!rows.length && <p>No active deliveries for this branch and the next two days.</p>}
        <div className="grid gap-4">{rows.map(row => <section key={row.orderId} className="rounded-xl border bg-white p-4">
            <div className="flex flex-wrap justify-between gap-2"><strong>{row.orderNumber}</strong>
                <span>{row.date} · {row.start.slice(0, 5)}–{row.end.slice(0, 5)} IST</span></div>
            <p>{row.orderStatus} · Rider {row.riderId ?? "unassigned"} · {row.address}</p>
            <p>Customer contact: <a href={`tel:${row.customerPhone}`}>{row.customerPhone}</a></p>
            <div className="mt-3 flex flex-wrap gap-2">
                {row.orderStatus === "READY_FOR_DELIVERY" && hasPermission("BRANCH_MANAGE") &&
                    <button type="button" disabled={busy || !chosen[row.orderId]} className="rounded border px-3 py-2"
                        onClick={async () => {
                            if (!authorization) return;
                            setBusy(true); setMessage("");
                            try {
                                const result = await adminFetch(`${path}/riders/${chosen[row.orderId]}/windows/${row.windowId}`,
                                    authorization, {method: "PUT", headers: {"Content-Type": "application/json"},
                                        body: JSON.stringify({available: true})});
                                if (!result.ok) throw new Error("Could not register rider availability.");
                                setMessage("Rider availability registered for this window.");
                            } catch (error) {setMessage(String(error));} finally {setBusy(false);}
                        }}>Mark rider available</button>}
                {row.orderStatus === "READY_FOR_DELIVERY" && hasPermission("ORDER_DISPATCH_DELIVERY") && <>
                    <select aria-label={`Rider for ${row.orderNumber}`} className="rounded border p-2" value={chosen[row.orderId] ?? ""}
                        onChange={event => setChosen(current => ({...current, [row.orderId]: event.target.value}))}>
                        <option value="">Choose available rider</option>{riders.filter(r => r.active).map(r =>
                        <option key={r.id} value={r.id}>{r.displayName} (#{r.id})</option>)}</select>
                    <button type="button" disabled={busy || !chosen[row.orderId] || (!!row.riderId && row.state !== "EXCEPTION")} className="rounded bg-[#7a1625] px-4 py-2 text-white"
                        onClick={() => send(`${path}/orders/${row.orderId}/assign`, {riderId: Number(chosen[row.orderId])})}>Assign</button>
                </>}
                {hasPermission("ORDER_DISPATCH_DELIVERY") && <button type="button" disabled={busy} className="rounded border px-3 py-2"
                    onClick={() => {const detail = window.prompt("Describe the delay or handoff issue");
                        if (detail?.trim()) void send(`${path}/orders/${row.orderId}/exception`,
                            {reason: "OTHER", detail: detail.trim(), customerContacted: false});}}>Record exception</button>}
                {row.orderStatus === "DELIVERED" && hasPermission("ORDER_CONFIRM_DELIVERY") && <button type="button" disabled={busy}
                    onClick={() => {const cost = window.prompt("Actual journey cost (INR)");
                        if (cost !== null && Number.isFinite(Number(cost)) && Number(cost) >= 0)
                            void send(`${path}/orders/${row.orderId}/outcome`, {actualJourneyCost: Number(cost), outcome: "DELIVERED"});}}
                    className="rounded border px-3 py-2">Record journey cost</button>}
                <Link className="rounded border px-3 py-2" href="/admin/orders">Open orders</Link>
            </div>
        </section>)}</div>
    </main>;
}
