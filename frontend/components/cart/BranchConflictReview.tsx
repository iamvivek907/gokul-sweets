"use client";

import {useEffect, useState} from "react";
import {useRouter} from "next/navigation";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getCartSnapshot, saveCart} from "@/lib/cartStorage";
import {previewCartSwitch, type CartSwitchPreview} from "@/services/cartSwitchPreview";
import type {CartItem} from "@/types/cart";

export default function BranchConflictReview({branchId, items, removeItem, clearCart}: {
    branchId: number; items: CartItem[]; removeItem: (id: number) => void; clearCart: () => void;
}) {
    const router = useRouter();
    const features = useStorefrontFeatures();
    const {date} = usePickupIntent(branchId);
    const [result, setResult] = useState<{preview: CartSwitchPreview; cartSnapshot: string} | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const serviceDate = date ?? features?.today;
    useEffect(() => {
        if (!serviceDate || !items.length) return;
        const controller = new AbortController();
        const cartSnapshot = getCartSnapshot();
        previewCartSwitch(branchId, serviceDate, items, AbortSignal.any([controller.signal, AbortSignal.timeout(10000)]))
            .then(preview => {if (!controller.signal.aborted) {setResult({preview, cartSnapshot}); setError(null);}})
            .catch(() => {if (!controller.signal.aborted) {setResult(null); setError("Couldn't verify availability. The original cart is saved.");}});
        return () => controller.abort();
    }, [branchId, serviceDate, items]);

    async function applyAvailableItems() {
        if (!result || !serviceDate || busy) return;
        if (getCartSnapshot() !== result.cartSnapshot) {setError("Cart changed. Review it again."); return;}
        setBusy(true);
        try {
            const latest = await previewCartSwitch(branchId, serviceDate, items, AbortSignal.timeout(10000));
            if (getCartSnapshot() !== result.cartSnapshot || JSON.stringify(latest) !== JSON.stringify(result.preview)) {
                setError("Availability changed. Review the refreshed cart before switching.");
                setResult({preview: latest, cartSnapshot: getCartSnapshot()});
                return;
            }
            const available = latest.lines.filter(line => line.orderable && line.proposed);
            if (!available.length || latest.availableSlots === 0) {setError("Choose another date or browse this branch's menu."); return;}
            if (latest.conflicts && !window.confirm("Remove unavailable items and keep only available items for this branch?")) return;
            saveCart({branchId, items: available.map(line => ({...line.item, product: line.proposed!}))});
        } catch {setError("Couldn't recheck this branch. Your original cart is unchanged.");}
        finally {setBusy(false);}
    }

    const unavailable = result?.preview.lines.filter(line => !line.orderable) ?? [];
    return <section aria-label="Review cart for selected branch" className="mt-4 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm">
        <h2 className="font-bold">Review items for this branch</h2>
        <p className="mt-1">Your original cart is saved. Checkout is paused until you confirm the items for this branch.</p>
        {!result && !error && <p role="status" className="mt-2">Checking menu and stock...</p>}
        {result && <ul className="mt-3 space-y-2">{result.preview.lines.map(line =>
            <li key={line.item.product.id} className="rounded-lg bg-white p-3">
                <strong>{line.item.product.name}</strong> — {line.orderable ? "Available" : line.reason ?? "Unavailable"}
                {!line.orderable && <button type="button" className="ml-3 font-bold text-[#7a1625] underline" onClick={() => removeItem(line.item.product.id)}>Remove</button>}
            </li>)}</ul>}
        {result?.preview.availableSlots === 0 && <p className="mt-2 font-semibold">No pickup time has capacity for this date. Choose another date.</p>}
        {error && <p role="alert" className="mt-2 text-red-700">{error}</p>}
        <div className="mt-4 flex flex-wrap gap-4">
            {result && result.preview.lines.some(line => line.orderable) && <button type="button" disabled={busy || result.preview.availableSlots === 0} onClick={() => void applyAvailableItems()} className="min-h-11 rounded-lg bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50">{unavailable.length ? "Use available items" : "Confirm this branch"}</button>}
            {result && unavailable.length === result.preview.lines.length && <button type="button" onClick={() => {if (window.confirm("Clear the original cart and browse this branch's menu?")) {clearCart(); router.push("/menu");}}} className="min-h-11 font-bold text-[#7a1625] underline">Clear cart and choose again</button>}
        </div>
    </section>;
}
