"use client";
import {createMenuPreviewRefresh} from "@/lib/menuPreviewRefresh";
import {watchMenuState} from "@/services/menuStateWatch";
import {reportLoadingStage} from "@/lib/customerLoading";
import LinkFeedback from "@/components/common/LinkFeedback";

import {T,useTranslation} from "@/lib/language";

import Link from "next/link";
import {usePickupClock} from "@/hooks/usePickupClock";
import {indiaToday,pickupIsFresh,validPickupDate} from "@/lib/pickupFreshness";
import {useEffect, useMemo, useRef, useState} from "react";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {savePickupIntent, usePickupIntent} from "@/hooks/usePickupIntent";
import {availabilityItems, checkCartAvailability, checkMenuAvailability, discoverPickupDates, menuPreviewDeadline, MENU_READ_RETRY_DEADLINE_MS, readFreshMenuPreview, retryMenuRead, type CartAvailability} from "@/services/availabilityApi";
import CartSwitchDialog from "@/components/cart/CartSwitchDialog";
import {clearPickupSlot, getPickupSlotSnapshot, savePickupSlot} from "@/lib/checkoutStorage";
import {getCartSnapshot, parseCart} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {findSoonestMenuPickup} from "@/lib/soonestMenuPickup";
import {branchPickupOptions, menuItemsForPickup} from "@/lib/menuPickupOptions";
import {getMenuPickupModeSnapshot} from "@/lib/menuPickupMode";
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
    const refresh = useMemo(() => createMenuPreviewRefresh(() => setRevision(value => value + 1)), []);
    const [result, setResult] = useState<{key: string; scope: string; data?: CartAvailability; error?: string} | null>(null);
    const candidates = useMemo(() => products?.filter(product => product.available).map(product => ({
        productId: product.id, quantity: product.saleMode === "WEIGHT" ? null : 1,
        weightGrams: product.saleMode === "WEIGHT" ? product.minimumWeightGrams : null
    })) ?? [], [products]);
    // Always include the real cart, even when search/category filters hide its products.
    const requested = useMemo(() => {
        const amounts = new Map((cart.branchId === branch?.id ? availabilityItems(cart.items, parseCart(cart.snapshot)) : [])
            .map(item => [item.productId, item]));
        return [...amounts.values(), ...candidates.filter(item => !amounts.has(item.productId))]
            .sort((a, b) => a.productId - b.productId);
    }, [branch?.id, candidates, cart.branchId, cart.items, cart.snapshot]);
    const menuPreview=!!products;
    const itemsJson = useMemo(() => JSON.stringify(requested), [requested]);
    const requestedScope=useRef<string|null>(null);
    const recentMenuReads=useRef(new Map<string,CartAvailability>());
    const [choosing,setChoosing] = useState(false);
    const [automaticResult,setAutomaticResult] = useState<{key:string; error?:string; empty?:boolean} | null>(null);
    const requestDate = intent.date;
    const previewSlotId = menuPreview && intent.selection?.date === requestDate
        ? intent.selection?.slot.id : undefined;
    // Service-rule edits change the live menu revision even when all SKUs remain browsable.
    const menuRevision = products?.[0]?.availabilityRevision ?? null;
    const key = JSON.stringify([branch?.id, requestDate, itemsJson, revision, features?.smartAvailability,menuPreview,menuRevision,previewSlotId]);
    // Retain the last preview while quantities refresh, but never across branch,
    // date, slot, or catalogue changes. Checkout still checks the exact cart.
    const scope = useMemo(() => JSON.stringify([branch?.id,intent.date,intent.selection?.slot.id,intent.selection?.pickupType,features?.smartAvailability,menuPreview,candidates,menuRevision]),
        [branch?.id, intent.date, intent.selection?.slot.id, intent.selection?.pickupType, features?.smartAvailability, menuPreview, candidates, menuRevision]);
    const hasRequested = requested.length > 0;
    const validDate = requestDate && features?.smartAvailability && Number.isInteger(features.futureOrderingDays) && validPickupDate(requestDate,today,features.futureOrderingDays);
    useEffect(() => {
        if (!features?.smartAvailability || !branch || !requestDate || !validDate || !hasRequested) return;
        const cached=menuPreview?recentMenuReads.current.get(key):undefined;
        if(cached&&performance.now() < Math.min((cached.receivedMonotonic??0)+8000,menuPreviewDeadline(cached)??Infinity)){
            let active=true;
            queueMicrotask(()=>{if(active)setResult({key,scope,data:cached});});
            return()=>{active=false;};
        }
        const controller = new AbortController();
        const release = refresh.acquire();
        const firstInScope=requestedScope.current!==scope;requestedScope.current=scope;
        const timer = window.setTimeout(() => {
            const started = performance.now();
            const items = JSON.parse(itemsJson);
            const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
            const read = menuPreview
                ? readFreshMenuPreview(() => checkMenuAvailability(branch.id, requestDate, 1, items, signal, previewSlotId), signal)
                : retryMenuRead(attempt => checkCartAvailability(branch.id, requestDate, 1, items, attempt), signal);
            void read
                .then(data => {
                    if (controller.signal.aborted) return;
                    if (menuPreview){
                        reportLoadingStage("MENU_PREVIEW", started);
                        const reads=recentMenuReads.current;
                        reads.delete(key);reads.set(key,data);
                        if(reads.size>3)reads.delete(reads.keys().next().value!);
                    }
                    setResult({key, scope, data});

                })
                .catch(error => {if (!controller.signal.aborted) {
                    console.warn("Date availability check failed.", error);
                    setResult({key, scope, error: "We couldn't check this pickup. Your cart is saved. Try again."});
                }}).finally(() => release());
        }, firstInScope?0:150);
        return () => {controller.abort(); window.clearTimeout(timer); release(false);};
    }, [features?.smartAvailability, branch, requestDate, validDate, hasRequested, itemsJson, key,scope,menuPreview,previewSlotId,refresh]);
    // Recheck slot capacity, cutoff and preparation as time passes, without changing the pickup.
    useEffect(() => {
        if (!menuPreview || !features?.smartAvailability) return;
        const resume = () => {if (navigator.onLine && document.visibilityState === "visible") refresh.request();};
        const timer = window.setInterval(resume, 60000);
        window.addEventListener("online", resume); document.addEventListener("visibilitychange", resume);
        return () => {clearInterval(timer); window.removeEventListener("online", resume); document.removeEventListener("visibilitychange", resume);};
    }, [menuPreview, features?.smartAvailability, refresh]);
    const branchId = branch?.id;
    useEffect(() => {
        if (!menuPreview || !features?.smartAvailability || !branchId || !requestDate || !validDate || !hasRequested) return;
        const controller = new AbortController();
        watchMenuState(branchId, requestDate, refresh.request, controller.signal);
        return () => controller.abort();
    }, [menuPreview, features?.smartAvailability, branchId, requestDate, validDate, hasRequested, refresh]);
    // Server time boundaries are independent of stock mutations. Never reuse a preview past one.
    const refreshedBoundary = useRef<string | null>(null);
    const snapshot = result?.data && (result.key === key || menuPreview && result.scope === scope)
        ? result.data : undefined;
    const recheckAt = snapshot?.recheckAt;
    const deadline = snapshot ? menuPreviewDeadline(snapshot) : null;
    useEffect(() => {
        if (!menuPreview || !recheckAt || deadline === null) return;
        const boundaryKey = JSON.stringify([branchId, requestDate, previewSlotId, recheckAt]);
        const remaining = deadline - performance.now();
        if (!Number.isFinite(remaining)) return;
        const timer = setTimeout(() => {
            setResult(current => current?.data === snapshot ? null : current);
            if (refreshedBoundary.current !== boundaryKey && navigator.onLine && document.visibilityState === "visible") {
                // A fast device clock or a late response must not create a refresh loop.
                refreshedBoundary.current = boundaryKey;
                refresh.request();
            }
        }, Math.max(0, Math.min(Math.ceil(remaining), 2147483647)));
        return () => clearTimeout(timer);
    }, [menuPreview, recheckAt, deadline, branchId, requestDate, previewSlotId, refresh, snapshot]);
    const data = result?.key === key ? result.data : undefined;
    const day = snapshot?.dates[0];
    const selectedSlot = day?.slots.find(value => value.slot.id === intent.selection?.slot.id);
    // Menu previews include unchosen products; only the actual cart can invalidate its saved pickup.
    const cartIds = new Set(cart.branchId === branch?.id ? cart.items.map(item => item.product.id) : []);
    const selectionUnavailable = !!intent.selection && !!day && (!selectedSlot
        || selectedSlot.code === "PICKUP_WINDOW"
        || (intent.selection.pickupType === "PRIORITY"
            ? !selectedSlot.slot.priorityEnabled || selectedSlot.slot.priorityRemainingCapacity <= 0
            : selectedSlot.slot.remainingCapacity <= 0)
        || selectedSlot.issues?.some(issue => cartIds.has(issue.productId)));
    const pickupType = intent.selection?.pickupType;
    const items = useMemo(() => menuItemsForPickup(day?.items, selectedSlot, pickupType),
        [day, selectedSlot, pickupType]);
    // Match the date-intent snapshot guard below: date-only changes must abort
    // the old verification and start a new one, even when slot/mode stay equal.
    const autoKey = JSON.stringify([branch?.id,today,features?.futureOrderingDays,itemsJson,intent.dateRaw,intent.pickupRaw,intent.modeRaw,revision,menuRevision,choosing,cart.isEmpty]);
    const selectedStillFits = !!data && !!intent.selection && branchPickupOptions(data)
        .some(value=>value.date===intent.selection?.date && value.slot.id===intent.selection.slot.id && value.pickupType==="NORMAL");
    const pendingCheckout = !!clock && (!!localStorage.getItem("gokul-pending-order") || !!localStorage.getItem("gokul-pending-payment") || !!sessionStorage.getItem("gokul-mobile-order-attempt"));
    const needsAutomatic = !!features?.smartAvailability && (candidates.length > 0 || cart.isEmpty) && !pendingCheckout && !!clock && menuPreview && (intent.automatic || intent.expired) && (cart.isEmpty || cart.branchId===branch?.id&&(!intent.selection||intent.expired)) && !choosing && (!intent.selection || !validDate || (!!data && !selectedStillFits));
    useEffect(() => {
        if (!needsAutomatic || !branch || !features?.smartAvailability || !today || !Number.isInteger(features.futureOrderingDays)) return;
        const pending = () => !!localStorage.getItem("gokul-pending-order") || !!localStorage.getItem("gokul-pending-payment") || !!sessionStorage.getItem("gokul-mobile-order-attempt");
        if (pending()) return;
        const actualCart=cart.branchId===branch.id?availabilityItems(cart.items):[];
        if(actualCart.length>100)return;
        const businessDayBefore = indiaToday();
        const before = [getStoredBranchSnapshot(),getPickupSlotSnapshot(),getCartSnapshot(),localStorage.getItem("gokul-pickup-intent"),getMenuPickupModeSnapshot()];
        const controller = new AbortController();
        const signal = AbortSignal.any([controller.signal,AbortSignal.timeout(30000)]);
        const release = refresh.acquire();
        const timer = window.setTimeout(() => {
            setAutomaticResult({key:autoKey});
            void retryMenuRead(attempt=>discoverPickupDates(branch.id,today,features.futureOrderingDays+1,attempt),signal)
                .then(discovery=>findSoonestMenuPickup(discovery,JSON.parse(itemsJson).map((item:{productId:number})=>item.productId),today,features.futureOrderingDays,
                    date => retryMenuRead(attempt => checkCartAvailability(branch.id, date, 1, actualCart, attempt), signal),
                    signal,()=>new Date(),!!actualCart.length))
                .then(first=>{
                    if (controller.signal.aborted) return;
                    const after = [getStoredBranchSnapshot(),getPickupSlotSnapshot(),getCartSnapshot(),localStorage.getItem("gokul-pickup-intent"),getMenuPickupModeSnapshot()];
                    if (pending() || before.some((value,index)=>value!==after[index]) || businessDayBefore!==indiaToday()) return;
                    if (first && !pickupIsFresh(first,new Date())) {refresh.request();return;}
                    setAutomaticResult({key:autoKey,empty:!first});
                    if (first) {savePickupIntent(branch.id,first.date,true);savePickupSlot(first,!actualCart.length);}
                }).catch(()=>{if (!controller.signal.aborted) setAutomaticResult({key:autoKey,error:"We couldn’t find the soonest pickup. Try again or choose a time."});}).finally(() => release());
        },0);
        return ()=>{controller.abort();clearTimeout(timer);release(false);};
    },[needsAutomatic,autoKey,branch,features?.smartAvailability,features?.futureOrderingDays,today,itemsJson,candidates.length,cart.branchId,cart.items,menuRevision,refresh]);
    const automaticState = automaticResult?.key===autoKey ? automaticResult : null;
    const noPickupMessage = automaticState?.empty ? "No verified normal pickup is available in the booking window. Choose a date to explore other times." : null;
    const pickupRequired = menuPreview && !!features?.smartAvailability && (!intent.selection || !validDate);
    return {features, today, branch, intent, data, items, deadline, stockRevision:JSON.stringify([revision,menuRevision]), selectionUnavailable: selectionUnavailable || !!intent.selection && !validDate, error: result?.key === key ? result.error : null,
        noPickupMessage, pickupRequired, choosing, setChoosing, findingSoonest: needsAutomatic && !automaticState?.empty && !automaticState?.error, automaticError: automaticState?.error, hasItems: requested.length > 0, retry: refresh.request};
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
                {intent.selection.date === today ? "Today" : intent.selection.date} · {intent.selection.slot.startTime.slice(0, 5)} IST <T text="pickup · Change time" /><LinkFeedback /></Link>}
        </div>
        {dateError && <p role="alert" className="mt-2 text-sm text-red-700">{dateError}</p>}
        {intent.expired && <p role="status" className="mt-2 text-sm"><T text="Your previous pickup has passed. Choose a new date; your cart is saved." /></p>}
        {check.noPickupMessage && <p role="status" className="mt-3 text-sm font-semibold text-[#7a1625]">{check.noPickupMessage} <Link href="/checkout/pickup" className="underline">Choose pickup</Link></p>}
        {!intent.date ? <p className="mt-2 text-sm text-[#756763]"><T text="Choose a pickup date and time to see which items you can add." /></p>
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
