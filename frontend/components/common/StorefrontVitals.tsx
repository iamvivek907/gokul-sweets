"use client";

import {usePathname} from "next/navigation";
import {getCartSnapshot,parseCart,subscribeToCart} from "@/lib/cartStorage";
import {useCallback, useEffect, useRef} from "react";
import {useReportWebVitals} from "next/web-vitals";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {API_BASE_URL} from "@/lib/constants";
import {safeVital, type VitalPayload} from "@/lib/vitals";

export default function StorefrontVitals() {
    const path=usePathname();
    const seen=useRef(new Set<string>());
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

    const stageCounts = useRef(new Map<string, number>());
    const report = useCallback((metric: {name: string; value: number; rating: string}) => {
        const payload = safeVital(metric.name, metric.value, metric.rating, location.pathname);
        if (!payload || !state.current.sample) return;
        if (payload.name.startsWith("MENU_")) {
            const count = stageCounts.current.get(payload.name) ?? 0;
            if (count >= 12) return;
            stageCounts.current.set(payload.name, count + 1);
        }
        if (state.current.enabled) send(payload);
        else if (state.current.enabled === undefined && state.current.queue.length < 24) state.current.queue.push(payload);
    }, [send]);
    // Coarse events are deduplicated per document. No customer, order or branch identifiers.
    const funnel=useCallback((name:string)=>{
        if(!safeVital(name,1,"measured",location.pathname)||seen.current.has(name))return;
        seen.current.add(name);report({name,value:1,rating:"measured"});
    },[report]);
    useEffect(()=>{
        if(enabled!==true)return;
        if(path==="/menu")funnel("FUNNEL_MENU");
        if(path.startsWith("/checkout"))funnel("FUNNEL_CHECKOUT");
    },[path,enabled,funnel]);
    useEffect(()=>{
        let previous=parseCart(getCartSnapshot()).items.reduce((sum,item)=>sum+item.quantity,0);
        return subscribeToCart(()=>{
            const count=parseCart(getCartSnapshot()).items.reduce((sum,item)=>sum+item.quantity,0);
            if(enabled===true&&location.pathname==="/menu"&&count>previous)funnel("FUNNEL_ADD");
            previous=count;
        });
    },[enabled,funnel]);
    useEffect(()=>{
        const handle=(event:Event)=>{
            const name=(event as CustomEvent<unknown>).detail;
            if(typeof name==="string"&&["FUNNEL_PAYMENT","FUNNEL_PAID","FUNNEL_FAILED","FUNNEL_EXPIRED"].includes(name))funnel(name);
        };
        window.addEventListener("gokul-payment-funnel",handle);
        return()=>window.removeEventListener("gokul-payment-funnel",handle);
    },[funnel]);
    useEffect(() => {
        const stage = (event: Event) => {
            const detail = (event as CustomEvent<{name: string; value: number; rating: string}>).detail;
            if (detail) report(detail);
        };
        window.addEventListener("gokul-loading-stage", stage);
        return () => window.removeEventListener("gokul-loading-stage", stage);
    }, [report]);
    useEffect(() => {
        if (!enabled || !state.current.sample || typeof PerformanceObserver === "undefined") return;
        let images = 0;
        const observer = new PerformanceObserver(list => {
            if (!location.pathname.startsWith("/menu")) return;
            for (const entry of list.getEntries()) {
                if ((entry as PerformanceResourceTiming).initiatorType === "img" && images < 12) {
                    images++;
                    report({name: "MENU_IMAGE", value: entry.duration, rating: "measured"});
                }
            }
        });
        try {observer.observe({type: "resource", buffered: true});} catch {observer.disconnect();}
        return () => observer.disconnect();
    }, [enabled, report]);
    useReportWebVitals(report);

    return null;
}
