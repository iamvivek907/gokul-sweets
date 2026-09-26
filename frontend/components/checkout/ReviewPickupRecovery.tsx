"use client";

import {useEffect, useState} from "react";
import {availabilityItems, checkCartAvailability, type CartAvailability} from "@/services/availabilityApi";
import {savePickupSlot} from "@/lib/checkoutStorage";
import type {CartItem} from "@/types/cart";

type Choice = {date: string; slot: CartAvailability["dates"][number]["slots"][number]};

export default function ReviewPickupRecovery({branchId, items, today, days, rejectedSlotId, rejectedDate, rejectedTime, onSelected, mode = "later"}: {
    branchId: number; items: CartItem[]; today: string; days: number; rejectedSlotId: number;
    rejectedDate: string; rejectedTime: string;
    mode?: "later" | "all";
    onSelected: () => void;
}) {
    const [result, setResult] = useState<CartAvailability | null>(null);
    const [error, setError] = useState(false);
    const [revision, setRevision] = useState(0);
    const cartKey = JSON.stringify(availabilityItems(items));

    useEffect(() => {
        const controller = new AbortController();
        checkCartAvailability(branchId, today, days + 1, JSON.parse(cartKey), controller.signal)
            .then(value => {if (!controller.signal.aborted) {setResult(value); setError(false);}})
            .catch(() => {if (!controller.signal.aborted) setError(true);});
        return () => controller.abort();
    }, [branchId, today, days, cartKey, revision]);

    const choices: Choice[] = result?.dates.flatMap(day => day.slots
        .filter(slot => (mode === "all" || day.date > rejectedDate || day.date === rejectedDate && slot.slot.startTime > rejectedTime)
            && !(day.date === rejectedDate && slot.slot.id === rejectedSlotId)
            && (slot.normalAvailable || slot.priorityAvailable))
        .map(slot => ({date: day.date, slot}))) ?? [];

    function choose(choice: Choice) {
        savePickupSlot({date: choice.date, slot: choice.slot.slot,
            pickupType: choice.slot.normalAvailable ? "NORMAL" : "PRIORITY"});
        onSelected();
    }

    return <section aria-label="Choose another pickup time" className="mt-4 rounded-2xl border border-[#e8d2af] bg-[#fff8eb] p-4">
        <h3 className="font-bold text-[#241715]">{mode === "all" ? "Change your pickup time" : "Choose another pickup time"}</h3>
        <p className="mt-1 text-sm text-[#756763]">Your cart and details are saved. Pick a time that works for everything in your order.</p>
        {!result && !error && <p role="status" className="mt-3 text-sm">Finding available times…</p>}
        {error && <button type="button" className="mt-3 min-h-11 font-semibold text-[#7a1625] underline"
            onClick={() => {setError(false); setRevision(value => value + 1);}}>Try finding times again</button>}
        {result && !choices.length && <p className="mt-3 text-sm">No other times are available for this cart right now. Try another date or contact the branch.</p>}
        {choices.length > 0 && <div className="mt-3 flex flex-wrap gap-2">{choices.slice(0, 6).map(choice => {
            const label = new Intl.DateTimeFormat("en-IN", {weekday: "short", day: "numeric", month: "short", timeZone: "Asia/Kolkata"})
                .format(new Date(`${choice.date}T00:00:00+05:30`));
            return <button type="button" key={choice.slot.slot.id} onClick={() => choose(choice)}
                className="min-h-12 rounded-xl border border-[#dfcaae] bg-white px-3 py-2 text-sm font-semibold text-[#7a1625]">
                {label} · {choice.slot.slot.startTime.slice(0, 5)}{!choice.slot.normalAvailable ? " · Priority" : ""}
            </button>;
        })}</div>}
        <a href="/checkout/pickup" className="mt-3 inline-flex min-h-11 items-center text-sm font-semibold text-[#7a1625] underline">See all pickup times</a>
    </section>;
}
