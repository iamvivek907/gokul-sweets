"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {savePickupIntent, usePickupIntent} from "@/hooks/usePickupIntent";
import {availabilityItems, checkCartAvailability, type CartAvailability} from "@/services/availabilityApi";
import type {MenuProduct} from "@/types/menu";

export function useDateAvailability(products?: MenuProduct[]) {
    const features = useStorefrontFeatures();
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const intent = usePickupIntent(branch?.id);
    const [revision, setRevision] = useState(0);
    const [result, setResult] = useState<{key: string; data?: CartAvailability; error?: string} | null>(null);
    const amounts = new Map((cart.branchId === branch?.id ? availabilityItems(cart.items) : []).map(item => [item.productId, item]));
    const requested = products ? products.filter(product => product.available).slice(0, 100).map(product => amounts.get(product.id) ?? {
        productId: product.id, quantity: product.saleMode === "WEIGHT" ? null : 1,
        weightGrams: product.saleMode === "WEIGHT" ? product.minimumWeightGrams : null
    }) : [...amounts.values()];
    const itemsJson = JSON.stringify(requested);
    const key = JSON.stringify([branch?.id, intent.date, itemsJson, revision, features?.smartAvailability]);
    const validDate = intent.date && features && intent.date >= features.today;
    useEffect(() => {
        if (!features?.smartAvailability || !branch || !intent.date || !validDate || !JSON.parse(itemsJson).length) return;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            void checkCartAvailability(branch.id, intent.date!, 1, JSON.parse(itemsJson),
                AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]))
                .then(data => {if (!controller.signal.aborted) setResult({key, data});})
                .catch(error => {if (!controller.signal.aborted) {
                    console.warn("Date availability check failed.", error);
                    setResult({key, error: "We couldn't check this pickup. Your cart is saved. Try again."});
                }});
        }, 350);
        return () => {controller.abort(); window.clearTimeout(timer);};
    }, [features?.smartAvailability, branch, intent.date, validDate, itemsJson, key]);
    const data = result?.key === key ? result.data : undefined;
    const day = data?.dates[0];
    const selectedSlot = day?.slots.find(value => value.slot.id === intent.selection?.slot.id);
    // Menu previews include unchosen products; only the actual cart can invalidate its saved pickup.
    const cartIds = new Set(cart.branchId === branch?.id ? cart.items.map(item => item.product.id) : []);
    const selectionUnavailable = !!intent.selection && !!day && cartIds.size > 0 && (!selectedSlot
        || selectedSlot.code === "PICKUP_WINDOW"
        || (intent.selection.pickupType === "PRIORITY"
            ? !selectedSlot.slot.priorityEnabled || selectedSlot.slot.priorityRemainingCapacity <= 0
            : selectedSlot.slot.remainingCapacity <= 0)
        || selectedSlot.issues?.some(issue => cartIds.has(issue.productId)));
    const items = day?.items?.map(item => selectedSlot?.issues?.find(issue => issue.productId === item.productId) ?? item);
    return {features, branch, intent, data, items, selectionUnavailable, error: result?.key === key ? result.error : null,
        hasItems: requested.length > 0, retry: () => setRevision(value => value + 1)};
}

export default function PickupContext({check, cart = false}: {check: ReturnType<typeof useDateAvailability>; cart?: boolean}) {
    const {features, branch, intent, data, items, error, hasItems, retry} = check;
    if (!features?.smartAvailability || !branch) return null;
    const maximum = new Date(`${features.today}T12:00:00+05:30`);
    maximum.setUTCDate(maximum.getUTCDate() + features.futureOrderingDays);
    const max = maximum.toISOString().slice(0, 10);
    return <section aria-label="Pickup context" className="my-4 rounded-2xl border border-[#eadfd6] bg-white p-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
            <div><p className="text-xs font-semibold uppercase text-[#756763]">Pickup at {branch.name}</p>
                <label className="mt-2 block text-sm font-bold">Pickup date
                    <input aria-label="Pickup date" type="date" min={features.today} max={max} value={intent.date ?? ""}
                        onChange={event => savePickupIntent(branch.id, event.target.value)}
                        className="ml-3 min-h-11 rounded-xl border border-[#eadfd6] px-3" />
                </label></div>
            {intent.selection && <Link href="/checkout/pickup" className="min-h-11 py-3 text-sm underline">
                {intent.selection.slot.startTime.slice(0, 5)} pickup · Change time</Link>}
        </div>
        {!intent.date ? <p className="mt-2 text-sm text-[#756763]">Choose pickup to check availability. Browse and build your cart first if you prefer.</p>
            : intent.date < features.today ? <p role="alert" className="mt-2 text-sm">That pickup date has passed. Choose a new date; your cart is saved.</p>
            : error ? <div role="alert"><p className="mt-2 text-sm">{error}</p><button onClick={retry} className="min-h-11 underline">Try again</button></div>
            : !data && hasItems ? <p role="status" className="mt-2 text-sm">Checking your pickup date...</p>
            : <p className="mt-2 text-xs text-[#756763]">Live preview, not a reservation. {intent.selection ? "Quantities and this time are checked again at checkout." : "Choose a time for all items after building your cart."}</p>}
        {check.selectionUnavailable && <p role="status" className="mt-3 text-sm text-[#7a1625]">Your saved pickup time no longer fits this selection. <Link href="/checkout/pickup" className="underline">Choose another time</Link>. Your cart is unchanged.</p>}
        {cart && items?.some(item => !item.available) && <div role="status" className="mt-3 rounded-xl bg-[#fff0dc] p-3">
            <h3 className="font-bold">Review these items for your pickup</h3>
            {items.filter(item => !item.available).map(item => <p key={item.productId} className="mt-2 text-sm">
                <strong>{item.productName}</strong>: {item.reason}
                {item.code === "QUANTITY_TOO_LARGE" && ` Up to ${item.availableQuantity} ${item.unit === "GRAM" ? "g" : "pieces"} remain.`}
            </p>)}
            <p className="mt-2 text-sm">Edit quantities or remove items below, or keep your cart and find another pickup.</p>
            <Link href="/checkout/pickup" className="inline-flex min-h-11 items-center font-semibold underline">Find a time for all items</Link>
        </div>}
    </section>;
}
