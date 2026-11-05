"use client";
import {T} from "@/lib/language";


import {useEffect, useState, useRef} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminFetch} from "@/services/adminApi";
import OccasionPlanningDatePicker from "@/components/admin/OccasionPlanningDatePicker";
import OccasionProductionCalendar,{type ProductionWeek,type ProductionProduct} from "@/components/admin/OccasionProductionCalendar";
import OccasionPackingEditor,{type PackingRequest} from "@/components/admin/OccasionPackingEditor";
import OccasionQuoteEditor from "@/components/admin/OccasionQuoteEditor";
import OccasionCatalogueEditor from "@/components/admin/OccasionCatalogueEditor";
import {prettyDate} from "@/components/occasion/OccasionDatePicker";
import PackingPlanView from "@/components/occasion/PackingPlanView";
import type {GiftSnapshot,PackedGroup} from "@/types/occasionCatalogue";
import {preferredAdminBranchId, rememberAdminBranchId} from "@/lib/adminBranchSelection";
import {getActiveBranches} from "@/services/branchApi";

type ReviewQueue = {
    requests: {id: string; occasionType: string; serviceDate: string; guestCount: number}[];
    nextAfter: string | null;
};
type Branch = {id: number; name: string};
type Enquiry = PackingRequest & {id: string; occasionType: string; serviceDate: string; guestCount: number; fulfilment: string;
    customerPhone: string; deliveryAddress: string | null; notes: string | null; status: string;
    gift?: GiftSnapshot | null;packingGroups?:PackedGroup[];
    items: {productId: number; productName: string; quantity: number; supplementalGrams?:number; unit: string; productionUnit?: string; suggestedProductionQuantity?: number | null}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number; taxAmount: number; cgstRate: number; sgstRate: number}[];
    quotedAmount: number | null; depositAmount: number | null; paidAmount: number; balanceDueAt: string | null;
    orderNumber: string | null;
    productionPlan?: {productId: number; quantity: number; supplementalGrams?:number; unit: string; expectedReadyAt: string; state: string; readyQuantity: number; readinessRevision: number}[];
    cancellationReview?: {paidAmount: number; reason: string; state: string} | null;
    quoteTerms: string | null; nextStep: string};

export default function OccasionEnquiriesPage() {
    const {authorization, hasPermission, profile} = useAdminAuth();
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branchId, setBranchId] = useState<number | null>(null);
    const [week,setWeek]=useState<ProductionWeek|null>(null);const [weekFrom,setWeekFrom]=useState("");const [serviceDate,setServiceDate]=useState("");
    const [pages,setPages]=useState(1);const [loading,setLoading]=useState(true);const [refresh,setRefresh]=useState(0);const [refreshedAt,setRefreshedAt]=useState("");
    const [openRequest,setOpenRequest]=useState<string|null>(null);const deepLinkHandled=useRef(false);
    const [reviewTarget,setReviewTarget]=useState<{id:string;branchId:number;syncCalendar?:boolean}|null>(null);
    const [reviewResult,setReviewResult]=useState<{target:NonNullable<typeof reviewTarget>;enquiry:Enquiry|null}|null>(null);
    const calendarSyncedReview=useRef<NonNullable<typeof reviewTarget>|null>(null);
    const [planningError,setPlanningError]=useState("");
    const activeReview=reviewTarget?.branchId===branchId?reviewTarget:null;
    const activeResult=activeReview&&reviewResult?.target===activeReview?reviewResult:null;
    const [queueCursors,setQueueCursors]=useState<string[]>([""]);
    const queueCursor=queueCursors.at(-1)??"";
    const [queue,setQueue]=useState<(ReviewQueue & {branchId:number;cursor:string})|null>(null);
    const [queueError,setQueueError]=useState(false);
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
            const query=new URLSearchParams(window.location.search);
            const linkedBranchId=Number(query.get("branch"));
            const linkedEnquiry=query.get("enquiry");
            setBranches(allowed);
            setBranchId(current=>preferredAdminBranchId(profile!.staffId,allowed,current??linkedBranchId));
            if(!deepLinkHandled.current){
                deepLinkHandled.current=true;
                if(linkedEnquiry&&allowed.some(branch=>branch.id===linkedBranchId)){
                    setReviewTarget(current=>current??{id:linkedEnquiry,branchId:linkedBranchId,syncCalendar:true});
                }
            }
        }}).catch(() => {if (!controller.signal.aborted) setNotice("Could not load branches.");});
        return () => controller.abort();
    }, [authorization, profile]);
    useEffect(() => {
        if (!authorization || !branchId) return;
        const controller=new AbortController();
        let running=false;
        async function loadQueue() {
            if(running || document.visibilityState==="hidden") return;
            running=true;
            try {
                const suffix=queueCursor?`?after=${encodeURIComponent(queueCursor)}`:"";
                const response=await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/review-queue${suffix}`,authorization!,{signal:controller.signal});
                if(!response.ok) throw Error("Could not load review queue.");
                const data=await response.json() as ReviewQueue;
                if(!Array.isArray(data.requests)) throw Error("Invalid review queue response.");
                if(!controller.signal.aborted){setQueue({...data,branchId:branchId!,cursor:queueCursor});setQueueError(false);}
            } catch {
                if(!controller.signal.aborted)setQueueError(true);
            } finally {running=false;}
        }
        void loadQueue();
        const timer=window.setInterval(()=>void loadQueue(),30000);
        const visible=()=>void loadQueue();
        document.addEventListener("visibilitychange",visible);
        return()=>{controller.abort();window.clearInterval(timer);document.removeEventListener("visibilitychange",visible);};
    },[authorization,branchId,queueCursor,refresh]);
    useEffect(() => {
        if(!authorization||!activeReview)return;
        const controller=new AbortController();
        const target=activeReview;
        async function loadReview() {
            try {
                const response=await adminFetch(`/api/admin/branches/${target.branchId}/occasion-enquiries/${encodeURIComponent(target.id)}`,authorization!,{signal:controller.signal});
                if(!response.ok)throw Error("Could not open this request.");
                const enquiry=await response.json() as Enquiry;
                if(!controller.signal.aborted){
                    setReviewResult({target,enquiry});
                    if(target.syncCalendar&&calendarSyncedReview.current!==target){
                        calendarSyncedReview.current=target;
                        setWeekFrom(enquiry.serviceDate);
                        setServiceDate(enquiry.serviceDate);
                        setPages(1);
                        setOpenRequest(null);
                    }
                }
            } catch {
                if(!controller.signal.aborted)setReviewResult({target,enquiry:null});
            }
        }
        void loadReview();
        return()=>controller.abort();
    },[authorization,activeReview,refresh]);
    useEffect(()=>{
        if(activeReview)document.getElementById("occasion-active-review")?.scrollIntoView({block:"start"});
    },[activeReview]);
    useEffect(() => {
        if (!authorization || !branchId) return;
        const controller=new AbortController();let running=false;
        async function load() {
            if(running||document.visibilityState==="hidden")return;running=true;
            try {
                const planning=await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/planning${weekFrom?`?from=${weekFrom}`:""}`,authorization!,{signal:controller.signal});if(!planning.ok)throw Error("Could not refresh production totals.");
                const nextWeek=await planning.json() as ProductionWeek;
                const selected=serviceDate||nextWeek.today;
                const list:Enquiry[]=[];let before="";
                for(let n=0;n<pages;n++) {const response=await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries?serviceDate=${selected}${before?`&before=${before}`:""}`,authorization!,{signal:controller.signal});if(!response.ok)throw Error("Could not refresh bookings.");const batch=await response.json() as Enquiry[];list.push(...batch);if(batch.length<50)break;before=batch.at(-1)!.id;}
                if(!controller.signal.aborted){setWeek(nextWeek);setRequests(list);setPlanningError("");setRefreshedAt(new Date().toLocaleTimeString("en-IN",{timeZone:"Asia/Kolkata",hour:"numeric",minute:"2-digit",second:"2-digit"}));setLoading(false);}
            }catch(error){if(!controller.signal.aborted){setLoading(false);setPlanningError(error instanceof Error?error.message:"Could not refresh. Existing details may be out of date.");}}finally{running=false;}
        }
        void load();const timer=window.setInterval(()=>void load(),30000);const visible=()=>{if(document.visibilityState==="visible")void load();};document.addEventListener("visibilitychange",visible);
        return()=>{controller.abort();window.clearInterval(timer);document.removeEventListener("visibilitychange",visible);};
    },[authorization,branchId,weekFrom,serviceDate,pages,refresh]);
    const activeQueue=queue?.branchId===branchId&&queue.cursor===queueCursor?queue:null;
    const selectedDate=serviceDate||week?.today||"";
    async function approveProduction(product:ProductionProduct) {
        if(!authorization||!branchId||busy||!product.approvalToken)return;setBusy(true);setNotice("");
        try {const response=await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/production/${selectedDate}/${product.productId}/approve`,authorization,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({token:product.approvalToken})});const result=await response.json();if(!response.ok)throw Error(result.message||result.detail||"Commitments changed. Refresh and review the product totals.");setNotice(`${product.name}: ${result.updatedCount} booking allocations approved in dedicated bulk production. Physical readiness is recorded separately.`);setRefresh(value=>value+1);}catch(error){setNotice(error instanceof Error?error.message:"Could not approve production.");}finally{setBusy(false);}
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
            applyEnquiryUpdate(updated);
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
            applyEnquiryUpdate(updated);
            setNotice("Booking cancelled and dedicated production released. Finance must review the amount collected; no refund has been executed.");
        } catch (error) {setNotice(error instanceof Error ? error.message : "Could not cancel this booking.");}
        finally {setBusy(false);}
    }

    function applyEnquiryUpdate(updated:Enquiry) {
        setRequests(current=>current.map(item=>item.id===updated.id?updated:item));
        setReviewResult(current=>current?.enquiry?.id===updated.id?{...current,enquiry:updated}:current);
    }

    function renderEnquiry(enquiry:Enquiry,direct=false) {
        return <details id={`occasion-admin-${enquiry.id}`} key={enquiry.id} open={direct||openRequest===enquiry.id} onToggle={direct?undefined:event=>{if(event.currentTarget.open)setOpenRequest(enquiry.id);else setOpenRequest(current=>current===enquiry.id?null:current);}} className="scroll-mt-28 rounded-2xl border bg-white p-5"><summary className="list-none cursor-pointer"><div className="flex w-full flex-wrap items-center justify-between gap-3"><span><strong className="block text-lg">{enquiry.occasionType}</strong><span className="mt-1 block text-sm">{enquiry.customerPhone} · {enquiry.items.length} items · {enquiry.packingGroups?.length??(enquiry.gift?1:0)} packing groups</span></span><span className="rounded-full bg-[#eef6f1] px-3 py-2 text-sm font-semibold">{enquiry.status.replaceAll("_"," ")} · View details ▾</span></div></summary><article className="mt-5 space-y-3 border-t pt-5">
            <h2 className="text-xl font-bold">{enquiry.occasionType} · {prettyDate(enquiry.serviceDate)}</h2>
            <p>{enquiry.guestCount} guests · {enquiry.fulfilment} · {enquiry.status}</p>
            <p>Verified customer: {enquiry.customerPhone}</p>
            {enquiry.deliveryAddress && <p>Requested delivery address: {enquiry.deliveryAddress} (coverage requires staff review)</p>}
            {enquiry.notes && <p>Customer notes: {enquiry.notes}</p>}
            <ul className="list-inside list-disc">{enquiry.items.map(item => <li key={item.productId}>{item.productName}: {item.unit === "GRAM" ? `${item.quantity / 1000} kg` : `${item.quantity.toLocaleString("en-IN")} pieces`}{item.supplementalGrams?` + ${item.supplementalGrams/1000} kg`:""}</li>)}</ul>
            <PackingPlanView groups={enquiry.packingGroups} items={enquiry.items} />
            {enquiry.gift && <div className="rounded-xl bg-amber-50 p-4 text-sm"><strong>{enquiry.gift.boxCount} boxes · {enquiry.gift.box.name}</strong><p>{enquiry.gift.box.dimensions} · {enquiry.gift.box.material} · {enquiry.gift.box.compartments} compartments · {enquiry.gift.box.capacityPieces} pieces maximum</p><p>{enquiry.gift.box.branding}</p><p>Packaging estimate: {enquiry.gift.packagingEstimate == null ? "Needs review" : `₹${enquiry.gift.packagingEstimate}`}. Approved packaging included in item totals: {enquiry.gift.approvedPackagingTotal == null ? "Awaiting review" : `₹${enquiry.gift.approvedPackagingTotal}`}.</p>{enquiry.gift.recipe.map(line => <p key={line.productId}>{enquiry.items.find(item => item.productId === line.productId)?.productName}: {line.pieces} per box × {enquiry.gift!.boxCount} boxes = {line.pieces * enquiry.gift!.boxCount} pieces</p>)}</div>}
            {enquiry.pricedLines?.length > 0 && <details className="rounded-xl border p-3"><summary className="cursor-pointer text-sm font-semibold">Item prices & tax breakdown</summary><ul className="mt-3 text-sm">{enquiry.pricedLines.map(line => <li key={line.productId}>
                {line.productName}: ₹{line.grossAmount} inclusive (base ₹{line.subtotal}, tax ₹{line.taxAmount}{" "}<T text="at" />{" "}{line.cgstRate}% CGST + {line.sgstRate}% SGST)
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
            {authorization && branchId && hasPermission("APPROVAL_MANAGE") && enquiry.estimated && !enquiry.packingFinalizedAt && enquiry.status==="PAID" && <OccasionPackingEditor enquiry={enquiry} branchId={branchId} authorization={authorization} onSaved={updated=>{applyEnquiryUpdate(updated);setNotice("Final packed invoice saved. The customer can review their final balance; any credit needs finance review.");}} />}
            {authorization && branchId && hasPermission("APPROVAL_MANAGE") && ["REQUESTED","QUOTED"].includes(enquiry.status) && <OccasionQuoteEditor key={`${branchId}:${enquiry.id}:${enquiry.status}:${enquiry.quotedAmount}`} enquiry={enquiry} branchId={branchId} authorization={authorization} onSaved={updated=>{applyEnquiryUpdate(updated);setQueue(current=>current?{...current,requests:current.requests.filter(item=>item.id!==updated.id)}:current);setRefresh(value=>value+1);setNotice("Request updated. Customer tracking and notifications reflect the new status.");}} />}
        </article></details>;
    }

    return <div className="mx-auto max-w-7xl space-y-6 p-4 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-3"><div><h1 className="text-3xl font-bold">Occasion food enquiries</h1><p className="mt-2 text-sm text-stone-600">Daily bulk planning & customer requests{refreshedAt?` · updated ${refreshedAt} IST`:""}</p></div><button type="button" onClick={()=>setRefresh(value=>value+1)} className="min-h-11 rounded-xl border bg-white px-4">Refresh now</button></div>
        <p>Review a request before quoting. A quote is not a confirmed booking. When dedicated bulk production is enabled, approving a pickup quote automatically creates its production plan; the deposit commits it. Daily online stock is unchanged.</p>
        {notice && <p role="status" className="rounded-xl bg-amber-50 p-3">{notice}</p>}
        <label className="block"><T text="Branch" />{" "}<select value={branchId ?? ""} onChange={event => {const id=Number(event.target.value);setBranchId(id);setPlanningError("");setQueue(null);setQueueError(false);setQueueCursors([""]);setReviewTarget(null);setRequests([]);setWeek(null);setServiceDate("");setWeekFrom("");setPages(1);setOpenRequest(null);if(profile)rememberAdminBranchId(profile.staffId,id);}} className="ml-3 rounded-lg border p-2">
            {branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
        </select></label>
        {branchId && <section aria-labelledby="occasion-review-queue" className="space-y-3 rounded-2xl border border-amber-200 bg-amber-50/60 p-4 sm:p-5">
            <div><h2 id="occasion-review-queue" className="text-xl font-bold">Awaiting review</h2><p className="mt-1 text-sm text-stone-700">New requests for this branch, across all pickup dates. Oldest requests are shown first.</p></div>
            {queueError && <p role="status" className="text-sm">Could not refresh the review queue. Use Refresh now to retry.</p>}
            {!activeQueue && !queueError && <p role="status" className="text-sm">Loading requests…</p>}
            {activeQueue && !activeQueue.requests.length && !queueError && <p className="text-sm">{queueCursor?"No more requests on this page.":"No requests awaiting review."}</p>}
            {!!activeQueue?.requests.length && <ul className="divide-y rounded-xl border bg-white px-3">
                {activeQueue.requests.map(item=><li key={item.id} className="flex flex-wrap items-center justify-between gap-2 py-2">
                    <div><strong>{item.occasionType}</strong><p className="text-sm text-stone-700">Requested for {prettyDate(item.serviceDate)} · {item.guestCount} guests</p></div>
                    <button type="button" onClick={()=>setReviewTarget({id:item.id,branchId})} className="min-h-11 rounded-lg border bg-white px-3">{hasPermission("APPROVAL_MANAGE")?"Review request":"View request"}</button>
                </li>)}
            </ul>}
            {(queueCursors.length>1||activeQueue?.nextAfter) && <div className="flex flex-wrap gap-2">
                {queueCursors.length>1&&<button type="button" onClick={()=>setQueueCursors(current=>current.slice(0,-1))} className="min-h-11 rounded-lg border bg-white px-3">Previous requests</button>}
                {activeQueue?.nextAfter&&<button type="button" onClick={()=>setQueueCursors(current=>[...current,activeQueue.nextAfter!])} className="min-h-11 rounded-lg border bg-white px-3">More requests</button>}
            </div>}
        </section>}
        {activeReview&&<section id="occasion-active-review" aria-label="Selected occasion request" className="scroll-mt-28 space-y-3">
            <button type="button" onClick={()=>setReviewTarget(null)} className="min-h-11 rounded-lg border bg-white px-3">Close request</button>
            {!activeResult&&<p role="status">Opening request…</p>}
            {activeResult&&!activeResult.enquiry&&<div role="alert" className="rounded-xl bg-amber-50 p-3">
                <p>Could not open this request. Try again to load its latest details.</p>
                <button type="button" onClick={()=>setReviewTarget({...activeReview})} className="mt-2 min-h-11 rounded-lg border bg-white px-3">Retry opening request</button>
            </div>}
            {activeResult?.enquiry&&renderEnquiry(activeResult.enquiry,true)}
        </section>}
        {branchId && authorization && hasPermission("MENU_MANAGE") && <OccasionCatalogueEditor key={`catalogue-${branchId}`} branchId={branchId} authorization={authorization} />}
        {planningError&&<p role="status" className="rounded-xl bg-amber-50 p-3">{planningError}</p>}
        {loading&&!week&&<p role="status">Loading dates and production totals…</p>}
        {week&&branchId&&authorization&&<OccasionPlanningDatePicker key={`planning-picker-${branchId}`} date={selectedDate} today={week.today} branchId={branchId} authorization={authorization} refreshKey={refreshedAt+":"+refresh} onDate={date=>{setWeekFrom(date);setServiceDate(date);setPages(1);setOpenRequest(null);}} />}
        {week&&<OccasionProductionCalendar week={week} date={selectedDate} onDate={date=>{setServiceDate(date);setPages(1);setOpenRequest(null);}} onWeek={from=>{setWeekFrom(from);setServiceDate(from);setPages(1);setOpenRequest(null);}} onApprove={product=>void approveProduction(product)} canApprove={hasPermission("APPROVAL_MANAGE")} busy={busy} />}
        <h2 className="text-xl font-bold">{selectedDate?prettyDate(selectedDate):"Selected date"} · bookings & requests</h2>
        {!loading&&!requests.length&&<p className="rounded-2xl border bg-white p-5">No requests for this date.</p>}
        {requests.filter(enquiry=>enquiry.serviceDate===selectedDate&&enquiry.id!==activeResult?.enquiry?.id).map(enquiry=>renderEnquiry(enquiry))}
        {requests.length>=pages*50&&<button type="button" onClick={()=>setPages(value=>value+1)} className="min-h-11 rounded-xl border bg-white px-4">Load more bookings for this date</button>}
    </div>;
}
