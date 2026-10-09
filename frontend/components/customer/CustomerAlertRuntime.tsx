"use client";

import {useEffect} from "react";
import {apiClient, ApiError} from "@/services/apiClient";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {readCustomerInbox} from "@/services/customerInbox";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import {API_BASE_URL} from "@/lib/constants";
import {inQuietHours, playChime, type AlertSettings} from "@/lib/notificationAlerts";

/** No permission requests here. Sound requires a prior explicit activation in this tab. */
export default function CustomerAlertRuntime() {
    const features = useStorefrontFeatures();
    useEffect(() => {
        if (!features?.notificationAlerts) return;
        let active = true, paused = false, initial = true, scope = "";
        let pending: AbortController | null = null;
        async function poll() {
            if (!active || pending || paused || document.visibilityState !== "visible" || !navigator.onLine) return;
            const controller = new AbortController();
            pending = controller;
            try {
                const [settings, inbox] = await Promise.all([
                    apiClient<AlertSettings>("/api/customer/identity/notification-alerts", {credentials: "include", signal: AbortSignal.any([controller.signal, AbortSignal.timeout(8000)])}),
                    readCustomerInbox(controller.signal)
                ]);
                if (!active || pending !== controller || controller.signal.aborted) return;
                const max = Math.max(0, ...inbox.messages.map(message => message.id));
                const key = `gokul-alert-cursor:${API_BASE_URL}:${settings.scopeId}`;
                const baseline = initial || scope !== settings.scopeId;
                initial = false; scope = settings.scopeId;
                // Cross-tab lock + cursor. Unsupported locks/storage use the silent inbox fallback.
                if (!navigator.locks) return;
                await navigator.locks.request(key, {ifAvailable: true}, lock => {
                    if (!lock || !active || pending !== controller || controller.signal.aborted) return;
                    try {
                        const previous = Number(localStorage.getItem(key) ?? 0);
                        localStorage.setItem(key, String(Math.max(previous, max)));
                        if (baseline || !settings.soundEnabled || inQuietHours(settings) || document.visibilityState !== "visible") return;
                        if (inbox.messages.some(message => message.id > previous && !message.readAt && Date.now() - new Date(message.createdAt).getTime() < 300_000)) playChime();
                    } catch { /* Storage unavailable: preserve a silent, duplicate-free fallback. */ }
                });
            } catch (reason) {
                if (pending === controller && reason instanceof ApiError && [401, 403, 404].includes(reason.status)) paused = true;
            } finally {controller.abort(); if (pending === controller) pending = null;}
        }
        const identityChanged = () => {pending?.abort(); pending = null; paused = false; initial = true; void poll();};
        const refresh = () => {void poll();};
        void poll();
        const timer = window.setInterval(refresh, 30_000);
        const stopIdentity = subscribeCustomerIdentityChanges(identityChanged, {revalidateOnResume: false});
        window.addEventListener("gokul-alert-preferences-changed", refresh);
        const resume = () => {if (document.visibilityState === "visible") {paused = false; void poll();}};
        const pageshow = (event: PageTransitionEvent) => {if (event.persisted) resume();};
        window.addEventListener("focus", resume);
        window.addEventListener("pageshow", pageshow);
        window.addEventListener("online", resume);
        document.addEventListener("visibilitychange", resume);
        return () => {
            active = false; pending?.abort(); window.clearInterval(timer);
            stopIdentity();
            window.removeEventListener("gokul-alert-preferences-changed", refresh);
            window.removeEventListener("focus", resume);
            window.removeEventListener("pageshow", pageshow);
            window.removeEventListener("online", resume);
            document.removeEventListener("visibilitychange", resume);
        };
    }, [features?.notificationAlerts]);
    return null;
}
