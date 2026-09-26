"use client";

import {useEffect, useRef, useState} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getCartSnapshot} from "@/lib/cartStorage";
import {getStoredBranchSnapshot} from "@/lib/branchStorage";
import {getPickupSlotSnapshot} from "@/lib/checkoutStorage";
import {previewCartSwitch, type CartSwitchPreview} from "@/services/cartSwitchPreview";
import type {CartItem} from "@/types/cart";

interface Props {
    branchId: number;
    branchName: string;
    date: string;
    items: CartItem[];
    onKeep: () => void;
    onSwitch: (preview: CartSwitchPreview) => void;
}

const money = (amount: number) => new Intl.NumberFormat("en-IN", {style: "currency", currency: "INR"}).format(amount);

export default function CartSwitchDialog({branchId, branchName, date, items, onKeep, onSwitch}: Props) {
    const accessible = useStorefrontFeatures()?.accessibleOrderingV2 === true;
    const dialogRef = useRef<HTMLDivElement>(null);
    const onKeepRef = useRef(onKeep);
    const [preview, setPreview] = useState<CartSwitchPreview | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const busyRef = useRef(false);
    const [snapshots, setSnapshots] = useState<{cart: string; branch: string; pickup: string} | null>(null);

    useEffect(() => {onKeepRef.current = onKeep;}, [onKeep]);
    useEffect(() => {busyRef.current = busy;}, [busy]);

    useEffect(() => {
        if (!accessible) return;
        const previous = document.activeElement;
        const dialog = dialogRef.current;
        const previousOverflow = document.body.style.overflow;
        document.body.style.overflow = "hidden";
        const focusable = () => Array.from(dialog?.querySelectorAll<HTMLElement>(
            'button:not(:disabled), a[href], input:not(:disabled), [tabindex="0"]'
        ) ?? []).filter(element => element.getClientRects().length > 0);
        focusable()[0]?.focus();
        function onKeyDown(event: KeyboardEvent) {
            if (event.key === "Escape") {event.preventDefault(); if (!busyRef.current) onKeepRef.current(); return;}
            if (event.key !== "Tab") return;
            const targets = focusable();
            if (!targets.length) {event.preventDefault(); return;}
            const first = targets[0]; const last = targets[targets.length - 1];
            if (event.shiftKey && document.activeElement === first) {event.preventDefault(); last.focus();}
            else if (!event.shiftKey && document.activeElement === last) {event.preventDefault(); first.focus();}
        }
        dialog?.addEventListener("keydown", onKeyDown);
        return () => {
            dialog?.removeEventListener("keydown", onKeyDown);
            document.body.style.overflow = previousOverflow;
            if (previous instanceof HTMLElement && previous.isConnected) previous.focus();
        };
    }, [accessible]);

    useEffect(() => {
        const controller = new AbortController();
        const current = {cart: getCartSnapshot(), branch: getStoredBranchSnapshot(), pickup: getPickupSlotSnapshot()};
        previewCartSwitch(branchId, date, items, AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]))
            .then(value => {if (!controller.signal.aborted) {setPreview(value); setSnapshots(current);}})
            .catch(() => {if (!controller.signal.aborted) setError("We couldn't verify this switch. Your cart is unchanged. Try again.");});
        return () => controller.abort();
    }, [branchId, date, items]);

    async function confirm() {
        if (!preview || !snapshots || busy) return;
        const unchanged = () => getCartSnapshot() === snapshots.cart &&
            getStoredBranchSnapshot() === snapshots.branch && getPickupSlotSnapshot() === snapshots.pickup;
        if (!unchanged()) {setError("Your cart or pickup changed. Close this preview and try again."); return;}
        setBusy(true);
        try {
            // Recheck immediately before applying; a stale stock or price response cannot silently authorize a switch.
            const latest = await previewCartSwitch(branchId, date, items, AbortSignal.timeout(10000));
            if (!unchanged()) {setError("Your cart or pickup changed. Close this preview and try again."); return;}
            if (JSON.stringify(latest) !== JSON.stringify(preview)) {
                setPreview(latest);
                setError("Prices, stock or times changed. Review the updated preview before confirming again.");
                return;
            }
            setError(null);
            onSwitch(latest);
        } catch {
            setError("We couldn't recheck the switch. Your cart is unchanged. Try again.");
        } finally {setBusy(false);}
    }

    return <div ref={dialogRef} role="dialog" aria-modal="true" aria-label="Review cart before switching" className="fixed inset-0 z-[100] flex items-center justify-center bg-black/60 p-4">
        <div className="max-h-[90dvh] w-full max-w-lg overflow-y-auto rounded-2xl bg-white p-5 shadow-xl">
            <h2 className="text-xl font-bold">Review your cart for {branchName}</h2>
            <p className="mt-2 text-sm text-[#756763]">Pickup date: {date} (India time). Nothing changes until you confirm.</p>
            {!preview && !error && <p role="status" className="mt-4">Checking menu, stock and pickup times...</p>}
            {preview && <>
                <ul className="mt-4 divide-y divide-[#eadfd6]">
                    {preview.lines.map((line, index) => <li key={`${line.item.product.id}-${index}`} className="py-3 text-sm">
                        <p className="font-semibold">{line.item.product.name} · {line.item.product.saleMode === "WEIGHT" ? `${line.item.weightGrams} g` : `${line.item.quantity} pcs`}</p>
                        <p>Price: {money(line.item.product.price)} → {line.proposed ? money(line.proposed.price) : "Not offered"}{line.item.product.saleMode === "WEIGHT" ? "/kg" : " each"}</p>
                        <p className={line.orderable ? "text-green-700" : "text-[#7a1625]"}>
                            {line.orderable ? "Available for this date" : line.reason}</p>
                        {!line.orderable && line.availableQuantity !== null && <p>Available quantity: {line.availableQuantity} {line.item.product.saleMode === "WEIGHT" ? "g" : "pcs"}</p>}
                    </li>)}
                </ul>
                <p className="mt-3 text-sm">Pickup times with capacity: {preview.availableSlots}</p>
                <p className="mt-2 font-semibold">Item subtotal: {money(preview.oldSubtotal)} → {preview.newSubtotal === null ? "Cannot quote until conflicts are resolved" : money(preview.newSubtotal)}</p>
                <p className="mt-1 text-xs text-[#756763]">Taxes and any priority pickup charge depend on the time you choose at checkout. Review the final amount before paying.</p>
                {preview.conflicts && <p className="mt-2 text-sm font-semibold text-[#7a1625]">The cart stays saved. Unavailable lines need attention before checkout.</p>}
            </>}
            {error && <p role="alert" className="mt-3 text-sm text-[#7a1625]">{error}</p>}
            <div className="mt-5 flex flex-wrap gap-3">
                <button type="button" disabled={accessible && busy} onClick={onKeep} className="min-h-11 rounded-xl border px-4 font-semibold">Keep current selection</button>
                <button type="button" disabled={!preview || busy} onClick={() => void confirm()}
                    className="min-h-11 rounded-xl bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50">{busy ? "Rechecking..." : "Accept and switch"}</button>
                {error && <button type="button" onClick={onKeep} className="min-h-11 px-2 underline">Close and retry</button>}
            </div>
        </div>
    </div>;
}
