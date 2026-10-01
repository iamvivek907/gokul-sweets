"use client";

import {useEffect, useSyncExternalStore} from "react";
import {apiClient} from "@/services/apiClient";

export interface StorefrontFeatures {
    smartAvailability: boolean;
    smartPickupSelection: boolean;
    inventoryAutomationV2: boolean;
    customerHomeV2: boolean;
    homepageCampaigns: boolean;
    preHomeIntentGateway: boolean;
    contextualStorefrontV2: boolean;
    controlledCampaignPublishing: boolean;
    branchExperience: boolean;
    occasionEnquiries: boolean;
    occasionPayments: boolean;
    accessibleOrderingV2: boolean;
    checkoutExperienceV2: boolean;
    futuristicStorefrontV2: boolean;
    persistentPickupContext: boolean;
    cartSwitchPreview: boolean;
    inPlaceBranchSwitch: boolean;
    authoritativePickupCommitment: boolean;
    acceptedCheckoutQuote: boolean;
    truthfulOrderTracking: boolean;
    paidCartRecovery: boolean;
    paymentPollingV2: boolean;
    deliveryLocalityCheck: boolean;
    deliveryZones: boolean;
    deliveryCapacity: boolean;
    deliveryAddressBoundaries: boolean;
    deliveryCheckout: boolean;
    customerAccountHub: boolean;
    notificationInbox: boolean;
    notificationAlerts: boolean;
    brandCareers: boolean;
    pickupAddOns: boolean;
    futureOrderingDays: number;
    today: string;
}

type Configuration = {features: StorefrontFeatures | null; error: string | null; checkedAt: number};
const initial: Configuration = {features: null, error: null, checkedAt: 0};
let state = initial;
let pending: Promise<void> | null = null;
let lastAttemptAt = 0;
let retryTimer: ReturnType<typeof setTimeout> | null = null;
let consumers = 0;
const listeners = new Set<() => void>();
const notify = () => listeners.forEach(listener => listener());
const subscribe = (listener: () => void) => {listeners.add(listener); return () => {listeners.delete(listener);};};
const snapshot = () => state;
const serverSnapshot = () => initial;
const cacheKey = "gokul-storefront-settings";

function scheduleRefresh() {
    if (retryTimer) clearTimeout(retryTimer);
    if (consumers === 0) return;
    const interval = state.error ? 15_000 : 60_000;
    retryTimer = setTimeout(() => {
        retryTimer = null;
        if (document.visibilityState === "visible") void refresh();
    }, Math.max(1000, interval - (Date.now() - lastAttemptAt)));
}

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
    if (!force && Date.now() - lastAttemptAt < (state.error ? 15_000 : 60_000)) {scheduleRefresh(); return;}
    if (typeof navigator !== "undefined" && !navigator.onLine) return;
    lastAttemptAt = Date.now();
    pending = (async () => {
        try {
            const features = await apiClient<StorefrontFeatures>("/api/storefront/features", {signal: AbortSignal.timeout(5000)});
            state = {features: JSON.stringify(features) === JSON.stringify(state.features) ? state.features : features,
                error: null, checkedAt: Date.now()};
            try {sessionStorage.setItem(cacheKey, JSON.stringify(state));}
            catch (error) {console.warn("Ordering settings could not be saved for this tab.", error);}
        } catch (error) {
            console.warn("Storefront configuration check failed.", error);
            // Retain the last known layout for this tab; never turn a network error into a flag-OFF response.
            state = {...state, error: "We couldn't refresh ordering settings. Your cart is saved.",
                features: state.features};
        } finally {
            pending = null; notify();
            scheduleRefresh();
        }
    })();
    return pending;
}

export function useStorefrontConfiguration() {
    const configuration = useSyncExternalStore(subscribe, snapshot, serverSnapshot);
    useEffect(() => {
        consumers++;
        void refresh();
        const resume = () => {
            if (document.visibilityState === "visible") void refresh();
        };
        window.addEventListener("focus", resume);
        window.addEventListener("online", resume);
        document.addEventListener("visibilitychange", resume);
        return () => {
            consumers--;
            window.removeEventListener("focus", resume);
            window.removeEventListener("online", resume);
            document.removeEventListener("visibilitychange", resume);
            if (consumers === 0 && retryTimer) {clearTimeout(retryTimer); retryTimer = null;}
        };
    }, []);
    return {...configuration, retry: () => {void refresh(true);}};
}

export function useStorefrontFeatures() {
    return useStorefrontConfiguration().features;
}
