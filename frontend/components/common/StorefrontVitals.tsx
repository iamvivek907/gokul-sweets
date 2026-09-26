"use client";

import {useCallback, useEffect, useRef} from "react";
import {useReportWebVitals} from "next/web-vitals";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {API_BASE_URL} from "@/lib/constants";
import {safeVital, type VitalPayload} from "@/lib/vitals";

export default function StorefrontVitals() {
    const enabled = useStorefrontFeatures()?.accessibleOrderingV2;
    const state = useRef<{enabled: boolean | undefined; sample: boolean; queue: VitalPayload[]}>(
        {enabled: undefined, sample: false, queue: []});
    useEffect(() => {state.current.sample = Math.random() < 0.1;}, []);

    const send = useCallback((payload: VitalPayload) => {
        void fetch(`${API_BASE_URL}/api/storefront/vitals`, {method: "POST", credentials: "omit", keepalive: true,
            headers: {"Content-Type": "application/json"}, body: JSON.stringify(payload)}).catch(() => {
            // Performance collection must not affect checkout or create a retry storm offline.
        });
    }, []);

    useEffect(() => {
        state.current.enabled = enabled;
        if (enabled) {
            if (state.current.sample) state.current.queue.forEach(send);
            state.current.queue = [];
        } else if (enabled === false) state.current.queue = [];
    }, [enabled, send]);

    const report = useCallback((metric: {name: string; value: number; rating: string}) => {
        const payload = safeVital(metric.name, metric.value, metric.rating, location.pathname);
        if (!payload || !state.current.sample) return;
        if (state.current.enabled) send(payload);
        else if (state.current.enabled === undefined && state.current.queue.length < 12) state.current.queue.push(payload);
    }, [send]);
    useReportWebVitals(report);

    return null;
}
