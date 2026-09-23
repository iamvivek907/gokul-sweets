"use client";

import {useSyncExternalStore} from "react";
import {getPickupSlotSnapshot, getServerPickupSlotSnapshot, parsePickupSlot, subscribeToPickupSlot} from "@/lib/checkoutStorage";

const key = "gokul-pickup-intent";
const event = "gokul-pickup-intent-change";
const snapshot = () => localStorage.getItem(key) ?? "";
const server = () => "";
function subscribe(listener: () => void) {
    window.addEventListener(event, listener); window.addEventListener("storage", listener);
    return () => {window.removeEventListener(event, listener); window.removeEventListener("storage", listener);};
}
export function savePickupIntent(branchId: number, date: string) {
    localStorage.setItem(key, JSON.stringify({branchId, date})); window.dispatchEvent(new Event(event));
}
export function usePickupIntent(branchId?: number | null) {
    const raw = useSyncExternalStore(subscribe, snapshot, server);
    const pickupRaw = useSyncExternalStore(subscribeToPickupSlot, getPickupSlotSnapshot, getServerPickupSlotSnapshot);
    const selection = parsePickupSlot(pickupRaw);
    let date: string | null = null;
    try {
        const value = raw ? JSON.parse(raw) : null;
        if (value?.branchId === branchId && /^\d{4}-\d{2}-\d{2}$/.test(value.date)) date = value.date;
    } catch { /* Ignore a malformed local preference; never treat it as availability. */ }
    if (!date && selection && selection.slot.branchId === branchId) date = selection.date;
    return {date, selection: selection && selection.slot.branchId === branchId && selection.date === date ? selection : null};
}
