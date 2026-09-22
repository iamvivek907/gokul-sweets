"use client";

import Link from "next/link";
import {useEffect, useRef, useState} from "react";
import {useRouter} from "next/navigation";
import AppShell from "@/components/layout/AppShell";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import type {StorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {availabilityItems, checkCartAvailability, type CartAvailability} from "@/services/availabilityApi";
import {savePickupSlot} from "@/lib/checkoutStorage";
import type {PickupType} from "@/types/pickup";

function dateLabel(date: string) {
    return new Intl.DateTimeFormat("en-IN", {weekday: "short", day: "numeric", month: "short", timeZone: "Asia/Kolkata"})
        .format(new Date(`${date}T00:00:00+05:30`));
}

export default function SmartPickupSelection({features, onFallback}: {
    features: StorefrontFeatures; onFallback: () => void;
}) {
    const router = useRouter();
    const cart = useCart();
    const {branch} = useSelectedBranch();
    const [selectedDate, setSelectedDate] = useState<string | null>(null);
    const [selection, setSelection] = useState<{id: number; type: PickupType} | null>(null);
    const [revision, setRevision] = useState(0);
    const [result, setResult] = useState<{key: string; data: CartAvailability} | null>(null);
    const [failure, setFailure] = useState<{key: string; message: string} | null>(null);
    const [continuing, setContinuing] = useState(false);
    const [message, setMessage] = useState("");
    const stateVersion = useRef(0);
    const date = selectedDate ?? features.today;
    const itemsJson = JSON.stringify(availabilityItems(cart.items));
    const key = JSON.stringify([branch?.id, itemsJson, features.today, features.futureOrderingDays, date, selection, revision]);
    const data = result?.key === key ? result.data : null;
    const error = failure?.key === key ? failure.message : null;
    const validBranch = branch && branch.id === cart.branchId;
    const currentDate = data?.dates.find(value => value.date === date);
    const chosen = currentDate?.slots.find(value => value.slot.id === selection?.id);
    const available = selection?.type === "PRIORITY" ? chosen?.priorityAvailable : chosen?.normalAvailable;

    useEffect(() => {
        stateVersion.current += 1;
        if (!branch || branch.id !== cart.branchId || !cart.items.length) return;
        const controller = new AbortController();
        const items: ReturnType<typeof availabilityItems> = JSON.parse(itemsJson);
        checkCartAvailability(branch.id, features.today, features.futureOrderingDays + 1, items,
            AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]))
            .then(data => {if (!controller.signal.aborted) setResult({key, data});})
            .catch(error => {if (!controller.signal.aborted) setFailure({key, message: error.message});});
        return () => controller.abort();
    }, [branch, cart.branchId, cart.items.length, itemsJson, features.today, features.futureOrderingDays, key]);

    useEffect(() => {
        const timer = window.setInterval(() => setRevision(value => value + 1), 30_000);
        return () => window.clearInterval(timer);
    }, []);

    async function continueCheckout() {
        if (!branch || !selection || !available) return;
        const version = stateVersion.current;
        setContinuing(true); setMessage("");
        try {
            // Refresh before navigation, without deleting any cart or customer fields if the choice has changed.
            const fresh = await checkCartAvailability(branch.id, date, 1, availabilityItems(cart.items), AbortSignal.timeout(10_000));
            if (stateVersion.current !== version) {
                setMessage("Your cart or pickup choice changed. Review the updated times before continuing.");
                return;
            }
            const slot = fresh.dates[0]?.slots.find(value => value.slot.id === selection.id);
            if (!slot || !(selection.type === "NORMAL" ? slot.normalAvailable : slot.priorityAvailable)) {
                setMessage("That time has just changed. Your cart is safe; choose another available time below.");
                setRevision(value => value + 1);
                return;
            }
            savePickupSlot({date, slot: slot.slot, pickupType: selection.type});
            router.push("/checkout/customer");
        } catch (error) {
            setMessage(error instanceof Error ? error.message : "Unable to refresh availability.");
        } finally {setContinuing(false);}
    }

    return <AppShell showSocialPopup={false}>
        <nav aria-label="Order progress" className="mb-5 text-sm text-[#756763]">
            <Link href="/menu">Branch & menu</Link> / <Link href="/cart">Your cart</Link> / <strong>Pickup time</strong> / Review
        </nav>
        <h1 className="text-3xl font-bold text-[#7a1625]">When would you like to collect?</h1>
        <p className="mt-2 text-sm text-[#756763]">Times are checked for your whole cart at {branch?.name ?? "your branch"}. Your order is reserved at checkout.</p>
        {!cart.items.length ? <Link className="mt-5 block underline" href="/menu">Add items from the menu first</Link>
            : !validBranch ? <p role="alert" className="mt-5">Your cart belongs to a different branch. <Link href="/cart" className="underline">Review your cart</Link> before continuing.</p>
            : <>
                {error && <div role="alert" className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4">
                    <p>Live suggestions could not be loaded. {error}</p>
                    <button className="min-h-11 px-4 underline" onClick={() => setRevision(value => value + 1)}>Retry</button>
                    <button className="min-h-11 px-4 underline" onClick={onFallback}>Use standard pickup selection</button>
                    <p className="text-xs">Your cart is preserved. Checkout will still validate availability.</p>
                </div>}
                {!data && !error && <p role="status" className="mt-5">Checking dates and times for your cart...</p>}
                {data && <div className="mt-6 space-y-6">
                    <section aria-label="Pickup dates">
                        <h2 className="mb-3 font-bold">Choose a date</h2>
                        <div className="flex gap-2 overflow-x-auto pb-3">{data.dates.map(day => <button key={day.date}
                            type="button" disabled={continuing} aria-pressed={date === day.date}
                            onClick={() => {setSelectedDate(day.date); setSelection(null); setMessage("");}}
                            className={`min-h-16 min-w-28 rounded-xl border px-3 py-2 text-sm ${date === day.date ? "border-[#7a1625] bg-[#fff1e9]" : "border-[#eadfd6] bg-white"}`}>
                            <span className="block font-bold">{dateLabel(day.date)}</span>
                            <span className={day.available ? "text-green-800" : "text-[#756763]"}>{day.available ? "Available" : "Unavailable"}</span>
                        </button>)}</div>
                    </section>
                    <section aria-label="Pickup times">
                        <h2 className="mb-3 font-bold">{dateLabel(date)} - pickup times</h2>
                        {!currentDate?.available && <p role="status" className="mb-3 text-[#7a1625]">This date cannot currently fulfil your cart. Choose an available date above; your items and details are saved.</p>}
                        <div className="grid gap-3 sm:grid-cols-2">{currentDate?.slots.map(value => <div key={value.slot.id} className="rounded-2xl border border-[#eadfd6] bg-white p-4">
                            <p className="font-bold">{value.slot.startTime.slice(0, 5)} - {value.slot.endTime.slice(0, 5)}</p>
                            {value.reason && <p className="mt-1 text-sm text-[#756763]">{value.reason}</p>}
                            <div className="mt-2 flex flex-wrap gap-2">
                                {(["NORMAL", "PRIORITY"] as const).map(type => {
                                    const enabled = type === "NORMAL" ? value.normalAvailable : value.priorityAvailable;
                                    if (type === "PRIORITY" && !value.slot.priorityEnabled) return null;
                                    const selected = selection?.id === value.slot.id && selection.type === type;
                                    return <button key={type} disabled={!enabled || continuing} aria-pressed={selected}
                                        onClick={() => {setSelection({id: value.slot.id, type}); setMessage("");}}
                                        className={`min-h-11 rounded-xl border px-4 text-sm font-bold disabled:opacity-50 ${selected ? "bg-[#7a1625] text-white" : ""}`}>
                                        {type === "NORMAL" ? "Normal pickup" : `Priority + INR ${value.slot.priorityCharge}`}
                                        {!enabled ? " - unavailable" : selected ? " - selected" : ""}
                                    </button>;
                                })}
                            </div>
                        </div>)}</div>
                    </section>
                </div>}
                {message && <p role="alert" className="mt-4 text-red-700">{message}</p>}
                <div className="sticky bottom-20 mt-6 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#eadfd6] bg-white p-4 shadow-lg">
                    <Link href="/cart" className="min-h-11 py-3 text-sm underline">{cart.itemCount} items · INR {cart.subtotal.toFixed(2)} before tax</Link>
                    <button disabled={!available || continuing || !data} onClick={continueCheckout}
                        className="min-h-12 rounded-xl bg-[#7a1625] px-6 font-bold text-white disabled:opacity-50">
                        {continuing ? "Checking..." : "Continue"}
                    </button>
                </div>
            </>}
    </AppShell>;
}
