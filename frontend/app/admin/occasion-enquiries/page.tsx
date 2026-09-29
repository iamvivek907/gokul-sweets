"use client";

import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import {getActiveBranches} from "@/services/branchApi";

type Branch = {id: number; name: string};
type Enquiry = {id: string; occasionType: string; serviceDate: string; guestCount: number; fulfilment: string;
    customerPhone: string; deliveryAddress: string | null; notes: string | null; status: string;
    items: {productId: number; productName: string; quantity: number; unit: string}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number; taxAmount: number; cgstRate: number; sgstRate: number}[];
    quotedAmount: number | null; depositAmount: number | null; paidAmount: number; balanceDueAt: string | null;
    orderNumber: string | null;
    productionPlan?: {productId: number; quantity: number; unit: string; expectedReadyAt: string; state: string; readyQuantity: number; readinessRevision: number}[];
    quoteTerms: string | null; nextStep: string};

export default function OccasionEnquiriesPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [requests, setRequests] = useState<Enquiry[]>([]);
    const [lineAmounts, setLineAmounts] = useState<Record<string, Record<number, string>>>({});
    const [deposit, setDeposit] = useState<Record<string, string>>({});
    const [balanceDue, setBalanceDue] = useState<Record<string, string>>({});
    const [actualReady, setActualReady] = useState<Record<string, string>>({});
    const [readyAt, setReadyAt] = useState<Record<string, string>>({});
    const [terms, setTerms] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState("");
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        if (!authorization) return;
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(list => {if (!controller.signal.aborted) {
            setBranches(list); setBranchId(current => current ?? list[0]?.id ?? null);
        }}).catch(() => {if (!controller.signal.aborted) setNotice("Could not load branches.");});
        return () => controller.abort();
    }, [authorization]);
    useEffect(() => {
        if (!authorization || !branchId) return;
        const controller = new AbortController();
        adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries`, authorization, {signal: controller.signal})
            .then(async response => {if (!response.ok) throw new Error("Could not load enquiries."); return response.json() as Promise<Enquiry[]>;})
            .then(list => {if (!controller.signal.aborted) setRequests(list);})
            .catch(() => {if (!controller.signal.aborted) setNotice("Could not load enquiries for this branch.");});
        return () => controller.abort();
    }, [authorization, branchId]);

    async function decide(enquiry: Enquiry, action: "quote" | "decline") {
        if (!authorization || !branchId || busy) return;
        setBusy(true); setNotice("");
        try {
            // Quote lifetime begins when the staff member submits the decision.
            const lines = enquiry.items.map(item => ({productId: item.productId,
                grossAmount: Number(lineAmounts[enquiry.id]?.[item.productId])}));
            const total = Math.round(lines.reduce((sum, line) => sum + line.grossAmount, 0) * 100) / 100;
            const advance = Number(deposit[enquiry.id]);
            if (action === "quote" && (lines.some(line => !Number.isFinite(line.grossAmount) || line.grossAmount <= 0)
                || !Number.isFinite(total) || total <= 0 || !Number.isFinite(advance)
                || advance <= 0 || advance > total || advance < total && !balanceDue[enquiry.id]))
                throw new Error("Enter a positive deposit and a balance deadline when a balance remains.");
            if (action === "quote" && enquiry.fulfilment === "PICKUP"
                && (!readyAt[enquiry.id] || !readyAt[enquiry.id].startsWith(enquiry.serviceDate)))
                throw new Error("Enter a kitchen-ready time on the requested date in IST.");
            const body = action === "quote" ? {amount: total, deposit: advance, lines,
                expectedReadyAt: enquiry.fulfilment === "PICKUP" ? readyAt[enquiry.id] : null,
                // eslint-disable-next-line react-hooks/purity
                expiresAt: new Date(Math.min(Date.now() + 24 * 60 * 60 * 1000,
                    new Date(`${enquiry.serviceDate}T00:00:00+05:30`).getTime() - 2 * 60 * 60 * 1000,
                    advance < total ? new Date(`${balanceDue[enquiry.id]}+05:30`).getTime() - 60 * 60 * 1000 : Infinity)).toISOString(),
                balanceDueAt: advance < total ? new Date(`${balanceDue[enquiry.id]}+05:30`).toISOString() : null,
                terms: terms[enquiry.id] ?? ""}
                : {reason: terms[enquiry.id] ?? ""};
            const response = await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/${enquiry.id}/${action}`,
                authorization, {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify(body)});
            if (!response.ok) throw new Error("Could not save. Check your permission, amount and request status.");
            const updated = await response.json() as Enquiry;
            setRequests(current => current.map(item => item.id === updated.id ? updated : item));
            setNotice(action === "quote" ? "Quote saved. The customer can choose a live pickup slot and start a deposit if payments are enabled." : "Enquiry declined.");
        } catch (error) {setNotice(error instanceof Error ? error.message : "Could not save this request.");}
        finally {setBusy(false);}
    }

    async function saveReadiness(enquiry: Enquiry, productId: number, revision: number, unit: string) {
        if (!authorization || !branchId || busy) return;
        const input = Number(actualReady[`${enquiry.id}:${productId}`]);
        if (!Number.isFinite(input) || input < 0 || actualReady[`${enquiry.id}:${productId}`] === undefined) {
            setNotice("Enter the actual quantity prepared."); return;
        }
        setBusy(true); setNotice("");
        try {
            const response = await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/${enquiry.id}/production/${productId}/readiness`, authorization,
                {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({quantity: unit === "GRAM" ? Math.round(input * 1000) : input, revision})});
            if (!response.ok) throw new Error("Could not save readiness. The order must be preparing; check the quantity and refresh if another staff member updated it.");
            const updated = await response.json() as Enquiry;
            setRequests(current => current.map(item => item.id === updated.id ? updated : item));
            setNotice("Actual prepared quantity recorded. Mark the order ready for pickup only after every item is fully prepared.");
        } catch (error) {setNotice(error instanceof Error ? error.message : "Could not save readiness.");}
        finally {setBusy(false);}
    }

    return <div className="mx-auto max-w-5xl space-y-6 p-6">
        <h1 className="text-3xl font-bold">Occasion food enquiries</h1>
        <p>Review a request before quoting. A quote is not a confirmed booking. When dedicated bulk production is enabled, approving a pickup quote automatically creates its production plan; the deposit commits it. Daily online stock is unchanged.</p>
        {notice && <p role="status" className="rounded-xl bg-amber-50 p-3">{notice}</p>}
        <label className="block">Branch <select value={branchId ?? ""} onChange={event => setBranchId(Number(event.target.value))} className="ml-3 rounded-lg border p-2">
            {branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select></label>
        {requests.map(enquiry => <article key={enquiry.id} className="space-y-3 rounded-2xl border bg-white p-5">
            <h2 className="text-xl font-bold">{enquiry.occasionType} · {enquiry.serviceDate}</h2>
            <p>{enquiry.guestCount} guests · {enquiry.fulfilment} · {enquiry.status}</p>
            <p>Verified customer: {enquiry.customerPhone}</p>
            {enquiry.deliveryAddress && <p>Requested delivery address: {enquiry.deliveryAddress} (coverage requires staff review)</p>}
            {enquiry.notes && <p>Customer notes: {enquiry.notes}</p>}
            <ul className="list-inside list-disc">{enquiry.items.map(item => <li key={item.productId}>{item.productName}: {item.quantity} {item.unit.toLowerCase()}</li>)}</ul>
            {enquiry.pricedLines?.length > 0 && <ul className="text-sm">{enquiry.pricedLines.map(line => <li key={line.productId}>
                {line.productName}: ₹{line.grossAmount} inclusive (base ₹{line.subtotal}, tax ₹{line.taxAmount} at {line.cgstRate}% CGST + {line.sgstRate}% SGST)
            </li>)}</ul>}
            {enquiry.orderNumber && <p>Operational order: {enquiry.orderNumber}</p>}
            {enquiry.quotedAmount != null && <p>Quoted ₹{enquiry.quotedAmount}; deposit ₹{enquiry.depositAmount}; paid ₹{enquiry.paidAmount}.
                {enquiry.balanceDueAt && ` Balance due ${new Date(enquiry.balanceDueAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST.`} {enquiry.quoteTerms}</p>}
            <p className="text-sm">{enquiry.nextStep}</p>
            {!!enquiry.productionPlan?.length && <div className="rounded-xl bg-blue-50 p-3 text-sm">
                <p className="font-semibold">Dedicated bulk production — separate from daily online stock</p>
                {enquiry.productionPlan.map(line => <p key={line.productId}>{enquiry.items.find(item => item.productId === line.productId)?.productName}: {line.unit === "GRAM" ? `${line.quantity / 1000} kg` : `${line.quantity} pcs`} · ready by {line.expectedReadyAt.replace("T", " ")} IST · {line.state === "PLANNED" ? "Awaiting deposit" : line.state === "HELD" ? "Payment in progress" : line.state === "COMMITTED" ? "Production committed" : "Released"}</p>)}
                {enquiry.productionPlan.map(line => <div key={`ready-${line.productId}`} className="mt-3 rounded-lg border bg-white p-3">
                    <p>{enquiry.items.find(item => item.productId === line.productId)?.productName}: physically prepared {line.unit === "GRAM" ? `${line.readyQuantity / 1000} kg` : `${line.readyQuantity} pcs`}</p>
                    {enquiry.status === "CONFIRMED" && line.state === "COMMITTED" && hasPermission("ORDER_MARK_READY") && <div className="mt-2 flex flex-wrap items-end gap-2">
                        <label>Actual ready quantity ({line.unit === "GRAM" ? "kg" : "pcs"})
                            <input type="number" min="0" max={line.unit === "GRAM" ? line.quantity / 1000 : line.quantity} step={line.unit === "GRAM" ? ".001" : "1"}
                                value={actualReady[`${enquiry.id}:${line.productId}`] ?? ""} onChange={event => setActualReady(current => ({...current, [`${enquiry.id}:${line.productId}`]: event.target.value}))}
                                className="block w-40 rounded-lg border p-2" />
                        </label>
                        <button type="button" disabled={busy} onClick={() => void saveReadiness(enquiry, line.productId, line.readinessRevision, line.unit)} className="min-h-11 rounded-lg border px-3 disabled:opacity-50">Record prepared quantity</button>
                        <p className="w-full text-xs">Enter the total actually prepared, not an additional quantity. Start preparation in the linked order first. Partial preparation does not make the whole order ready; every item must reach its approved quantity. Changes are audited and daily online stock is unchanged.</p>
                    </div>}
                </div>)}
                <p>Approval records a plan, not physically ready stock. Review procurement, time remaining and all existing kitchen commitments before approving.</p>
            </div>}
            {hasPermission("APPROVAL_MANAGE") && ["REQUESTED", "QUOTED"].includes(enquiry.status) && <div className="grid gap-3 sm:grid-cols-3">
                <div className="sm:col-span-3"><p className="font-semibold">Approved item totals, including configured tax</p>
                    {enquiry.items.map(item => <label key={item.productId} className="mt-2 block">{item.productName} ({item.quantity} {item.unit.toLowerCase()}) ₹
                        <input type="number" min="0.01" step="0.01" value={lineAmounts[enquiry.id]?.[item.productId] ?? ""}
                            onChange={event => setLineAmounts(current => ({...current, [enquiry.id]: {...current[enquiry.id], [item.productId]: event.target.value}}))}
                            className="mt-1 block w-full rounded-lg border p-2" /></label>)}
                    <p className="mt-2">Quote total ₹{enquiry.items.reduce((sum, item) => sum + Number(lineAmounts[enquiry.id]?.[item.productId] ?? 0), 0).toFixed(2)}</p>
                </div>
                {enquiry.fulfilment === "PICKUP" && <label>Kitchen ready by (IST)
                    <input type="datetime-local" value={readyAt[enquiry.id] ?? ""} onChange={event => setReadyAt(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" />
                    <span className="mt-1 block text-xs">Promise a time on the fulfilment date after reviewing ingredients, production time and existing bookings. Pickup cannot start earlier. With bulk production enabled, approval creates the dedicated allocation automatically.</span>
                </label>}
                <label>Deposit ₹ <input type="number" min="0.01" step="0.01" value={deposit[enquiry.id] ?? ""} onChange={event => setDeposit(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /><span className="mt-1 block text-xs">Part of the total, not an extra charge. Verified deposit reserves production; the remaining balance confirms the pickup.</span></label>
                <label>Balance due (IST) <input type="datetime-local" value={balanceDue[enquiry.id] ?? ""} onChange={event => setBalanceDue(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /><span className="mt-1 block text-xs">Required for part payment. Choose a deadline after quote expiry and before the fulfilment date in IST.</span></label>
                <label>Terms or reason <input maxLength={500} value={terms[enquiry.id] ?? ""} onChange={event => setTerms(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /><span className="mt-1 block text-xs">Explain fulfilment, payment deadlines and cancellation terms, or why the request is declined. The customer sees this text.</span></label>
                <button type="button" disabled={busy || !terms[enquiry.id]} onClick={() => void decide(enquiry, "quote")} className="min-h-11 rounded-lg bg-[#143936] px-4 text-white disabled:opacity-50">Send reviewed quote</button>
                <button type="button" disabled={busy || !terms[enquiry.id]} onClick={() => void decide(enquiry, "decline")} className="min-h-11 rounded-lg border px-4 disabled:opacity-50">Decline with reason</button>
            </div>}
        </article>)}
    </div>;
}
