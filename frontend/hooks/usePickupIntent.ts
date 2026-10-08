"use client";

import {getMenuPickupModeSnapshot,subscribeMenuPickupMode,isSoonestPickup,setMenuPickupMode} from "@/lib/menuPickupMode";
import {useSyncExternalStore} from "react";
import {getPickupSlotSnapshot, getServerPickupSlotSnapshot, parsePickupSlot, subscribeToPickupSlot} from "@/lib/checkoutStorage";

import {usePickupClock} from "@/hooks/usePickupClock";
import {indiaToday,pickupIsFresh,validPickupDate} from "@/lib/pickupFreshness";
const key = "gokul-pickup-intent";
const event = "gokul-pickup-intent-change";
const snapshot = () => localStorage.getItem(key) ?? "";
const server = () => "";
function subscribe(listener: () => void) {
    window.addEventListener(event, listener); window.addEventListener("storage", listener);
    return () => {window.removeEventListener(event, listener); window.removeEventListener("storage", listener);};
}
export function savePickupIntent(branchId: number, date: string, automatic = false) {
    if (date && !validPickupDate(date,indiaToday(),60)) return;
    if (!automatic) setMenuPickupMode(branchId, false);
    localStorage.setItem(key, JSON.stringify({branchId, date})); window.dispatchEvent(new Event(event));
}
export function usePickupIntent(branchId?: number | null) {
    const raw = useSyncExternalStore(subscribe, snapshot, server);
    const pickupRaw = useSyncExternalStore(subscribeToPickupSlot, getPickupSlotSnapshot, getServerPickupSlotSnapshot);
    const modeRaw = useSyncExternalStore(subscribeMenuPickupMode,getMenuPickupModeSnapshot,server);
    const now=usePickupClock();
    const saved=parsePickupSlot(pickupRaw);
    const selection=saved && (!now || pickupIsFresh(saved,new Date(now))) ? saved : null;
    let date: string | null = null;
    try {
        const value = raw ? JSON.parse(raw) : null;
        if (value?.branchId === branchId && validPickupDate(value.date,value.date,0)) date = value.date;
    } catch { /* Ignore a malformed local preference; never treat it as availability. */ }
    if (!date && selection && selection.slot.branchId === branchId) date = selection.date;
    const previousDate = saved && saved.slot.branchId === branchId ? saved.date : date;
    const expired=!!saved && !selection || !!date && !!now && date < indiaToday(new Date(now));
    const preferenceMatches = !date || !saved || saved.slot.branchId !== branchId || date === saved.date;
    if (date && now && date < indiaToday(new Date(now))) date=null;
    return {date, expired, previousDate, pickupRaw, modeRaw, automatic: preferenceMatches && isSoonestPickup(modeRaw,branchId,pickupRaw,!!previousDate), selection: selection && selection.slot.branchId === branchId && selection.date === date ? selection : null};
}
