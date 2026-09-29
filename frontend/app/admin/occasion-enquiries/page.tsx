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
    quoteTerms: string | null; nextStep: string};

export default function OccasionEnquiriesPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [requests, setRequests] = useState<Enquiry[]>([]);
    const [lineAmounts, setLineAmounts] = useState<Record<string, Record<number, string>>>({});
    const [deposit, setDeposit] = useState<Record<string, string>>({});
    const [balanceDue, setBalanceDue] = useState<Record<string, string>>({});
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
            const body = action === "quote" ? {amount: total, deposit: advance, lines,
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

    return <div className="mx-auto max-w-5xl space-y-6 p-6">
        <h1 className="text-3xl font-bold">Occasion food enquiries</h1>
        <p>Review a request before quoting. A quote does not reserve inventory or confirm the occasion.</p>
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
            {hasPermission("APPROVAL_MANAGE") && ["REQUESTED", "QUOTED"].includes(enquiry.status) && <div className="grid gap-3 sm:grid-cols-3">
                <div className="sm:col-span-3"><p className="font-semibold">Approved item totals, including configured tax</p>
                    {enquiry.items.map(item => <label key={item.productId} className="mt-2 block">{item.productName} ({item.quantity} {item.unit.toLowerCase()}) ₹
                        <input type="number" min="0.01" step="0.01" value={lineAmounts[enquiry.id]?.[item.productId] ?? ""}
                            onChange={event => setLineAmounts(current => ({...current, [enquiry.id]: {...current[enquiry.id], [item.productId]: event.target.value}}))}
                            className="mt-1 block w-full rounded-lg border p-2" /></label>)}
                    <p className="mt-2">Quote total ₹{enquiry.items.reduce((sum, item) => sum + Number(lineAmounts[enquiry.id]?.[item.productId] ?? 0), 0).toFixed(2)}</p>
                </div>
                <label>Deposit ₹ <input type="number" min="0.01" step="0.01" value={deposit[enquiry.id] ?? ""} onChange={event => setDeposit(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /></label>
                <label>Balance due (IST) <input type="datetime-local" value={balanceDue[enquiry.id] ?? ""} onChange={event => setBalanceDue(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /></label>
                <label>Terms or reason <input maxLength={500} value={terms[enquiry.id] ?? ""} onChange={event => setTerms(current => ({...current, [enquiry.id]: event.target.value}))} className="block w-full rounded-lg border p-2" /></label>
                <button type="button" disabled={busy || !terms[enquiry.id]} onClick={() => void decide(enquiry, "quote")} className="min-h-11 rounded-lg bg-[#143936] px-4 text-white disabled:opacity-50">Send reviewed quote</button>
                <button type="button" disabled={busy || !terms[enquiry.id]} onClick={() => void decide(enquiry, "decline")} className="min-h-11 rounded-lg border px-4 disabled:opacity-50">Decline with reason</button>
            </div>}
        </article>)}
    </div>;
}
