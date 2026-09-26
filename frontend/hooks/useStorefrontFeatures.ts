"use client";

import {useEffect, useSyncExternalStore} from "react";
import {apiClient} from "@/services/apiClient";

export interface StorefrontFeatures {
    smartAvailability: boolean;
    smartPickupSelection: boolean;
    inventoryAutomationV2: boolean;
    customerHomeV2: boolean;
    homepageCampaigns: boolean;
    persistentPickupContext: boolean;
    cartSwitchPreview: boolean;
    inPlaceBranchSwitch: boolean;
    authoritativePickupCommitment: boolean;
    acceptedCheckoutQuote: boolean;
    truthfulOrderTracking: boolean;
    paidCartRecovery: boolean;
    futureOrderingDays: number;
    today: string;
}

type Configuration = {features: StorefrontFeatures | null; error: string | null; checkedAt: number};
const initial: Configuration = {features: null, error: null, checkedAt: 0};
let state = initial;
let pending: Promise<void> | null = null;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach(listener => listener());
const subscribe = (listener: () => void) => {listeners.add(listener); return () => {listeners.delete(listener);};};
const snapshot = () => state;
const serverSnapshot = () => initial;
const cacheKey = "gokul-storefront-settings";

async function refresh(force = false) {
    if (pending) return pending;
    if (state === initial) {
        try {
            const saved = JSON.parse(sessionStorage.getItem(cacheKey) ?? "null") as Configuration | null;
            if (saved?.features && Date.now() - saved.checkedAt < 15 * 60_000) {
                state = saved; notify();
            }
        } catch (error) {console.warn("Saved ordering settings could not be read.", error);}
        force = true;
    }
    if (!force && Date.now() - state.checkedAt < 60_000) return;
    pending = (async () => {
        try {
            const features = await apiClient<StorefrontFeatures>("/api/storefront/features", {signal: AbortSignal.timeout(5000)});
            state = {features, error: null, checkedAt: Date.now()};
            try {sessionStorage.setItem(cacheKey, JSON.stringify(state));}
            catch (error) {console.warn("Ordering settings could not be saved for this tab.", error);}
        } catch (error) {
            console.warn("Storefront configuration check failed.", error);
            // Retain the last known layout for this tab; never turn a network error into a flag-OFF response.
            state = {...state, error: "We couldn't refresh ordering settings. Your cart is saved.",
                features: Date.now() - state.checkedAt < 15 * 60_000 ? state.features : null};
        } finally {pending = null; notify();}
    })();
    return pending;
}

export function useStorefrontConfiguration() {
    const configuration = useSyncExternalStore(subscribe, snapshot, serverSnapshot);
    useEffect(() => {
        void refresh();
        const timer = window.setInterval(() => {void refresh();}, 60_000);
        return () => window.clearInterval(timer);
    }, []);
    return {...configuration, retry: () => {void refresh(true);}};
}

export function useStorefrontFeatures() {
    return useStorefrontConfiguration().features;
}
