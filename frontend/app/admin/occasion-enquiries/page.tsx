"use client";

import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import OccasionPackingEditor,{type PackingRequest} from "@/components/admin/OccasionPackingEditor";
import OccasionQuoteEditor from "@/components/admin/OccasionQuoteEditor";
import OccasionCatalogueEditor from "@/components/admin/OccasionCatalogueEditor";
import {prettyDate} from "@/components/occasion/OccasionDatePicker";
import type {GiftSnapshot} from "@/types/occasionCatalogue";
import {preferredAdminBranchId, rememberAdminBranchId} from "@/lib/adminBranchSelection";
import {getActiveBranches} from "@/services/branchApi";

type Branch = {id: number; name: string};
type Enquiry = PackingRequest & {id: string; occasionType: string; serviceDate: string; guestCount: number; fulfilment: string;
    customerPhone: string; deliveryAddress: string | null; notes: string | null; status: string;
    gift?: GiftSnapshot | null;
    items: {productId: number; productName: string; quantity: number; unit: string; productionUnit?: string; suggestedProductionQuantity?: number | null}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number; taxAmount: number; cgstRate: number; sgstRate: number}[];
    quotedAmount: number | null; depositAmount: number | null; paidAmount: number; balanceDueAt: string | null;
    orderNumber: string | null;
    productionPlan?: {productId: number; quantity: number; unit: string; expectedReadyAt: string; state: string; readyQuantity: number; readinessRevision: number}[];
    cancellationReview?: {paidAmount: number; reason: string; state: string} | null;
    quoteTerms: string | null; nextStep: string};

export default function OccasionEnquiriesPage() {
    const {authorization, hasPermission, profile} = useAdminAuth();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [requests, setRequests] = useState<Enquiry[]>([]);
    const [cancelReason, setCancelReason] = useState<Record<string, string>>({});
    const [cancelReviewed, setCancelReviewed] = useState<Record<string, boolean>>({});
    const [actualReady, setActualReady] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState("");
    const [busy, setBusy] = useState(false);

    useEffect(() => {
        if (!authorization) return;
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(list => {if (!controller.signal.aborted) {
            const allowed=profile?.roleName==="OWNER_ADMIN"?list:list.filter(branch=>profile?.branchIds.includes(branch.id));
            setBranches(allowed); setBranchId(current => preferredAdminBranchId(profile!.staffId,allowed,current));
        }}).catch(() => {if (!controller.signal.aborted) setNotice("Could not load branches.");});
        return () => controller.abort();
    }, [authorization, profile]);
    useEffect(() => {
        if (!authorization || !branchId) return;
        const controller = new AbortController();
        adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries`, authorization, {signal: controller.signal})
            .then(async response => {if (!response.ok) throw new Error("Could not load enquiries."); return response.json() as Promise<Enquiry[]>;})
            .then(list => {if (!controller.signal.aborted) setRequests(list);})
            .catch(() => {if (!controller.signal.aborted) setNotice("Could not load enquiries for this branch.");});
        return () => controller.abort();
    }, [authorization, branchId]);

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

    async function cancelBooking(enquiry: Enquiry) {
        if (!authorization || !branchId || busy || !cancelReviewed[enquiry.id] || !cancelReason[enquiry.id]?.trim()) return;
        setBusy(true); setNotice("");
        try {
            const response = await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/${enquiry.id}/cancel`, authorization,
                {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({reason: cancelReason[enquiry.id]})});
            if (!response.ok) throw new Error("Could not cancel. Preparation may have started or the payment/booking state changed. Refresh and contact operations or finance.");
            const updated = await response.json() as Enquiry;
            setRequests(current => current.map(item => item.id === updated.id ? updated : item));
            setNotice("Booking cancelled and dedicated production released. Finance must review the amount collected; no refund has been executed.");
        } catch (error) {setNotice(error instanceof Error ? error.message : "Could not cancel this booking.");}
        finally {setBusy(false);}
    }

    return <div className="mx-auto max-w-5xl space-y-6 p-6">
        <h1 className="text-3xl font-bold">Occasion food enquiries</h1>
        <p>Review a request before quoting. A quote is not a confirmed booking. When dedicated bulk production is enabled, approving a pickup quote automatically creates its production plan; the deposit commits it. Daily online stock is unchanged.</p>
        {notice && <p role="status" className="rounded-xl bg-amber-50 p-3">{notice}</p>}
        <label className="block">Branch <select value={branchId ?? ""} onChange={event => {const id=Number(event.target.value);setBranchId(id);if(profile)rememberAdminBranchId(profile.staffId,id);}} className="ml-3 rounded-lg border p-2">
            {branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select></label>
        {branchId && authorization && hasPermission("MENU_MANAGE") && <OccasionCatalogueEditor key={branchId} branchId={branchId} authorization={authorization} />}
        {requests.map(enquiry => <article key={enquiry.id} className="space-y-3 rounded-2xl border bg-white p-5">
            <h2 className="text-xl font-bold">{enquiry.occasionType} · {prettyDate(enquiry.serviceDate)}</h2>
            <p>{enquiry.guestCount} guests · {enquiry.fulfilment} · {enquiry.status}</p>
            <p>Verified customer: {enquiry.customerPhone}</p>
            {enquiry.deliveryAddress && <p>Requested delivery address: {enquiry.deliveryAddress} (coverage requires staff review)</p>}
            {enquiry.notes && <p>Customer notes: {enquiry.notes}</p>}
            <ul className="list-inside list-disc">{enquiry.items.map(item => <li key={item.productId}>{item.productName}: {item.unit === "GRAM" ? `${item.quantity / 1000} kg` : `${item.quantity.toLocaleString("en-IN")} pieces`}</li>)}</ul>
            {enquiry.gift && <div className="rounded-xl bg-amber-50 p-4 text-sm"><strong>{enquiry.gift.boxCount} boxes · {enquiry.gift.box.name}</strong><p>{enquiry.gift.box.dimensions} · {enquiry.gift.box.material} · {enquiry.gift.box.compartments} compartments · {enquiry.gift.box.capacityPieces} pieces maximum</p><p>{enquiry.gift.box.branding}</p><p>Packaging estimate: {enquiry.gift.packagingEstimate == null ? "Needs review" : `₹${enquiry.gift.packagingEstimate}`}. Approved packaging included in item totals: {enquiry.gift.approvedPackagingTotal == null ? "Awaiting review" : `₹${enquiry.gift.approvedPackagingTotal}`}.</p>{enquiry.gift.recipe.map(line => <p key={line.productId}>{enquiry.items.find(item => item.productId === line.productId)?.productName}: {line.pieces} per box × {enquiry.gift!.boxCount} boxes = {line.pieces * enquiry.gift!.boxCount} pieces</p>)}</div>}
            {enquiry.pricedLines?.length > 0 && <details className="rounded-xl border p-3"><summary className="cursor-pointer text-sm font-semibold">Item prices & tax breakdown</summary><ul className="mt-3 text-sm">{enquiry.pricedLines.map(line => <li key={line.productId}>
                {line.productName}: ₹{line.grossAmount} inclusive (base ₹{line.subtotal}, tax ₹{line.taxAmount} at {line.cgstRate}% CGST + {line.sgstRate}% SGST)
            </li>)}</ul></details>}
            {enquiry.orderNumber && <p>Operational order: {enquiry.orderNumber}</p>}
            {enquiry.quotedAmount != null && <p>{enquiry.estimated&&!enquiry.packingFinalizedAt?"Estimated":"Final quoted"} ₹{enquiry.quotedAmount}; deposit ₹{enquiry.depositAmount}; paid ₹{enquiry.paidAmount}.
                {enquiry.balanceDueAt && ` Balance due ${new Date(enquiry.balanceDueAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST.`} {enquiry.quoteTerms}</p>}
            {!!enquiry.creditReviewAmount&&<p className="rounded-xl border border-amber-300 bg-amber-50 p-4 text-sm font-semibold">Finance action required: ₹{enquiry.creditReviewAmount.toLocaleString("en-IN")} collected above the final packed invoice. Review and reconcile this credit with the customer. No refund has been sent.</p>}
            <p className="text-sm">{enquiry.estimated&&enquiry.status==="PAID"&&!enquiry.packingFinalizedAt?"Advance verified. Pack all requested pieces on the requested date and finalize actual food weights before collecting the remaining balance.":enquiry.nextStep}</p>
            {!!enquiry.productionPlan?.length && <details open={enquiry.status==="CONFIRMED"} className="rounded-xl bg-blue-50 p-3 text-sm"><summary className="min-h-11 cursor-pointer font-semibold">Kitchen plan & prepared quantities</summary><div>
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
            </div></details>}
            {enquiry.cancellationReview && <div className="rounded-xl bg-amber-50 p-3 text-sm">
                <p className="font-semibold">Cancelled — finance review required</p>
                <p>Amount collected ₹{enquiry.cancellationReview.paidAmount}. This is not an approved refund amount or a completed refund.</p>
                <p>Reason: {enquiry.cancellationReview.reason}</p>
            </div>}
            {hasPermission("APPROVAL_MANAGE") && ["PAID", "CONFIRMED"].includes(enquiry.status) && !!enquiry.productionPlan?.length && <details className="rounded-xl border border-red-200 p-3">
                <summary className="cursor-pointer font-semibold">Review cancellation before preparation</summary>
                <p className="mt-2 text-sm">This releases dedicated production and pickup capacity only if preparation has not started. ₹{enquiry.paidAmount} already collected will require finance review under the agreed terms. It does not send a refund. Pending balance payments may still settle and need separate reconciliation.</p>
                <label className="mt-3 block">Cancellation reason (customer-visible)
                    <textarea maxLength={500} value={cancelReason[enquiry.id] ?? ""} onChange={event => setCancelReason(current => ({...current, [enquiry.id]: event.target.value}))} className="mt-1 block w-full rounded-lg border p-2" />
                </label>
                <label className="mt-3 flex items-start gap-2 text-sm"><input type="checkbox" checked={cancelReviewed[enquiry.id] ?? false} onChange={event => setCancelReviewed(current => ({...current, [enquiry.id]: event.target.checked}))} />I reviewed the booking terms and understand finance must decide and reconcile any refund.</label>
                <button type="button" disabled={busy || !cancelReviewed[enquiry.id] || !cancelReason[enquiry.id]?.trim()} onClick={() => void cancelBooking(enquiry)} className="mt-3 min-h-11 rounded-lg border border-red-700 px-4 text-red-800 disabled:opacity-50">Cancel booking and release production</button>
            </details>}
            {authorization && branchId && hasPermission("APPROVAL_MANAGE") && enquiry.estimated && !enquiry.packingFinalizedAt && enquiry.status==="PAID" && <OccasionPackingEditor enquiry={enquiry} branchId={branchId} authorization={authorization} onSaved={updated=>{setRequests(current=>current.map(item=>item.id===updated.id?updated:item));setNotice("Final packed invoice saved. The customer can review their final balance; any credit needs finance review.");}} />}
            {authorization && branchId && hasPermission("APPROVAL_MANAGE") && ["REQUESTED","QUOTED"].includes(enquiry.status) && <OccasionQuoteEditor key={`${branchId}:${enquiry.id}:${enquiry.status}:${enquiry.quotedAmount}`} enquiry={enquiry} branchId={branchId} authorization={authorization} onSaved={updated=>{setRequests(current=>current.map(item=>item.id===updated.id?updated:item));setNotice("Request updated. Customer tracking and notifications reflect the new status.");}} />}
        </article>)}
    </div>;
}
