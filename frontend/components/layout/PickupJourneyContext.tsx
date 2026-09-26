"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useSyncExternalStore} from "react";
import {useCart} from "@/hooks/useCart";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {usePickupIntent} from "@/hooks/usePickupIntent";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {formatBusinessTime, parseBusinessDate} from "@/lib/businessTime";
import BranchSelector from "@/components/branch/BranchSelector";
import {getPickupSlotSnapshot, getServerPickupSlotSnapshot, parsePickupSlot, subscribeToPickupSlot} from "@/lib/checkoutStorage";

export function pickupDateLabel(date: string): string {
    const value = parseBusinessDate(date);
    return Number.isNaN(value.getTime()) ? date : new Intl.DateTimeFormat("en-IN", {
        day: "numeric", month: "short", year: "numeric", timeZone: "Asia/Kolkata"
    }).format(value);
}

export default function PickupJourneyContext() {
    const pathname = usePathname();
    const features = useStorefrontFeatures();
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const {date, selection} = usePickupIntent(branch?.id);
    const slotSnapshot = useSyncExternalStore(subscribeToPickupSlot, getPickupSlotSnapshot, getServerPickupSlotSnapshot);
    // Checkout pages display the saved slot used by the review and order request.
    // A newer browsing date preference must not hide a still-selected checkout slot.
    const savedSelection = parsePickupSlot(slotSnapshot);
    const checkoutSelection = features?.inPlaceBranchSwitch && pathname.startsWith("/checkout/") && savedSelection?.slot.branchId === branch?.id
        ? savedSelection : null;

    // Committed orders must use their own server-confirmed branch and pickup time.
    if (!features?.persistentPickupContext || !branch ||
        !(pathname === "/menu" || pathname === "/cart" ||
          pathname === "/checkout/pickup" || pathname === "/checkout/customer" ||
          pathname === "/checkout/review")) return null;

    const mismatchedCart = !cart.isEmpty && cart.branchId !== branch.id;
    const activeSelection = checkoutSelection ?? selection;
    const slot = activeSelection?.slot;
    const displayedDate = checkoutSelection?.date ?? date;
    return <aside aria-label="Current pickup plan" className="mx-auto mt-3 w-full max-w-[1180px] px-4 sm:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3 rounded-2xl border border-[#eadfd6] bg-white px-4 py-3 text-sm shadow-sm">
            <div>
                <p className="text-xs font-semibold uppercase tracking-wide text-[#756763]">Pickup · {branch.name}</p>
                <p className="mt-1 font-semibold text-[#241715]">
                    {mismatchedCart ? "Cart belongs to another branch — review before checkout"
                        : slot && activeSelection ? `${pickupDateLabel(activeSelection.date)} · ${formatBusinessTime(slot.startTime)}–${formatBusinessTime(slot.endTime)} IST`
                        : displayedDate ? `${pickupDateLabel(displayedDate)} · Time not chosen` : "Choose a pickup time at checkout"}
                </p>
                {slot && activeSelection && <p className="mt-1 text-xs text-[#756763]">{activeSelection.pickupType === "PRIORITY" ? "Priority" : "Normal"} · Time is checked again at checkout</p>}
            </div>
            <div className="flex gap-3">
                {features.inPlaceBranchSwitch ? <BranchSelector compact /> :
                    <Link href="/" className="inline-flex min-h-11 items-center font-semibold text-[#7a1625] underline">Change branch</Link>}
                <Link href="/checkout/pickup" className="inline-flex min-h-11 items-center font-semibold text-[#7a1625] underline">{slot ? "Change time" : "Choose time"}</Link>
            </div>
        </div>
    </aside>;
}
