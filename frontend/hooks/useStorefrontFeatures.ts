"use client";

import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";

export interface StorefrontFeatures {
    smartAvailability: boolean;
    smartPickupSelection: boolean;
    inventoryAutomationV2: boolean;
    customerHomeV2: boolean;
    homepageCampaigns: boolean;
    futureOrderingDays: number;
    today: string;
}

export function useStorefrontFeatures() {
    const [features, setFeatures] = useState<StorefrontFeatures | null>(null);
    useEffect(() => {
        const controller = new AbortController();
        async function load() {
            try {
                const result = await apiClient<StorefrontFeatures>("/api/storefront/features", {
                    signal: AbortSignal.any([controller.signal, AbortSignal.timeout(5000)])
                });
                if (!controller.signal.aborted) setFeatures(result);
            } catch (error) {
                if (!controller.signal.aborted) {
                    console.warn("Optional storefront configuration unavailable; using baseline ordering.", error);
                    setFeatures(null);
                }
            }
        }
        void load();
        const timer = window.setInterval(load, 60_000);
        return () => {controller.abort(); window.clearInterval(timer);};
    }, []);
    return features;
}
