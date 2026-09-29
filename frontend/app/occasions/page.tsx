"use client";

import {useEffect, useState} from "react";
import Link from "next/link";
import {useRouter} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import CustomerIdentityPanel, {type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getMenu} from "@/services/menuApi";
import {apiClient, ApiError} from "@/services/apiClient";
import type {MenuProduct} from "@/types/menu";
import {useCart} from "@/hooks/useCart";
import {saveCart} from "@/lib/cartStorage";

type Item = {productId: number; quantity: number; unit: "GRAM" | "PIECE"};
type Enquiry = {id: string; branchId: number; occasionType: string; serviceDate: string; guestCount: number;
    status: string; quotedAmount: number | null; depositAmount: number | null; paidAmount: number;
    quoteTerms: string | null; quoteExpiresAt: string | null; nextStep: string; items: Item[]};

function nextBusinessDate(today: string): string {
    if (!/^\d{4}-\d{2}-\d{2}$/.test(today)) return today;
    const date = new Date(`${today}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + 1);
    return date.toISOString().slice(0, 10);
}

function lastOnlineDate(today: string, days: number): string {
    const date = new Date(`${today}T00:00:00Z`);
    date.setUTCDate(date.getUTCDate() + days);
    return date.toISOString().slice(0, 10);
}

export default function OccasionsPage() {
    const router = useRouter();
    const cart = useCart();
    const features = useStorefrontFeatures();
    const {branch} = useSelectedBranch();
    const [session, setSession] = useState<CustomerSession>({authenticated: false});
    const [products, setProducts] = useState<MenuProduct[]>([]);
    const [items, setItems] = useState<Record<number, number>>({});
    const [history, setHistory] = useState<Enquiry[]>([]);
    const [historyPhone, setHistoryPhone] = useState("");
    const [type, setType] = useState("Family celebration");
    const [date, setDate] = useState("");
    const [guests, setGuests] = useState(20);
    const [mode, setMode] = useState<"PICKUP" | "DELIVERY_REQUEST">("PICKUP");
    const [address, setAddress] = useState("");
    const [notes, setNotes] = useState("");
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");

    useEffect(() => {
        if (!branch || !features?.occasionEnquiries) return;
        const controller = new AbortController();
        getMenu(branch.id, controller.signal).then(categories => {
            if (!controller.signal.aborted) setProducts(categories.flatMap(category => category.products).filter(product => product.available));
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

    async function submit(event: React.FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!branch || busy) return;
        if (!date || date <= (features?.today ?? "")) {setMessage("Choose a future service date in India."); return;}
        const chosen = products.filter(product => Number(items[product.id]) > 0).map(product => ({
            productId: product.id, quantity: Number(items[product.id]), unit: product.saleMode === "WEIGHT" ? "GRAM" : "PIECE"
        }));
        if (!chosen.length) {setMessage("Add at least one product and quantity."); return;}
        setBusy(true); setMessage("");
        try {
            const result = await apiClient<Enquiry>("/api/occasion-enquiries", {method: "POST", credentials: "include",
                body: JSON.stringify({branchId: branch.id, occasionType: type, serviceDate: date, guestCount: guests,
                    fulfilment: mode, deliveryAddress: mode === "DELIVERY_REQUEST" ? address : null, notes, items: chosen})});
            setHistory(current => [result, ...current]);
            setHistoryPhone(session.phone ?? "");
            setMessage("Request sent. The branch will review it before sharing a quote. No booking or payment has been made.");
            setItems({});
        } catch (error) {
            setMessage(error instanceof ApiError && error.status === 401 ? "Please verify your phone, then try again."
                : error instanceof ApiError && error.status === 429 ? "You have sent three enquiries in the last 24 hours. Please contact the branch for changes."
                : error instanceof ApiError && error.status === 400 ? error.message
                : "The request could not be sent. Your entries are still here; please retry.");
        } finally {setBusy(false);}
    }

    function startOnlinePickup() {
        if (!branch || busy) return;
        if (!date || date <= (features?.today ?? "")) {setMessage("Choose a future service date in India first."); return;}
        if (features && date > lastOnlineDate(features.today, features.futureOrderingDays)) {
            setMessage("Online pickup is not open this far ahead. Send a reviewed enquiry to discuss the date with the branch."); return;
        }
        if (mode !== "PICKUP") {setMessage("Online checkout currently supports branch pickup. Ask the team about delivery instead."); return;}
        const chosen = products.filter(product => Number(items[product.id]) > 0);
        if (!chosen.length || chosen.length > 30) {setMessage("Choose 1 to 30 menu products first."); return;}
        if (chosen.some(product => {
            const quantity = Number(items[product.id]);
            return !Number.isSafeInteger(quantity) || quantity <= 0 || quantity > 100000
                || product.saleMode === "WEIGHT" && (quantity < (product.minimumWeightGrams ?? 250)
                    || quantity % (product.weightStepGrams ?? 50) !== 0);
        })) {setMessage("Check the quantities: pieces must be whole numbers, and weights must follow the product's minimum and step."); return;}
        if (cart.items.length && !window.confirm("Replace your current cart with these occasion items? Your existing cart will be removed.")) return;
        try {
            saveCart({branchId: branch.id, items: chosen.map(product => ({
                product, quantity: product.saleMode === "WEIGHT" ? 1 : Number(items[product.id]),
                weightGrams: product.saleMode === "WEIGHT" ? Number(items[product.id]) : null
            }))});
            router.push(`/checkout/pickup?occasionDate=${encodeURIComponent(date)}`);
        } catch {setMessage("Your cart could not be saved on this device. Check available storage and retry.");}
    }

    return <AppShell editorial showSocialPopup={false}>
        <div className="mx-auto max-w-5xl px-4 py-8 text-[#173a37] sm:px-6">
            <p className="text-sm font-bold uppercase tracking-widest text-[#b55f4a]">Occasions at Gokul</p>
            <h1 className="mt-3 font-serif text-4xl sm:text-6xl">Tell us what you&apos;re planning.</h1>
            <p className="mt-4 max-w-2xl">Share the date, guests and food you have in mind. Our team reviews availability and gives you a clear quote before any payment.</p>
            {!features && <p role="status" className="mt-8">Checking availability…</p>}
            {features && !features.occasionEnquiries && <p className="mt-8 rounded-xl bg-white p-6">Occasion enquiries are not available yet. <Link href="/menu" className="underline">Explore pickup ordering</Link>.</p>}
            {features?.occasionEnquiries && <>
                {!branch ? <p className="mt-8 rounded-xl bg-white p-6">Choose a branch first. <Link href="/branches" className="underline">Explore branches</Link>.</p> : <>
                    <p className="mt-5 font-semibold">Planning with {branch.name} · <Link href="/branches" className="underline">Change branch</Link></p>
                    <div className="mt-7 rounded-2xl border border-[#d9e5df] bg-white p-5"><CustomerIdentityPanel onSessionChange={setSession} /></div>
                    {session.authenticated && <form onSubmit={submit} className="mt-7 space-y-5 rounded-2xl border border-[#d9e5df] bg-white p-5 sm:p-8">
                        <p className="font-semibold">This is an enquiry. A quote is not a booking or stock reservation.</p>
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
                        <fieldset><legend className="font-semibold">Food and approximate quantities</legend>
                            <div className="mt-3 grid gap-3 sm:grid-cols-2">{products.map(product => <label key={product.id} className="flex items-center justify-between gap-3 rounded-xl border p-3">
                                <span>{product.name}<small className="block text-[#4e605c]">{product.saleMode === "WEIGHT" ? "grams" : "pieces"}</small></span>
                                <input aria-label={`${product.name} quantity`} type="number" min={0} step={product.saleMode === "WEIGHT" ? 50 : 1}
                                    value={items[product.id] ?? 0} onChange={event => setItems(current => ({...current, [product.id]: Number(event.target.value)}))}
                                    className="w-24 rounded-lg border p-2" /></label>)}</div>
                        </fieldset>
                        <label className="block">Anything else? <textarea maxLength={1000} value={notes} onChange={event => setNotes(event.target.value)} className="mt-2 block w-full rounded-xl border p-3" /></label>
                        <div className="flex flex-wrap gap-3">
                            <button disabled={busy || !products.length} className="min-h-12 rounded-full bg-[#c76752] px-6 font-bold text-white disabled:opacity-50">{busy ? "Sending…" : "Request a reviewed quote"}</button>
                            {mode === "PICKUP" && <button type="button" disabled={busy || !products.length} onClick={startOnlinePickup}
                                className="min-h-12 rounded-full border border-[#173a37] px-6 font-bold disabled:opacity-50">Continue to online pickup</button>}
                        </div>
                        <p className="text-sm text-[#4e605c]">Online pickup uses live menu prices and full payment. We carry your requested date to checkout, where you must choose an available time; capacity and stock are checked there. A manager quote follows a separate review and cannot be paid here.</p>
                    </form>}
                </>}
                {message && <p role="status" className="mt-5 rounded-xl bg-[#fff0dc] p-4">{message}</p>}
                {session.authenticated && historyPhone === (session.phone ?? "") && <section className="mt-10"><h2 className="font-serif text-3xl">Your requests</h2>
                    {history.map(enquiry => <article key={enquiry.id} className="mt-4 rounded-2xl border bg-white p-5">
                        <div className="flex flex-wrap justify-between gap-2"><strong>{enquiry.occasionType} · {enquiry.serviceDate}</strong><span>{enquiry.status}</span></div>
                        <p className="mt-2">{enquiry.nextStep}</p>
                        {enquiry.quotedAmount != null && <p className="mt-2">Quoted: ₹{enquiry.quotedAmount} · Requested deposit: ₹{enquiry.depositAmount} · Paid: ₹{enquiry.paidAmount}</p>}
                        {enquiry.quoteTerms && <p className="mt-2">{enquiry.quoteTerms}</p>}
                    </article>)}
                </section>}
            </>}
        </div>
    </AppShell>;
}
