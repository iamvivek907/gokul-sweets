"use client";
import LinkFeedback from "@/components/common/LinkFeedback";

import {T,useTranslation} from "@/lib/language";

import Link from "next/link";
import {usePickupClock} from "@/hooks/usePickupClock";
import {indiaToday,validPickupDate} from "@/lib/pickupFreshness";
import {useEffect, useState} from "react";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {savePickupIntent, usePickupIntent} from "@/hooks/usePickupIntent";
import {availabilityItems, checkCartAvailability, checkMenuAvailability, type CartAvailability} from "@/services/availabilityApi";
import CartSwitchDialog from "@/components/cart/CartSwitchDialog";
import {clearPickupSlot} from "@/lib/checkoutStorage";
import type {CartSwitchPreview} from "@/services/cartSwitchPreview";
import type {MenuProduct} from "@/types/menu";

export function useDateAvailability(products?: MenuProduct[]) {
    const features = useStorefrontFeatures();
    const clock=usePickupClock();
    const today=clock ? [features?.today ?? "",indiaToday(new Date(clock))].sort().at(-1)! : features?.today ?? "";
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const intent = usePickupIntent(branch?.id);
    const [revision, setRevision] = useState(0);
    const [result, setResult] = useState<{key: string; scope: string; data?: CartAvailability; error?: string} | null>(null);
    const amounts = new Map((cart.branchId === branch?.id ? availabilityItems(cart.items) : []).map(item => [item.productId, item]));
    // Always include the real cart, even when search/category filters hide its products.
    const candidates = products?.filter(product => product.available).map(product => ({
        productId: product.id, quantity: product.saleMode === "WEIGHT" ? null : 1,
        weightGrams: product.saleMode === "WEIGHT" ? product.minimumWeightGrams : null
    })) ?? [];
    const requested = [...amounts.values(),...candidates.filter(item=>!amounts.has(item.productId))];
    const menuPreview=!!products;
    const itemsJson = JSON.stringify(requested);
    const key = JSON.stringify([branch?.id, intent.date, itemsJson, revision, features?.smartAvailability,menuPreview]);
    // Retain the last preview while quantities refresh, but never across branch,
    // date, slot, or catalogue changes. Checkout still checks the exact cart.
    const scope = JSON.stringify([branch?.id,intent.date,intent.selection?.slot.id,intent.selection?.pickupType,features?.smartAvailability,menuPreview,candidates]);
    const validDate = intent.date && features && validPickupDate(intent.date,today,features.futureOrderingDays);
    useEffect(() => {
        if (!features?.smartAvailability || !branch || !intent.date || !validDate || !JSON.parse(itemsJson).length) return;
        const controller = new AbortController();
        const timer = window.setTimeout(() => {
            void (menuPreview ? checkMenuAvailability : checkCartAvailability)(branch.id, intent.date!, 1, JSON.parse(itemsJson),
                AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]),menuPreview)
                .then(data => {if (!controller.signal.aborted) setResult({key, scope, data});})
                .catch(error => {if (!controller.signal.aborted) {
                    console.warn("Date availability check failed.", error);
                    setResult({key, scope, error: "We couldn't check this pickup. Your cart is saved. Try again."});
                }});
        }, 350);
        return () => {controller.abort(); window.clearTimeout(timer);};
    }, [features?.smartAvailability, branch, intent.date, validDate, itemsJson, key,scope,menuPreview]);
    const data = result?.key === key ? result.data : undefined;
    const preview = data ?? (menuPreview && result?.scope === scope ? result.data : undefined);
    const day = preview?.dates[0];
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
    return {features, today, branch, intent, data, items, selectionUnavailable, error: result?.key === key ? result.error : null,
        hasItems: requested.length > 0, retry: () => setRevision(value => value + 1)};
}

export default function PickupContext({check, cart = false}: {check: ReturnType<typeof useDateAvailability>; cart?: boolean}) {
    const translate = useTranslation();
    const {features, today, branch, intent, data, items, error, hasItems, retry} = check;
    const currentCart = useCart();
    const [proposedDate, setProposedDate] = useState<string | null>(null);
    const [dateError,setDateError]=useState("");
    if (!features?.smartAvailability || !branch) return null;
    const maximum = new Date(`${today}T12:00:00+05:30`);
    maximum.setUTCDate(maximum.getUTCDate() + features.futureOrderingDays);
    const max = maximum.toISOString().slice(0, 10);
    return <section aria-label="Pickup context" className="my-4 rounded-2xl border border-[#eadfd6] bg-white p-4">
        {proposedDate && <CartSwitchDialog branchId={branch.id} branchName={branch.name} date={proposedDate}
            items={currentCart.items} onKeep={() => setProposedDate(null)} onSwitch={(preview: CartSwitchPreview) => {
                if (preview.conflicts) return;
                if (!validPickupDate(proposedDate, indiaToday(new Date()), features.futureOrderingDays)) {setProposedDate(null);setDateError("Choose a current pickup date.");return;}
                clearPickupSlot();
                savePickupIntent(branch.id, proposedDate);
                setProposedDate(null);
            }} />}
        <div className="flex flex-wrap items-end justify-between gap-3">
            <div><p className="text-xs font-semibold uppercase text-[#756763]"><T text="Pickup at" />{" "}{branch.name}</p>
                <label className="mt-2 block text-sm font-bold"><T text="Pickup date" />{" "}<input aria-label={translate("Pickup date")} type="date" min={today} max={max} value={intent.date ?? ""}
                        onChange={event => {
                            const date = event.target.value;
                            if (date && !validPickupDate(date,indiaToday(new Date()),features.futureOrderingDays)) {setDateError("Choose today or a future date in the booking window.");return;}
                            setDateError("");
                            if (features.cartSwitchPreview && !currentCart.isEmpty && date) setProposedDate(date);
                            else {
                                if (features.cartSwitchPreview) clearPickupSlot();
                                savePickupIntent(branch.id, date);
                            }
                        }}
                        className="ml-3 min-h-11 rounded-xl border border-[#eadfd6] px-3" />
                </label></div>
            {intent.selection && <Link href="/checkout/pickup" className="min-h-11 py-3 text-sm underline">
                {intent.selection.slot.startTime.slice(0, 5)} <T text="pickup · Change time" /><LinkFeedback /></Link>}
        </div>
        {dateError && <p role="alert" className="mt-2 text-sm text-red-700">{dateError}</p>}
        {intent.expired && <p role="status" className="mt-2 text-sm"><T text="Your previous pickup has passed. Choose a new date; your cart is saved." /></p>}
        {!intent.date ? <p className="mt-2 text-sm text-[#756763]"><T text="Choose pickup to check availability. Browse and build your cart first if you prefer." /></p>
            : intent.date < today ? <p role="alert" className="mt-2 text-sm"><T text="That pickup date has passed. Choose a new date; your cart is saved." /></p>
            : error ? <div role="alert"><p className="mt-2 text-sm">{error}</p><button onClick={retry} className="min-h-11 underline"><T text="Try again" /></button></div>
            : !data && hasItems ? <p role="status" className="mt-2 text-sm"><T text="Checking your pickup date..." /></p>
            : <p className="mt-2 text-xs text-[#756763]"><T text="Live preview, not a reservation." />{" "}{intent.selection ? "Quantities and this time are checked again at checkout." : "Choose a time for all items after building your cart."}</p>}
        {check.selectionUnavailable && <p role="status" className="mt-3 text-sm text-[#7a1625]"><T text="Your saved pickup time no longer fits this selection." />{" "}<Link href="/checkout/pickup" className="underline"><T text="Choose another time" /><LinkFeedback /></Link><T text=". Your cart is unchanged." /></p>}
        {cart && items?.some(item => !item.available) && <div role="status" className="mt-3 rounded-xl bg-[#fff0dc] p-3">
            <h3 className="font-bold"><T text="Review these items for your pickup" /></h3>
            {items.filter(item => !item.available).map(item => <p key={item.productId} className="mt-2 text-sm">
                <strong>{item.productName}</strong>: {item.reason}
                {item.code === "QUANTITY_TOO_LARGE" && ` Up to ${item.availableQuantity} ${item.unit === "GRAM" ? "g" : "pieces"} remain.`}
            </p>)}
            <p className="mt-2 text-sm"><T text="Edit quantities or remove items below, or keep your cart and find another pickup." /></p>
            <Link href="/checkout/pickup" className="inline-flex min-h-11 items-center font-semibold underline"><T text="Find a time for all items" /><LinkFeedback /></Link>
        </div>}
    </section>;
}
