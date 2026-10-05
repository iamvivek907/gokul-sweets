"use client";

import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";

export type PublicOffer = {
    rebateId: number; code: string; name: string; description: string | null;
    rebateType: string; rebateValue: number | null; minimumOrderAmount: number;
    maximumDiscountAmount: number | null;
    // Optional during a frontend/backend rolling deployment. Explicit UTC from the server.
    validUntil?: string;
    tiers: {minimumOrderAmount: number; rebateAmount: number}[];
};
type Catalog = {branchId: number; offers: PublicOffer[]; failed: boolean; loading: boolean; revision: number};

export function usePublicMenuOffers(branchId: number, retry: number) {
    const [catalog, setCatalog] = useState<Catalog | null>(null);
    useEffect(() => {
        let active = true, revision = 0;
        let controller: AbortController | null = null;
        let timer: ReturnType<typeof setTimeout> | undefined;
        const schedule = (offers: PublicOffer[], failed: boolean) => {
            const now = Date.now();
            const expiries = offers.map(o => Date.parse(o.validUntil ?? "")).filter(at => at > now);
            const delay = Math.min(failed ? 15000 : 60000, ...expiries.map(at => at - now));
            timer = setTimeout(load, Math.max(1, delay));
        };
        async function load() {
            if (!active || controller) return; // Deduplicate focus/visibility/online events.
            clearTimeout(timer);
            // Never keep stale promotion claims while a refresh is pending or failed.
            const currentRevision = ++revision;
            setCatalog({branchId, offers: [], failed: false, loading: true, revision: currentRevision});
            if (document.visibilityState !== "visible") return;
            const c = new AbortController(); controller = c;
            try {
                const value = await apiClient<PublicOffer[]>(`/api/menu/offers?branchId=${branchId}`, {
                    cacheMode: "no-store", signal: AbortSignal.any([c.signal, AbortSignal.timeout(5000)])
                });
                if (!Array.isArray(value)) throw new Error("Invalid offer catalogue");
                if (!active || c.signal.aborted) return;
                const now = Date.now();
                const offers = value.filter(o => !o.validUntil || Date.parse(o.validUntil) > now);
                setCatalog({branchId, offers, failed: false, loading: false, revision: currentRevision});
                schedule(offers, false);
            } catch {
                if (!active || c.signal.aborted) return;
                setCatalog({branchId, offers: [], failed: true, loading: false, revision: currentRevision});
                schedule([], true);
            } finally { if (controller === c) controller = null; }
        }
        const resume = () => { if (document.visibilityState === "visible") void load(); };
        const pageshow = (event: PageTransitionEvent) => { if (event.persisted) resume(); };
        void load();
        window.addEventListener("focus", resume);
        window.addEventListener("online", resume);
        window.addEventListener("pageshow", pageshow);
        document.addEventListener("visibilitychange", resume);
        return () => {
            active = false; controller?.abort(); clearTimeout(timer);
            window.removeEventListener("focus", resume);
            window.removeEventListener("online", resume);
            window.removeEventListener("pageshow", pageshow);
            document.removeEventListener("visibilitychange", resume);
        };
    }, [branchId, retry]);
    return catalog?.branchId === branchId ? catalog : {offers: [], failed: false, loading: true, revision: 0};
}
