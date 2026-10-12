"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {T} from "@/lib/language";

import {useEffect,useState} from "react";
import {useSearchParams} from "next/navigation";
import NotificationReadOnOpen from "@/components/customer/NotificationReadOnOpen";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import CustomerIdentityPanel,{type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {apiClient,ApiError} from "@/services/apiClient";
import {getPickupSlots} from "@/services/pickupApi";
import type {PickupSlot} from "@/types/pickup";
import PackingPlanView from "@/components/occasion/PackingPlanView";
import type {GiftSnapshot,PackedGroup} from "@/types/occasionCatalogue";
import {prettyDate} from "@/components/occasion/OccasionDatePicker";
type Item = {productName?: string; productId: number; quantity: number; supplementalGrams?:number; unit: "GRAM" | "PIECE"};
type Enquiry = {calculation?:{foodBase:number;foodTax:number;rebateTotal?:number;bulkRebatePercent?:number;packingCharges?:{groupNumber:number;name:string;quantity:number;pricePerBox:number;total:number}[];packagingTotal:number;lines:{productId:number;name:string;unit:string;unitPrice:number;productionQuantity:number;rebatePercent?:number;rebateAmount?:number}[]}|null;estimated?:boolean;originalEstimate?:number|null;packingFinalizedAt?:string|null;creditReviewAmount?:number|null;extraCharges?:{name:string;quantity:number;priceIncludingTax:number}[];id: string; branchId: number; occasionType: string; serviceDate: string; guestCount: number;
    gift?: GiftSnapshot | null;packingGroups?:PackedGroup[]; status: string; quotedAmount: number | null; depositAmount: number | null; paidAmount: number;
    quoteTerms: string | null; quoteExpiresAt: string | null; balanceDueAt: string | null;
    nextStep: string; fulfilment: string; items: Item[]; orderNumber: string | null; balancePaymentOpen: boolean; cancellationReview?: {paidAmount: number; reason: string; state: string} | null; productionPlan?: {productId: number; unit: string; expectedReadyAt: string; state: string; quantity: number; readyQuantity: number}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number;
        taxAmount: number; cgstRate: number; sgstRate: number}[]};
type Checkout = {attemptId: string; stage: string; status: string; amount: number; expiresAt: string; paymentUrl: string | null};


const money=(value:number)=>new Intl.NumberFormat("en-IN",{style:"currency",currency:"INR"}).format(value);
export default function OccasionRequests({embeddedSession}:{embeddedSession?:CustomerSession}={}) {
    const query=useSearchParams();const target=query.get("enquiry")??"";
    const features=useStorefrontFeatures();
    const [localSession,setSession]=useState<CustomerSession>({authenticated:false});
    const session=embeddedSession??localSession;
    const [historyError,setHistoryError]=useState(false);
    const [loading,setLoading]=useState(true);const [retry,setRetry]=useState(0);
    const [history,setHistory]=useState<Enquiry[]>([]);const [historyPhone,setHistoryPhone]=useState("");
    const [hasEarlier,setHasEarlier]=useState(false);
    const [historyLimit,setHistoryLimit]=useState(10);const [historySearch,setHistorySearch]=useState("");
    const [slots,setSlots]=useState<Record<string,PickupSlot[]>>({});const [selectedSlots,setSelectedSlots]=useState<Record<string,number>>({});
    const [attempts,setAttempts]=useState<Record<string,Checkout>>({});const [busy,setBusy]=useState(false);const [message,setMessage]=useState("");
    const [accepted,setAccepted]=useState<Record<string,boolean>>({});
    useEffect(()=>{if(target&&history.length)document.getElementById(`occasion-${target}`)?.scrollIntoView({block:"start"});},[target,history.length]);
    useEffect(() => {
        if (!session.authenticated || !features?.occasionEnquiries) return;
        let cancelled = false;
        const load = () => {void apiClient<Enquiry[]>("/api/occasion-enquiries", {credentials: "include", signal:AbortSignal.timeout(8000)})
            .then(async list => {
                if(target&&!list.some(item=>item.id===target)&&/^[0-9a-f-]{36}$/i.test(target)) {
                    try {list=[await apiClient<Enquiry>(`/api/occasion-enquiries/${target}`,{credentials:"include",signal:AbortSignal.timeout(8000)}),...list];}
                    catch {if(!cancelled)setMessage("This request is unavailable for your signed-in account.");}
                }
                if(!cancelled){setHistory(current=>{const ids=new Set(list.map(item=>item.id));return [...list,...(historyPhone===(session.phone??"")?current.filter(item=>!ids.has(item.id)):[])];});setHistoryPhone(session.phone??"");setHasEarlier(list.length>=100);setLoading(false);setHistoryError(false);}
            })
            .catch(error => {if (!cancelled) {setLoading(false);setHistoryError(true);if(error instanceof ApiError&&[401,403].includes(error.status)){setHistory([]);setHistoryPhone("");}setMessage("We couldn't load your enquiries. Please retry.");}});};
        load();
        const timer = window.setInterval(load, 60_000);
        return () => {cancelled = true; window.clearInterval(timer);};
    }, [session.authenticated, session.phone, features?.occasionEnquiries,target,historyPhone,retry]);

    useEffect(() => {
        if (!session.authenticated || !features?.occasionPayments) return;
        const query = new URLSearchParams(window.location.search);
        const enquiry = query.get("enquiry");
        const attempt = query.get("payment");
        if (!enquiry || !attempt || !/^[0-9a-f-]{36}$/i.test(enquiry) || !/^[0-9a-f-]{36}$/i.test(attempt)) return;
        let cancelled = false;
        const timer = window.setInterval(() => check(),5000);
        const check = () => {void apiClient<Checkout>(`/api/occasion-enquiries/${enquiry}/payments/${attempt}`,
            {credentials: "include"}).then(result => {
            if (cancelled) return;
            setAttempts(current => ({...current, [enquiry]: result}));
            if (result.status !== "PENDING") {
                window.clearInterval(timer);
                void apiClient<Enquiry[]>("/api/occasion-enquiries", {credentials: "include"})
                    .then(list => {if (!cancelled) setHistory(list);});
            }
        }).catch(() => {if (!cancelled) setMessage("Payment status is not available yet. Please retry from this page; do not pay again.");});};
        check();

        return () => {cancelled = true; window.clearInterval(timer);};
    }, [session.authenticated, features?.occasionPayments]);

    async function loadSlots(enquiry: Enquiry) {
        setBusy(true); setMessage("");
        try {
            const available = await getPickupSlots(enquiry.branchId, enquiry.serviceDate);
            setSlots(current => ({...current, [enquiry.id]: available.filter(slot => slot.active && slot.remainingCapacity > 0 && (!enquiry.productionPlan?.[0] || slot.startTime >= enquiry.productionPlan[0].expectedReadyAt.slice(11,19)))}));
        } catch {setMessage("Pickup times could not be loaded. Please retry.");}
        finally {setBusy(false);}
    }

    async function pay(enquiry: Enquiry, stage: "deposit" | "balance") {
        if (busy || stage === "deposit" && !selectedSlots[enquiry.id]) return;
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Checkout>(`/api/occasion-enquiries/${enquiry.id}/${stage}`,
                {method: "POST", credentials: "include", body: JSON.stringify(stage === "deposit"
                    ? {pickupSlotId: selectedSlots[enquiry.id],estimateAccepted:accepted[enquiry.id]??false} : {})});
            setAttempts(current => ({...current, [enquiry.id]: result}));
            if (result.paymentUrl) window.location.assign(result.paymentUrl);
            else setMessage("Checkout is being prepared. Refresh this page to check its status before retrying.");
        } catch (error) {
            setMessage(error instanceof ApiError ? error.message : "Payment could not start. Check the request status before retrying.");
        } finally {setBusy(false);}
    }

    async function checkPayment(enquiry: Enquiry) {
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Checkout>(`/api/occasion-enquiries/${enquiry.id}/payments/latest`,
                {credentials: "include"});
            setAttempts(current => ({...current, [enquiry.id]: result}));
            const list = await apiClient<Enquiry[]>("/api/occasion-enquiries", {credentials: "include"});
            setHistory(list);
        } catch {setMessage("Payment status is unavailable. Please wait and check again; do not start another payment.");}
        finally {setBusy(false);}
    }


    async function loadEarlier() {
        if(busy||!history.length)return;setBusy(true);
        try {const list=await apiClient<Enquiry[]>(`/api/occasion-enquiries?before=${encodeURIComponent(history[history.length-1].id)}`,{credentials:"include"});setHistory(current=>[...current,...list.filter(item=>!current.some(known=>known.id===item.id))]);setHasEarlier(list.length===100);setHistoryLimit(current=>current+10);}
        catch {setMessage("Earlier requests could not be loaded. Please retry.");}finally{setBusy(false);}
    }
    const matches=history.filter(enquiry=>`${enquiry.occasionType} ${enquiry.serviceDate} ${enquiry.orderNumber??""} ${enquiry.status}`.toLowerCase().includes(historySearch.toLowerCase())).sort((a,b)=>a.id===target?-1:b.id===target?1:0);
    const content=<div className={embeddedSession?"occasion-journey profile-bulk-history":"occasion-journey mx-auto max-w-5xl px-4 py-8 text-[#173a37] sm:px-6"}>
        {!embeddedSession&&<header className="occasion-hero"><p className="text-xs font-bold uppercase tracking-widest">Your Gokul celebrations</p><h1 className="mt-2 font-serif text-4xl">Requests & quotes</h1><p className="mt-3">Your request, price, payments and pickup updates — together in one place.</p><div className="occasion-hero-actions"><Link href="/occasions" className="occasion-primary">Plan another occasion</Link></div></header>}
        {!features&&<p role="status" className="mt-6">Checking availability…</p>}
        {features&&!features.occasionEnquiries&&<p className="mt-6">Occasion requests are not available at the moment.</p>}
        {features?.occasionEnquiries&&<>{session.authenticated&&historyPhone===(session.phone??"")&&history.some(item=>item.id===target)&&<NotificationReadOnOpen orderNumber={target} targetType="OCCASION" />}{!embeddedSession&&<div className="mt-6 rounded-2xl border bg-white p-4"><CustomerIdentityPanel mode="occasion" onSessionChange={setSession} /></div>}
                {session.authenticated&&loading&&<BrandLoading compact label="Loading your requests…" />}
                {session.authenticated && historyPhone === (session.phone ?? "") && <section id="occasion-tracker" className="occasion-tracker scroll-mt-28 mt-7"><div className="occasion-tracker-heading"><div><h2 className="text-lg font-bold">Your requests</h2><p className="text-sm">Open a request to review its quote and next step.</p></div><span>{history.length} requests</span></div><label className="mt-4 block text-sm">Find a request<input type="search" value={historySearch} onChange={event=>{setHistorySearch(event.target.value);setHistoryLimit(10);}} placeholder="Occasion, date or order number" className="mt-1 w-full rounded-xl border p-3" /></label>{!matches.length&&<p className="mt-4">{history.length?"No matching requests.":"Your requests and quotes will appear here. Start a celebration from Occasions & gifting."}</p>}
                    {matches.slice(0,historyLimit).map(enquiry => <article id={`occasion-${enquiry.id}`} key={enquiry.id} className="scroll-mt-28 mt-4 rounded-2xl border bg-white p-5">
                        <div className="flex flex-wrap justify-between gap-2"><strong>{enquiry.occasionType} · {prettyDate(enquiry.serviceDate)}</strong><span className="occasion-status">{enquiry.estimated&&enquiry.status==="PAID"&&!enquiry.packingFinalizedAt?"Advance received — packing pending":({REQUESTED: "Under branch review", QUOTED: "Quote ready — deposit due", PAYMENT_PENDING: "Deposit payment in progress", HELD: "Deposit payment in progress", PAID: "Deposit received — balance due", CONFIRMED: "Pickup confirmed", EXPIRED: "Quote or payment window expired", DECLINED: "Request declined", CANCELLED: "Cancelled — finance review pending"}[enquiry.status] ?? "Contact the branch")}</span></div>
                        <p className="mt-2 font-semibold">{enquiry.estimated&&enquiry.status==="PAID"&&!enquiry.packingFinalizedAt?"Advance received. The branch will finalize your invoice after packing on pickup day.":enquiry.nextStep}</p>{enquiry.quotedAmount!=null&&<p className="mt-2 text-lg font-bold">{enquiry.estimated&&!enquiry.packingFinalizedAt?"Estimated":"Total"} {money(enquiry.quotedAmount)} <span className="text-sm font-normal">· paid {money(enquiry.paidAmount)}</span></p>}<div className="occasion-progress" aria-label="Request milestones">{["Request received","Branch quote","Deposit","Pickup confirmed"].map((label,index)=><span key={label} className={index<=({REQUESTED:0,QUOTED:1,PAYMENT_PENDING:1,HELD:1,PAID:2,CONFIRMED:3}[enquiry.status]??-1)?"is-reached":""}>{label}</span>)}</div><details className="occasion-request-details" open={enquiry.id===target}><summary>{enquiry.quotedAmount==null?"View request details":`Review quote ₹${enquiry.quotedAmount.toLocaleString("en-IN")} & payment`}</summary>
                        <ul className="mt-3 text-sm">{enquiry.items.map(item => <li key={item.productId}><T text="Requested:" />{" "}{item.productName ?? enquiry.pricedLines?.find(line=>line.productId===item.productId)?.productName ?? "Item"} · {item.unit === "GRAM" ? `${item.quantity / 1000} kg` : `${item.quantity.toLocaleString("en-IN")} pieces`}{item.supplementalGrams?` + ${item.supplementalGrams/1000} kg`:""}</li>)}</ul>
                        <PackingPlanView groups={enquiry.packingGroups} items={enquiry.items} />
                        {enquiry.gift && <div className="mt-3 rounded-xl bg-[#fffaf2] p-3 text-sm"><strong>{enquiry.gift.boxCount} × {enquiry.gift.box.name}</strong><p>{enquiry.gift.box.dimensions} · {enquiry.gift.box.material} · {enquiry.gift.box.branding}</p><p>{enquiry.gift.approvedPackagingTotal == null ? "Packaging is awaiting manager price and fit review." : `Packaging ₹${enquiry.gift.approvedPackagingTotal}, included in the approved item totals.`}</p></div>}
                        {enquiry.quotedAmount != null && <dl className="my-4 grid grid-cols-2 gap-3 rounded-xl bg-[#f3f8f4] p-4 text-sm"><dt className="font-bold">{enquiry.estimated&&!enquiry.packingFinalizedAt?"Estimated total including tax":"Final total including tax"}</dt><dd className="text-right font-bold">{money(enquiry.quotedAmount)}</dd><dt>Paid so far</dt><dd className="text-right">{money(enquiry.paidAmount)}</dd><dt>Remaining balance</dt><dd className="text-right">{money(Math.max(0,enquiry.quotedAmount-enquiry.paidAmount))}</dd>{enquiry.status==="QUOTED"&&<><dt>Advance to book</dt><dd className="text-right font-bold">{money(enquiry.depositAmount??0)}</dd></>}</dl>}
                        {enquiry.estimated&&<div className="mt-3 rounded-xl bg-[#fff0dc] p-4 text-sm"><strong>{enquiry.packingFinalizedAt?"Packing complete — final measured invoice":"Booking estimate — final weight after packing"}</strong><p className="mt-1">{enquiry.packingFinalizedAt?`Original estimate ${money(enquiry.originalEstimate??0)}. Your advance is credited against the final total.`:"Your requested piece counts stay fixed. Actual packed food weight on pickup day determines the final price at the agreed rates. Boxes and accessories are included separately. The remaining balance opens after the branch finishes packing."}</p></div>}
                        {enquiry.calculation&&<details className="mt-3 rounded-xl border p-3 text-sm"><summary className="cursor-pointer font-semibold">How your price is calculated</summary><ul className="mt-3 space-y-2">{enquiry.calculation.lines.map(line=><li key={line.productId}><strong>{line.name}</strong>: {line.unit==="GRAM"?`${line.productionQuantity/1000} kg`:`${line.productionQuantity} pieces`} × {money(line.unitPrice)} per {line.unit==="GRAM"?"kg":"piece"}{" "}<T text="before tax" />{line.rebateAmount?`; food rebate ${money(line.rebateAmount)}`:""}.</li>)}</ul><p className="mt-3">Food rebates before tax: {money(enquiry.calculation.rebateTotal??0)}. Food after rebates {money(enquiry.calculation.foodBase)} + food tax {money(enquiry.calculation.foodTax)} + packaging and accessories {money(enquiry.calculation.packagingTotal)} = {money(enquiry.quotedAmount??0)}.</p>{enquiry.calculation.packingCharges?.map(charge=><p key={charge.groupNumber}>Packing group {charge.groupNumber}: {charge.quantity} × {charge.name}, {money(charge.pricePerBox)} each = {money(charge.total)}.</p>)}</details>}
                        {!!enquiry.extraCharges?.length&&<ul className="mt-3 text-sm">{enquiry.extraCharges.map((extra,index)=><li key={index}>{extra.name}: {extra.quantity.toLocaleString("en-IN")} × {money(extra.priceIncludingTax)} = {money(extra.quantity*extra.priceIncludingTax)}, included in the total.</li>)}</ul>}
                        {!!enquiry.creditReviewAmount&&<p className="mt-3 rounded-xl bg-[#fff0dc] p-3 text-sm">You paid {money(enquiry.creditReviewAmount)} above the final invoice. The branch must review this credit; a refund is not confirmed yet.</p>}
                        {enquiry.pricedLines?.length > 0 && <details className="mt-3 rounded-xl border border-[#d9e5df] p-3 text-sm">
                            <summary className="cursor-pointer font-semibold">Item prices & tax breakdown</summary>
                            {enquiry.pricedLines.map(line => <p key={line.productId} className="mt-1">{line.productName}: ₹{line.grossAmount} (base ₹{line.subtotal}, tax ₹{line.taxAmount}; {line.cgstRate}% CGST + {line.sgstRate}% SGST)</p>)}
                        </details>}
                        {enquiry.orderNumber && <p className="mt-2">Confirmed order <Link href={`/orders/${encodeURIComponent(enquiry.orderNumber)}`} className="underline">{enquiry.orderNumber}</Link></p>}
                        {enquiry.productionPlan?.[0] && <p className="mt-3 text-sm">Planned ready time: {prettyDate(enquiry.serviceDate)}{" "}<T text="at" />{" "}{enquiry.productionPlan[0].expectedReadyAt.slice(11,16)} IST. Follow your linked order for preparation updates.</p>}
                        {enquiry.cancellationReview && <div className="mt-3 rounded-xl bg-[#fff0dc] p-3 text-sm">
                            <p>Cancellation reason: {enquiry.cancellationReview.reason}</p>
                            <p>₹{enquiry.cancellationReview.paidAmount} already paid needs branch finance review under your booking terms. No refund is confirmed yet. Please contact the branch.</p>
                        </div>}
                        {enquiry.quoteTerms && <p className="mt-2">{enquiry.quoteTerms}</p>}
                        {enquiry.balanceDueAt && (!enquiry.estimated||enquiry.packingFinalizedAt) && <p className="mt-2">Balance due {new Date(enquiry.balanceDueAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST.</p>}
                        {features.occasionPayments && enquiry.status === "QUOTED" && enquiry.fulfilment === "PICKUP" && <div className="mt-4 space-y-3">
                            <button type="button" disabled={busy} onClick={() => void loadSlots(enquiry)} className="min-h-11 rounded-full border border-[#173a37] px-5">Choose a live pickup time</button>
                            {slots[enquiry.id] && <fieldset><legend className="text-sm font-semibold">Pickup time on {prettyDate(enquiry.serviceDate)} · IST</legend><div className="mt-2 flex flex-wrap gap-2">{slots[enquiry.id].map(slot=><button key={slot.id} type="button" aria-pressed={selectedSlots[enquiry.id]===slot.id} onClick={()=>setSelectedSlots(current=>({...current,[enquiry.id]:slot.id}))} className={`min-h-12 rounded-xl border px-4 ${selectedSlots[enquiry.id]===slot.id?"bg-[#143936] text-white":"bg-white"}`}>{slot.startTime.slice(0,5)}–{slot.endTime.slice(0,5)}</button>)}</div></fieldset>}
                            {slots[enquiry.id] && slots[enquiry.id].length === 0 && <p>No pickup capacity remains on this date. Contact the branch for a new quote.</p>}
                            {enquiry.estimated&&<label className="flex gap-2 rounded-xl border p-3 text-sm"><input type="checkbox" checked={accepted[enquiry.id]??false} onChange={event=>setAccepted(current=>({...current,[enquiry.id]:event.target.checked}))} />I understand this is an estimate: actual packed weights at the agreed rates determine my final invoice; the advance is credited and I can review the balance before collection.</label>}
                            <button type="button" disabled={busy || !selectedSlots[enquiry.id] || enquiry.estimated&&!accepted[enquiry.id]} onClick={() => void pay(enquiry, "deposit")}
                                className="min-h-11 rounded-full bg-[#c76752] px-5 font-bold text-white disabled:opacity-50">Pay advance {money(enquiry.depositAmount??0)}</button>
                        </div>}
                        {features.occasionPayments && enquiry.status === "PAID" && enquiry.quotedAmount != null
                            && enquiry.quotedAmount > enquiry.paidAmount && enquiry.balancePaymentOpen && <button type="button" disabled={busy}
                            onClick={() => void pay(enquiry, "balance")}
                            className="mt-4 min-h-11 rounded-full bg-[#c76752] px-5 font-bold text-white disabled:opacity-50">Pay balance {money(enquiry.quotedAmount - enquiry.paidAmount)}</button>}
                        {features.occasionPayments && ["PAYMENT_PENDING", "HELD", "PAID", "CONFIRMED", "EXPIRED", "CANCELLED"].includes(enquiry.status)
                            && <button type="button" disabled={busy} onClick={() => void checkPayment(enquiry)}
                            className="mt-4 ml-2 min-h-11 rounded-full border border-[#173a37] px-5 disabled:opacity-50">Check latest payment</button>}
                        {attempts[enquiry.id] && <p role="status" className="mt-3 rounded-xl bg-[#fff0dc] p-3">{attempts[enquiry.id].status === "REFUND_PENDING"
                            ? "A late payment needs branch refund review. Please do not pay again; contact the branch."
                            : `Payment ${attempts[enquiry.id].status.toLowerCase()}.`} {attempts[enquiry.id].status === "PENDING" && attempts[enquiry.id].paymentUrl
                            && <a className="underline" href={attempts[enquiry.id].paymentUrl ?? undefined}>Resume secure checkout</a>}</p>}
                    </details></article>)}
                    {matches.length>historyLimit&&<button type="button" onClick={()=>setHistoryLimit(current=>current+10)} className="min-h-11 rounded-xl border px-4">Show more requests</button>}
                    {hasEarlier&&historyLimit>=matches.length&&<button type="button" disabled={busy} onClick={()=>void loadEarlier()} className="min-h-11 rounded-xl border px-4">Load earlier requests</button>}
                </section>}
</>}
        {message&&<div role="status" className="mt-4 rounded-xl bg-[#fff0dc] p-4">{message}{historyError&&<button type="button" className="ml-2 min-h-11 underline" onClick={()=>{setMessage("");setLoading(true);setRetry(value=>value+1);}}>Try again</button>}</div>}
    </div>;
    return embeddedSession?content:<AppShell editorial showSocialPopup={false}><main>{content}</main></AppShell>;
}
