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
import {usePickupIntent, savePickupIntent} from "@/hooks/usePickupIntent";

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
    const intent = usePickupIntent(branch?.id);
    const [selectedDate, setSelectedDate] = useState<string | null>(null);
    const [selection, setSelection] = useState<{id: number; type: PickupType} | null>(null);
    const [revision, setRevision] = useState(0);
    const [result, setResult] = useState<{key: string; data: CartAvailability} | null>(null);
    const [failure, setFailure] = useState<{key: string; message: string} | null>(null);
    const [continuing, setContinuing] = useState(false);
    const [message, setMessage] = useState("");
    const stateVersion = useRef(0);
    const date = selectedDate ?? (intent.date && intent.date >= features.today ? intent.date : features.today);
    const itemsJson = JSON.stringify(availabilityItems(cart.items));
    const key = JSON.stringify([branch?.id, itemsJson, features.today, features.futureOrderingDays, revision]);
    const data = result?.key === key ? result.data : null;
    const error = failure?.key === key ? failure.message : null;
    const validBranch = branch && branch.id === cart.branchId;
    const currentDate = data?.dates.find(value => value.date === date);
    const chosen = currentDate?.slots.find(value => value.slot.id === selection?.id);
    const available = selection?.type === "PRIORITY" ? chosen?.priorityAvailable : chosen?.normalAvailable;
    const selectable = currentDate?.slots.filter(value => value.normalAvailable || value.priorityAvailable) ?? [];
    const unavailable = currentDate?.slots.filter(value => !value.normalAvailable && !value.priorityAvailable) ?? [];
    const next = data?.dates.flatMap(day => day.slots.filter(slot => slot.normalAvailable || slot.priorityAvailable)
        .map(slot => ({day, slot})))[0];

    useEffect(() => {stateVersion.current += 1;}, [date, selection]);

    useEffect(() => {
        stateVersion.current += 1;
        if (!branch || branch.id !== cart.branchId || !cart.items.length) return;
        const controller = new AbortController();
        const items: ReturnType<typeof availabilityItems> = JSON.parse(itemsJson);
        const timer = window.setTimeout(() => {void checkCartAvailability(branch.id, features.today, features.futureOrderingDays + 1, items,
            AbortSignal.any([controller.signal, AbortSignal.timeout(10_000)]))
            .then(data => {if (!controller.signal.aborted) setResult({key, data});})
            .catch(error => {if (!controller.signal.aborted) {
                console.warn("Cart pickup check failed.", error);
                setFailure({key, message: "We couldn't check pickup times. Your cart is saved. Try again."});
            }});}, 250);
        return () => {controller.abort(); window.clearTimeout(timer);};
    }, [branch, cart.branchId, cart.items.length, itemsJson, features.today, features.futureOrderingDays, key]);

    useEffect(() => {
        const timer = window.setInterval(() => setRevision(value => value + 1), 60_000);
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
            console.warn("Final pickup preview failed.", error);
            setMessage("We couldn't check this pickup time. Your cart is saved. Try again.");
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
                    <p>{error}</p>
                    <button className="min-h-11 px-4 underline" onClick={() => setRevision(value => value + 1)}>Retry</button>
                    <button className="min-h-11 px-4 underline" onClick={onFallback}>Use standard pickup selection</button>
                    <p className="text-xs">Your cart is preserved. Checkout will still validate availability.</p>
                </div>}
                {!data && !error && <p role="status" className="mt-5">Checking dates and times for your cart...</p>}
                {data && <div className="mt-6 space-y-6">
                    {next && !available && <button className="min-h-12 rounded-xl bg-[#fff0dc] px-4 py-3 text-left font-semibold"
                        onClick={() => {setSelectedDate(next.day.date); savePickupIntent(branch!.id, next.day.date);
                            setSelection({id: next.slot.slot.id, type: next.slot.normalAvailable ? "NORMAL" : "PRIORITY"});}}>
                        Next pickup for all items: {dateLabel(next.day.date)}, {next.slot.slot.startTime.slice(0, 5)}
                        {!next.slot.normalAvailable ? ` (priority + INR ${next.slot.slot.priorityCharge})` : ""}
                    </button>}
                    <section aria-label="Pickup dates">
                        <h2 className="mb-3 font-bold">Choose a date</h2>
                        <div className="flex gap-2 overflow-x-auto pb-3">{data.dates.map(day => <button key={day.date}
                            type="button" disabled={continuing} aria-pressed={date === day.date}
                            onClick={() => {setSelectedDate(day.date); savePickupIntent(branch!.id, day.date); setSelection(null); setMessage("");}}
                            className={`min-h-16 min-w-28 rounded-xl border px-3 py-2 text-sm ${date === day.date ? "border-[#7a1625] bg-[#fff1e9]" : "border-[#eadfd6] bg-white"}`}>
                            <span className="block font-bold">{dateLabel(day.date)}</span>
                            <span className={day.available ? "text-green-800" : "text-[#756763]"}>{day.available ? "Times available" : "No matching time"}</span>
                        </button>)}</div>
                    </section>
                    <section aria-label="Pickup times">
                        <h2 className="mb-3 font-bold">{dateLabel(date)} - pickup times</h2>
                        {!currentDate?.available && <div role="status" className="mb-3 rounded-xl bg-[#fff0dc] p-4">
                            <p>{currentDate?.reason ?? "No time can currently fulfil all items on this date."}</p>
                            {currentDate?.items?.filter(item => !item.available).map(item => <p key={item.productId} className="mt-2 text-sm">
                                <strong>{item.productName}</strong>: {item.reason}
                                {item.code === "QUANTITY_TOO_LARGE" && ` Up to ${item.availableQuantity} ${item.unit === "GRAM" ? "g" : "pieces"} remain.`}
                            </p>)}
                            <Link href="/cart" className="inline-flex min-h-11 items-center underline">Edit quantities or remove items in your cart</Link>
                        </div>}
                        <label htmlFor="smart-pickup-time" className="mb-2 block text-sm font-semibold">Pickup time</label>
                        <select id="smart-pickup-time" aria-describedby="pickup-time-help"
                            disabled={continuing || !selectable.length}
                            value={available && selection ? `${selection.id}:${selection.type}` : ""}
                            onChange={event => {
                                const [id, type] = event.target.value.split(":");
                                setSelection(id && (type === "NORMAL" || type === "PRIORITY") ? {id: Number(id), type} : null);
                                setMessage("");
                            }}
                            className="min-h-12 w-full rounded-xl border border-[#eadfd6] bg-white px-3 text-base sm:max-w-lg">
                            <option value="">{selectable.length ? "Choose an available time" : "No matching times on this date"}</option>
                            {selectable.flatMap(value => (["NORMAL", "PRIORITY"] as const).flatMap(type => {
                                if (!(type === "NORMAL" ? value.normalAvailable : value.priorityAvailable && value.slot.priorityEnabled)) return [];
                                return <option key={`${value.slot.id}:${type}`} value={`${value.slot.id}:${type}`}>
                                    {value.slot.startTime.slice(0, 5)} - {value.slot.endTime.slice(0, 5)}
                                    {type === "NORMAL" ? " · Normal pickup" : ` · Priority + INR ${value.slot.priorityCharge}`}
                                </option>;
                            }))}
                        </select>
                        <p id="pickup-time-help" className="mt-2 text-xs text-[#756763]">Only times that fit every item in your cart are selectable. All times are India time.</p>
                        {unavailable.length > 0 && <details className="mt-4 rounded-xl border border-[#eadfd6] p-3">
                            <summary className="min-h-11 cursor-pointer py-2 text-sm">Why are {unavailable.length} other times not available?</summary>
                            <ul className="space-y-3 text-sm">{unavailable.map(value => <li key={value.slot.id}>
                                <strong>{value.slot.startTime.slice(0, 5)}</strong>: {value.reason}
                                {value.issues && value.issues.length > 1 && <span> Also: {value.issues.slice(1).map(item => `${item.productName}: ${item.reason}`).join(" ")}</span>}
                            </li>)}</ul>
                        </details>}
                    </section>
                </div>}
                {message && <p role="alert" className="mt-4 text-red-700">{message}</p>}
                <div className="mt-6 mb-24 flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#eadfd6] bg-white p-4">
                    <Link href="/cart" className="min-h-11 py-3 text-sm underline">{cart.itemCount} items · INR {cart.subtotal.toFixed(2)} before tax</Link>
                    {chosen && <p className="text-sm font-semibold">{dateLabel(date)} at {chosen.slot.startTime.slice(0, 5)}{selection?.type === "PRIORITY" ? ` + INR ${chosen.slot.priorityCharge} priority` : ""}</p>}
                    <button disabled={!available || continuing || !data} onClick={continueCheckout}
                        className="min-h-12 rounded-xl bg-[#7a1625] px-6 font-bold text-white disabled:opacity-50">
                        {continuing ? "Checking..." : "Continue"}
                    </button>
                </div>
            </>}
    </AppShell>;
}
