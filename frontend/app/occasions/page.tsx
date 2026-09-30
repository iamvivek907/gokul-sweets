/* eslint-disable @next/next/no-img-element -- Real catalogue URLs are rendered directly without transforming supplier photos. */
"use client";

import {useEffect, useState} from "react";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import CustomerIdentityPanel, {type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {apiClient, ApiError} from "@/services/apiClient";
import type {OccasionSweet, OccasionBox, OccasionCatalogue, GiftSnapshot} from "@/types/occasionCatalogue";
import {getPickupSlots} from "@/services/pickupApi";
import type {PickupSlot} from "@/types/pickup";

type Item = {productId: number; quantity: number; unit: "GRAM" | "PIECE"};
type Enquiry = {id: string; branchId: number; occasionType: string; serviceDate: string; guestCount: number;
    gift?: GiftSnapshot | null; status: string; quotedAmount: number | null; depositAmount: number | null; paidAmount: number;
    quoteTerms: string | null; quoteExpiresAt: string | null; balanceDueAt: string | null;
    nextStep: string; fulfilment: string; items: Item[]; orderNumber: string | null; balancePaymentOpen: boolean; cancellationReview?: {paidAmount: number; reason: string; state: string} | null; productionPlan?: {productId: number; unit: string; expectedReadyAt: string; state: string; quantity: number; readyQuantity: number}[];
    pricedLines: {productId: number; productName: string; grossAmount: number; subtotal: number;
        taxAmount: number; cgstRate: number; sgstRate: number}[]};
type Checkout = {attemptId: string; stage: string; status: string; amount: number; expiresAt: string; paymentUrl: string | null};

function nextBusinessDate(today: string): string {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) return today;
    const date = new Date(`${today}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10);
}

export default function OccasionsPage() {
    const features = useStorefrontFeatures();
    const {branch} = useSelectedBranch();
    const [sessionVersion, setSessionVersion] = useState(0);
    const [session, setSession] = useState<CustomerSession>({authenticated: false});
    const [catalogueSearch, setCatalogueSearch] = useState("");
    const [specialOnly, setSpecialOnly] = useState(false);
    const [products, setProducts] = useState<OccasionSweet[]>([]);
    const [boxes, setBoxes] = useState<OccasionBox[]>([]);
    const [boxId, setBoxId] = useState<number | null>(null);
    const [boxCount, setBoxCount] = useState(700);
    const [units, setUnits] = useState<Record<number, "KG" | "PIECE">>({});
    const [items, setItems] = useState<Record<number, number>>({});
    const [history, setHistory] = useState<Enquiry[]>([]);
    const [historyPhone, setHistoryPhone] = useState("");
    const [slots, setSlots] = useState<Record<string, PickupSlot[]>>({});
    const [selectedSlots, setSelectedSlots] = useState<Record<string, number>>({});
    const [attempts, setAttempts] = useState<Record<string, Checkout>>({});
    const [type, setType] = useState("Family celebration");
    const [date, setDate] = useState("");
    const [guests, setGuests] = useState(20);
    const [mode, setMode] = useState<"PICKUP" | "DELIVERY_REQUEST">("PICKUP");
    const [address, setAddress] = useState("");
    const [notes, setNotes] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");

    useEffect(() => {
        const hash = window.location.hash.slice(1);
        if (hash.startsWith("occasion-") && history.length)
            document.getElementById(hash)?.scrollIntoView({block: "start"});
    }, [history]);

    useEffect(() => {
        if (!branch || !features?.occasionEnquiries) return;
        const controller = new AbortController();
        apiClient<OccasionCatalogue>(`/api/branches/${branch.id}/occasion-catalogue`, {signal: controller.signal}).then(catalogue => {
            if (!controller.signal.aborted) {setProducts(catalogue.sweets); setBoxes(catalogue.boxes); setItems({}); setBoxId(null);}
        }).catch(() => {if (!controller.signal.aborted) setMessage("Menu unavailable. Please retry before requesting a quote.");});
        return () => controller.abort();
    }, [branch, features?.occasionEnquiries]);

    useEffect(() => {
        if (!session.authenticated || !features?.occasionEnquiries) return;
        let cancelled = false;
        const load = () => {void apiClient<Enquiry[]>("/api/occasion-enquiries", {credentials: "include"})
            .then(list => {if (!cancelled) {setHistory(list); setHistoryPhone(session.phone ?? "");}})
            .catch(() => {if (!cancelled) setMessage("We couldn't load your enquiries. Please retry.");});};
        load();
        const timer = window.setInterval(load, 60_000);
        return () => {cancelled = true; window.clearInterval(timer);};
    }, [session.authenticated, session.phone, features?.occasionEnquiries]);

    useEffect(() => {
        if (!session.authenticated || !features?.occasionPayments) return;
        const query = new URLSearchParams(window.location.search);
        const enquiry = query.get("enquiry");
        const attempt = query.get("payment");
        if (!enquiry || !attempt || !/^[0-9a-f-]{36}$/i.test(enquiry) || !/^[0-9a-f-]{36}$/i.test(attempt)) return;
        let cancelled = false;
        const check = () => {void apiClient<Checkout>(`/api/occasion-enquiries/${enquiry}/payments/${attempt}`,
            {credentials: "include"}).then(result => {
            if (cancelled) return;
            setAttempts(current => ({...current, [enquiry]: result}));
            if (result.status !== "PENDING") {
                void apiClient<Enquiry[]>("/api/occasion-enquiries", {credentials: "include"})
                    .then(list => {if (!cancelled) setHistory(list);});
            }
        }).catch(() => {if (!cancelled) setMessage("Payment status is not available yet. Please retry from this page; do not pay again.");});};
        check();
        const timer = window.setInterval(check, 5000);
        return () => {cancelled = true; window.clearInterval(timer);};
    }, [session.authenticated, features?.occasionPayments]);

    async function loadSlots(enquiry: Enquiry) {
        setBusy(true); setMessage("");
        try {
            const available = await getPickupSlots(enquiry.branchId, enquiry.serviceDate);
            setSlots(current => ({...current, [enquiry.id]: available.filter(slot => slot.active && slot.remainingCapacity > 0)}));
        } catch {setMessage("Pickup times could not be loaded. Please retry.");}
        finally {setBusy(false);}
    }

    async function pay(enquiry: Enquiry, stage: "deposit" | "balance") {
        if (busy || stage === "deposit" && !selectedSlots[enquiry.id]) return;
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Checkout>(`/api/occasion-enquiries/${enquiry.id}/${stage}`,
                {method: "POST", credentials: "include", body: JSON.stringify(stage === "deposit"
                    ? {pickupSlotId: selectedSlots[enquiry.id]} : {})});
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

    async function submit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!branch || busy) return;
        if (!session.authenticated) {setMessage("Verify your phone before sending your enquiry. Your selection is saved on this page."); return;}
        if (!date || date <= (features?.today ?? "")) {setMessage("Choose a future service date in India."); return;}
        const chosen = products.filter(product => Number(items[product.id]) > 0).map(product => ({
            productId: product.id, quantity: Number(items[product.id]) * (boxId ? boxCount : (units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")) === "KG" ? 1000 : 1), unit: boxId || (units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")) === "PIECE" ? "PIECE" : "GRAM"
        }));
        if (!chosen.length) {setMessage("Add at least one product and quantity."); return;}
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Enquiry>("/api/occasion-enquiries", {method: "POST", credentials: "include",
                body: JSON.stringify({branchId: branch.id, occasionType: type, serviceDate: date, guestCount: guests,
                    fulfilment: mode, deliveryAddress: mode === "DELIVERY_REQUEST" ? address : null, notes, items: chosen, gift: boxId ? {boxId, boxCount, recipe: products.filter(product => Number(items[product.id]) > 0).map(product => ({productId: product.id, pieces: Number(items[product.id])}))} : null})});
            setHistory(current => [result, ...current]);
            setHistoryPhone(session.phone ?? "");
            setMessage("Request sent. The branch will review it before sharing a quote. No booking or payment has been made.");
            setItems({});
        } catch (error) {
            if (error instanceof ApiError && error.status === 401) {
                setSession({authenticated: false}); setHistory([]); setSessionVersion(current => current + 1);
                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
            }
            setMessage(error instanceof ApiError && error.status === 401 ? "Please verify your phone, then try again."
                : error instanceof ApiError && error.status === 429 ? "You have sent three enquiries in the last 24 hours. Please contact the branch for changes."
                : error instanceof ApiError && error.status === 400 ? error.message
                : "The request could not be sent. Your entries are still here; please retry.");
        } finally {setBusy(false);}
    }

    return <AppShell editorial showSocialPopup={false}>
        <div className="occasion-journey mx-auto max-w-5xl px-4 py-8 text-[#173a37] sm:px-6">
            <header className="occasion-hero"><p className="text-sm font-bold uppercase tracking-widest text-[#b55f4a]">Occasions at Gokul</p>
            <h1 className="mt-3 font-serif text-4xl sm:text-6xl">Sweet moments. Thoughtfully planned.</h1>
            <p className="mt-4 max-w-2xl">Share the date, guests and food you have in mind. Our team reviews availability and gives you a clear quote before any payment.</p><div className="mt-6 flex flex-wrap gap-2 text-sm"><span>Weddings & family celebrations</span><span>Corporate gifting</span><span>Made-to-order sweets</span></div></header>
            {!features && <p role="status" className="mt-8">Checking availability…</p>}
            {features && !features.occasionEnquiries && <p className="mt-8 rounded-xl bg-white p-6">Occasion enquiries are not available yet. <Link href="/menu" className="underline">Explore pickup ordering</Link>.</p>}
            {features?.occasionEnquiries && <>
                {!branch ? <p className="mt-8 rounded-xl bg-white p-6">Choose a branch first. <Link href="/branches" className="underline">Explore branches</Link>.</p> : <>
                    <p className="mt-5 font-semibold">Planning with {branch.name} · <Link href="/branches" className="underline">Change branch</Link></p>
                    <div className="mt-7 rounded-2xl border border-[#d9e5df] bg-white p-5"><CustomerIdentityPanel key={sessionVersion} mode="occasion" onSessionChange={setSession} /></div>
                    {<form onSubmit={submit} className="mt-7 space-y-5 rounded-2xl border border-[#d9e5df] bg-white p-5 sm:p-8">
                        <p className="text-xs font-bold uppercase tracking-widest text-[#c76752]">01 · Plan your celebration</p><p className="text-sm">Choose your sweets and packaging. Our branch reviews production, box fit and pricing before you pay.</p>
                        <label className="block">Occasion <input required maxLength={80} value={type} onChange={event => setType(event.target.value)} className="mt-2 w-full rounded-xl border p-3" /></label>
                        <div className="grid gap-4 sm:grid-cols-2">
                            <label>Date in India <input type="date" required min={nextBusinessDate(features.today)} value={date} onChange={event => setDate(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>
                            <label>Guests <input type="number" required min={1} max={10000} value={guests} onChange={event => setGuests(Number(event.target.value))} className="mt-2 block w-full rounded-xl border p-3" /></label>
                        </div>
                        <label className="block">How should food be collected?
                            <select value={mode} onChange={event => setMode(event.target.value as typeof mode)} className="mt-2 block w-full rounded-xl border p-3">
                                <option value="PICKUP">Pickup at the branch</option><option value="DELIVERY_REQUEST">Ask about delivery (not confirmed)</option>
                            </select>
                        </label>
                        {mode === "DELIVERY_REQUEST" && <label className="block">Delivery address for review <textarea required maxLength={500} value={address} onChange={event => setAddress(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>}
                        <fieldset><legend className="font-serif text-2xl">02 · Choose how to present your sweets</legend>
                            <div className="mt-3 grid gap-3 sm:grid-cols-2"><button type="button" aria-pressed={!boxId} onClick={() => {setBoxId(null); setItems({});}} className={`rounded-2xl border p-5 text-left ${!boxId ? "border-[#c76752] bg-[#fffaf2]" : ""}`}><strong>Bulk sweets</strong><p className="mt-1 text-sm">Order in kilograms or pieces for your gathering.</p></button>{boxes.map(box => <button type="button" key={box.id} aria-pressed={boxId === box.id} onClick={() => {setBoxId(box.id); setItems({});}} className={`overflow-hidden rounded-2xl border text-left ${boxId === box.id ? "border-[#c76752] bg-[#fffaf2]" : ""}`}>
                                {box.imageUrl ? <img src={box.imageUrl} alt={`${box.name} packaging`} className="h-40 w-full object-cover" /> : <div className="flex h-32 items-center justify-center bg-[#f1eee5] text-sm">Packaging photo coming soon</div>}
                                <div className="p-4"><strong>{box.name}</strong><p className="mt-1 text-sm">{box.dimensions} · {box.material}</p><p className="text-sm">{box.compartments} compartments · up to {box.capacityPieces} pieces</p><p className="text-sm">{box.price == null ? "Packaging price on review" : `Packaging estimate ₹${box.price} per box`} · {box.leadDays} days lead time</p>{box.branding && <p className="text-sm">{box.branding}</p>}</div></button>)}</div>
                            {boxId && <label className="mt-4 block">Number of gift boxes<input type="number" min={1} max={10000} required value={boxCount} onChange={event => setBoxCount(Number(event.target.value))} className="ml-3 w-32 rounded-xl border p-3" /><span className="mt-2 block text-sm">Choose pieces per box below. We calculate the full production request automatically. The branch confirms physical fit and packaging cost in your quote.</span></label>}
                        </fieldset>
                        <fieldset><legend className="font-serif text-2xl">03 · Build your sweet selection</legend>
                            <p className="mt-2 text-sm">Discover celebration specials alongside your favourites. Piece requests for weight-based sweets are sized by the branch before quoting.</p>
                            <div className="mt-4 flex flex-wrap gap-3"><label className="flex-1 text-sm">Find your favourites<input type="search" value={catalogueSearch} onChange={event => setCatalogueSearch(event.target.value)} placeholder="Search sweets" className="mt-1 w-full rounded-xl border p-3" /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={specialOnly} onChange={event => setSpecialOnly(event.target.checked)} />Occasion specials only</label></div>
                            <div className="mt-4 grid gap-4 sm:grid-cols-2">{products.filter(product => (!specialOnly || product.occasionOnly) && product.name.toLowerCase().includes(catalogueSearch.trim().toLowerCase())).map(product => <div key={product.id} className="overflow-hidden rounded-2xl border">
                                {product.imageUrl ? <img src={product.imageUrl} alt={product.name} className="h-40 w-full object-cover" /> : <div className="flex h-24 items-center justify-center bg-[#fffaf2] font-serif text-xl">Gokul celebration selection</div>}
                                <div className="p-4">{product.occasionOnly && <p className="text-xs font-bold uppercase tracking-wider text-[#c76752]">Made for occasions</p>}<h3 className="mt-1 text-lg font-semibold">{product.name}</h3>{product.description && <p className="mt-1 text-sm">{product.description}</p>}<p className="mt-2 text-xs">{product.leadDays} days advance notice{product.pieceGrams ? ` · configured piece size ${product.pieceGrams} g` : ""}</p>
                                <div className="mt-3 flex gap-2"><label className="flex-1 text-sm">{boxId ? "Pieces in each box" : "Requested quantity"}<input aria-label={`${product.name} quantity`} type="number" min={0} max={boxId ? 1000 : 100000} step={boxId || (units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")) === "PIECE" ? 1 : 0.001} value={items[product.id] ?? 0} onChange={event => setItems(current => ({...current, [product.id]: Number(event.target.value)}))} className="mt-1 w-full rounded-xl border p-3" /></label>{!boxId && <label className="text-sm">Unit<select aria-label={`${product.name} unit`} value={units[product.id] ?? (product.saleMode === "WEIGHT" ? "KG" : "PIECE")} onChange={event => {setUnits(current => ({...current, [product.id]: event.target.value as "KG" | "PIECE"})); setItems(current => ({...current, [product.id]: 0}));}} className="mt-1 rounded-xl border p-3">{product.saleMode === "WEIGHT" && <option value="KG">kg</option>}<option value="PIECE">pieces</option></select></label>}</div>
                                {boxId && Number(items[product.id]) > 0 && <p className="mt-2 text-sm font-semibold">{items[product.id]} × {boxCount} boxes = {(items[product.id] * boxCount).toLocaleString("en-IN")} pieces total</p>}</div></div>)}</div>
                        </fieldset>
                        <label className="block">Anything else? <textarea maxLength={1000} value={notes} onChange={event => setNotes(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>
                        <div className="flex flex-wrap gap-3">
                            <button disabled={busy || !products.length || !session.authenticated} className="min-h-12 rounded-full bg-[#c76752] px-6 font-bold text-white disabled:opacity-50">{busy ? "Sending…" : "Request a reviewed quote"}</button>
                        </div>
                        <p className="text-sm text-[#4e605c]">Verify your phone above to send your request. This enquiry creates no booking or stock reservation. The manager reviews a dedicated future production plan, packaging, taxes, deposit and balance before payment.</p>
                    </form>}
                </>}
                {message && <p role="status" className="mt-5 rounded-xl bg-[#fff0dc] p-4">{message}</p>}
                {session.authenticated && historyPhone === (session.phone ?? "") && <section className="mt-10"><h2 className="font-serif text-3xl">Your requests</h2>
                    {history.map(enquiry => <article id={`occasion-${enquiry.id}`} key={enquiry.id} className="scroll-mt-28 mt-4 rounded-2xl border bg-white p-5">
                        <div className="flex flex-wrap justify-between gap-2"><strong>{enquiry.occasionType} · {enquiry.serviceDate}</strong><span>{{REQUESTED: "Under branch review", QUOTED: "Quote ready — deposit due", PAYMENT_PENDING: "Deposit payment in progress", HELD: "Deposit payment in progress", PAID: "Deposit received — balance due", CONFIRMED: "Pickup confirmed", EXPIRED: "Quote or payment window expired", DECLINED: "Request declined", CANCELLED: "Cancelled — finance review pending"}[enquiry.status] ?? "Contact the branch"}</span></div>
                        <p className="mt-2">{enquiry.nextStep}</p>
                        <ul className="mt-3 text-sm">{enquiry.items.map(item => <li key={item.productId}>Requested: {products.find(product => product.id === item.productId)?.name ?? "Sweet"} · {item.unit === "GRAM" ? `${item.quantity / 1000} kg` : `${item.quantity.toLocaleString("en-IN")} pieces`}</li>)}</ul>
                        {enquiry.gift && <div className="mt-3 rounded-xl bg-[#fffaf2] p-3 text-sm"><strong>{enquiry.gift.boxCount} × {enquiry.gift.box.name}</strong><p>{enquiry.gift.box.dimensions} · {enquiry.gift.box.material} · {enquiry.gift.box.branding}</p><p>{enquiry.gift.approvedPackagingTotal == null ? "Packaging is awaiting manager price and fit review." : `Packaging ₹${enquiry.gift.approvedPackagingTotal}, included in the approved item totals.`}</p></div>}
                        {enquiry.quotedAmount != null && <p className="mt-2">Quoted: ₹{enquiry.quotedAmount} · Requested deposit: ₹{enquiry.depositAmount} · Paid: ₹{enquiry.paidAmount}</p>}
                        {enquiry.pricedLines?.length > 0 && <div className="mt-3 rounded-xl border border-[#d9e5df] p-3 text-sm">
                            <p className="font-semibold">Approved quote details</p>
                            {enquiry.pricedLines.map(line => <p key={line.productId} className="mt-1">{line.productName}: ₹{line.grossAmount} (base ₹{line.subtotal}, tax ₹{line.taxAmount}; {line.cgstRate}% CGST + {line.sgstRate}% SGST)</p>)}
                        </div>}
                        {enquiry.orderNumber && <p className="mt-2">Confirmed order <Link href={`/orders/${encodeURIComponent(enquiry.orderNumber)}`} className="underline">{enquiry.orderNumber}</Link></p>}
                        {!!enquiry.productionPlan?.length && <ul className="mt-2 text-sm">{enquiry.productionPlan.map(line => <li key={line.productId}>Approved production: {products.find(product => product.id === line.productId)?.name ?? "Sweet"} · {line.unit === "GRAM" ? `${line.quantity / 1000} kg` : `${line.quantity} pieces`}</li>)}</ul>}
                        {!!enquiry.productionPlan?.length && <p className="mt-2 text-sm">The branch has planned production specifically for your request. A verified deposit reserves it; full verified payment confirms pickup. This does not mean the food is already prepared.</p>}
                        {enquiry.status === "CONFIRMED" && !!enquiry.productionPlan?.length && <p className="mt-2 text-sm">{enquiry.productionPlan.every(line => line.readyQuantity >= line.quantity)
                            ? "The branch has recorded all requested quantities as prepared. Check your linked order for pickup status."
                            : "Your pickup is confirmed. The branch will update preparation and pickup status in your linked order."}</p>}
                        {enquiry.cancellationReview && <div className="mt-3 rounded-xl bg-[#fff0dc] p-3 text-sm">
                            <p>Cancellation reason: {enquiry.cancellationReview.reason}</p>
                            <p>₹{enquiry.cancellationReview.paidAmount} already paid needs branch finance review under your booking terms. No refund is confirmed yet. Please contact the branch.</p>
                        </div>}
                        {enquiry.quoteTerms && <p className="mt-2">{enquiry.quoteTerms}</p>}
                        {enquiry.balanceDueAt && <p className="mt-2">Balance due {new Date(enquiry.balanceDueAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST.</p>}
                        {features.occasionPayments && enquiry.status === "QUOTED" && enquiry.fulfilment === "PICKUP" && <div className="mt-4 space-y-3">
                            <button type="button" disabled={busy} onClick={() => void loadSlots(enquiry)} className="min-h-11 rounded-full border border-[#173a37] px-5">Choose a live pickup time</button>
                            {slots[enquiry.id] && <label className="block">Pickup time (IST)
                                <select className="mt-2 block w-full max-w-sm rounded-xl border p-3" value={selectedSlots[enquiry.id] ?? ""}
                                    onChange={event => setSelectedSlots(current => ({...current, [enquiry.id]: Number(event.target.value)}))}>
                                    <option value="">Choose time</option>{slots[enquiry.id].map(slot => <option key={slot.id} value={slot.id}>{slot.startTime}–{slot.endTime}</option>)}
                                </select></label>}
                            {slots[enquiry.id] && slots[enquiry.id].length === 0 && <p>No pickup capacity remains on this date. Contact the branch for a new quote.</p>}
                            <button type="button" disabled={busy || !selectedSlots[enquiry.id]} onClick={() => void pay(enquiry, "deposit")}
                                className="min-h-11 rounded-full bg-[#c76752] px-5 font-bold text-white disabled:opacity-50">Pay deposit ₹{enquiry.depositAmount}</button>
                        </div>}
                        {features.occasionPayments && enquiry.status === "PAID" && enquiry.quotedAmount != null
                            && enquiry.quotedAmount > enquiry.paidAmount && enquiry.balancePaymentOpen && <button type="button" disabled={busy}
                            onClick={() => void pay(enquiry, "balance")}
                            className="mt-4 min-h-11 rounded-full bg-[#c76752] px-5 font-bold text-white disabled:opacity-50">Pay balance ₹{(enquiry.quotedAmount - enquiry.paidAmount).toFixed(2)}</button>}
                        {features.occasionPayments && ["PAYMENT_PENDING", "HELD", "PAID", "CONFIRMED", "EXPIRED", "CANCELLED"].includes(enquiry.status)
                            && <button type="button" disabled={busy} onClick={() => void checkPayment(enquiry)}
                            className="mt-4 ml-2 min-h-11 rounded-full border border-[#173a37] px-5 disabled:opacity-50">Check latest payment</button>}
                        {attempts[enquiry.id] && <p role="status" className="mt-3 rounded-xl bg-[#fff0dc] p-3">{attempts[enquiry.id].status === "REFUND_PENDING"
                            ? "A late payment needs branch refund review. Please do not pay again; contact the branch."
                            : `Payment ${attempts[enquiry.id].status.toLowerCase()}.`} {attempts[enquiry.id].status === "PENDING" && attempts[enquiry.id].paymentUrl
                            && <a className="underline" href={attempts[enquiry.id].paymentUrl ?? undefined}>Resume secure checkout</a>}</p>}
                    </article>)}
                </section>}
            </>}
        </div>
    </AppShell>;
}
