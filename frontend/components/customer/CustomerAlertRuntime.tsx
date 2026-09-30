"use client";

import {useEffect} from "react";
import {apiClient, ApiError} from "@/services/apiClient";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {API_BASE_URL} from "@/lib/constants";
import {inQuietHours, playChime, type AlertSettings} from "@/lib/notificationAlerts";

/** No permission requests here. Sound requires a prior explicit activation in this tab. */
export default function CustomerAlertRuntime() {
    const features = useStorefrontFeatures();
    useEffect(() => {
        if (!features?.notificationAlerts) return;
        let active = true, pending = false, paused = false, initial = true, scope = "";
        const controller = new AbortController();
        async function poll() {
            if (!active || pending || paused || document.visibilityState !== "visible" || !navigator.onLine) return;
            pending = true;
            try {
                const settings = await apiClient<AlertSettings>("/api/customer/identity/notification-alerts", {credentials: "include", signal: controller.signal});
                const inbox = await apiClient<{messages: Array<{id: number; readAt: string | null; createdAt: string}>}>("/api/customer/identity/notifications", {credentials: "include", signal: controller.signal});
                if (!active) return;
                const max = Math.max(0, ...inbox.messages.map(message => message.id));
                const key = `gokul-alert-cursor:${API_BASE_URL}:${settings.scopeId}`;
                const baseline = initial || scope !== settings.scopeId;
                initial = false; scope = settings.scopeId;
                // Cross-tab lock + cursor. Unsupported locks/storage use the silent inbox fallback.
                if (!navigator.locks) return;
                await navigator.locks.request(key, {ifAvailable: true}, lock => {
                    if (!lock || !active) return;
                    try {
                        const previous = Number(localStorage.getItem(key) ?? 0);
                        localStorage.setItem(key, String(Math.max(previous, max)));
                        if (baseline || !settings.soundEnabled || inQuietHours(settings) || document.visibilityState !== "visible") return;
                        if (inbox.messages.some(message => message.id > previous && !message.readAt && Date.now() - new Date(message.createdAt).getTime() < 300_000)) playChime();
                    } catch { /* Storage unavailable: preserve a silent, duplicate-free fallback. */ }
                });
            } catch (reason) {
                if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) paused = true;
            } finally {pending = false;}
        }
        const identityChanged = () => {paused = false; initial = true; void poll();};
        const refresh = () => {void poll();};
        void poll();
        const timer = window.setInterval(refresh, 30_000);
        window.addEventListener("gokul-customer-identity-changed", identityChanged);
        window.addEventListener("gokul-alert-preferences-changed", refresh);
        window.addEventListener("online", refresh);
        document.addEventListener("visibilitychange", refresh);
        return () => {
            active = false; controller.abort(); window.clearInterval(timer);
            window.removeEventListener("gokul-customer-identity-changed", identityChanged);
            window.removeEventListener("gokul-alert-preferences-changed", refresh);
            window.removeEventListener("online", refresh);
            document.removeEventListener("visibilitychange", refresh);
        };
    }, [features?.notificationAlerts]);
    return null;
}
