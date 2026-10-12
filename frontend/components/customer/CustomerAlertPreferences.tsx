"use client";

import {useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {activateChime, applicationServerKey, minuteToTime, timeToMinute, type AlertSettings} from "@/lib/notificationAlerts";
const base = "/api/customer/identity";

export default function CustomerAlertPreferences() {
    const features = useStorefrontFeatures();
    const [settings, setSettings] = useState<AlertSettings | null>(null);
    const [draft, setDraft] = useState<AlertSettings | null>(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [supported, setSupported] = useState(false);
    const [permission, setPermission] = useState<NotificationPermission>("default");
    const [pushId, setPushId] = useState<string | null>(null);
    useEffect(() => {
        if (!features?.notificationAlerts) return;
        let live = true;
        const support = window.isSecureContext && "Notification" in window && "PushManager" in window && "serviceWorker" in navigator;
        void apiClient<AlertSettings>(`${base}/notification-alerts`, {credentials: "include"}).then(value => {
            if (!live) return;
            setSettings(value); setDraft(value); setSupported(support);
            if ("Notification" in window) setPermission(Notification.permission);
            try {setPushId(localStorage.getItem(`gokul-push-device:${value.scopeId}`));}
            catch {setMessage("This browser cannot remember its push registration. Use the inbox instead.");}
        }).catch(() => {if (live) setMessage("Alert settings could not load. The notification inbox remains available.");});
        return () => {live = false;};
    }, [features?.notificationAlerts]);
    if (!features?.notificationAlerts) return null;
    async function save() {
        if (!draft) return;
        setBusy(true); setMessage("");
        try {
            const value = await apiClient<AlertSettings>(`${base}/notification-alerts`, {method: "PUT", credentials: "include", body: JSON.stringify(draft)});
            setSettings(value); setDraft(value); window.dispatchEvent(new Event("gokul-alert-preferences-changed"));
            setMessage("Alert preferences saved.");
        } catch {setMessage("We could not save alert preferences. Your previous settings still apply.");}
        finally {setBusy(false);}
    }
    async function enablePush() {
        if (!settings?.applicationServerKey || !supported) return;
        setBusy(true); setMessage("");
        let newlyCreated: PushSubscription | null = null;
        try {
            // Invoke permission before any await so the browser sees the explicit user gesture.
            const granted = await Notification.requestPermission();
            setPermission(granted);
            if (granted !== "granted") {setMessage("Browser alerts are not allowed. New updates will still appear in your inbox."); return;}
            const registration = await navigator.serviceWorker.register("/sw.js");
            await navigator.serviceWorker.ready;
            let subscription = await registration.pushManager.getSubscription();
            if (!subscription) {
                subscription = await registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: applicationServerKey(settings.applicationServerKey)});
                newlyCreated = subscription;
            }
            const keys = subscription.toJSON().keys;
            const result = await apiClient<{id: string}>(`${base}/push-subscriptions`, {method: "POST", credentials: "include", body: JSON.stringify({endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})});
            setPushId(result.id);
            try {localStorage.setItem(`gokul-push-device:${settings.scopeId}`, result.id);}
            catch {
                await apiClient<void>(`${base}/push-subscriptions/${result.id}`, {method: "DELETE", credentials: "include"});
                await subscription.unsubscribe(); setPushId(null);
                setMessage("This browser cannot remember the registration. Push was disabled; use your inbox."); return;
            }
            setMessage("This browser is registered. Push delivery depends on browser and device settings; your inbox is the reliable record.");
        } catch {
            if (newlyCreated) await newlyCreated.unsubscribe().catch(() => false);
            setMessage("Browser registration could not be confirmed. Retry when connected, or use the inbox.");
        } finally {setBusy(false);}
    }
    async function disablePush() {
        if (!pushId || !settings) return;
        setBusy(true); setMessage("");
        try {
            // Server first: a failed request must never claim notifications are disabled.
            await apiClient<void>(`${base}/push-subscriptions/${pushId}`, {method: "DELETE", credentials: "include"});
            setPushId(null); localStorage.removeItem(`gokul-push-device:${settings.scopeId}`);
            if ("serviceWorker" in navigator) {
                const registration = await navigator.serviceWorker.getRegistration();
                const subscription = await registration?.pushManager.getSubscription();
                if (subscription) await subscription.unsubscribe().catch(() => false);
            }
            setMessage("Push disabled for this browser. An already accepted browser notification may still arrive; inbox messages remain available.");
        } catch {setMessage("We could not confirm push was disabled. Retry when connected, or revoke notification permission in browser settings.");}
        finally {setBusy(false);}
    }
    return <div className="mt-6 rounded-2xl border border-[#eadfd6] p-4" aria-label="Browser alerts and sound">
        <h3 className="font-semibold">Browser alerts and sound</h3>
        <p className="mt-2 text-sm leading-6">Both are optional. Push uses standard browser notifications. Custom chimes work only in an open, visible page after you activate sound in that tab; muted devices and autoplay rules may prevent sound.</p>
        <p className="mt-2 text-sm leading-6">On iPhone or iPad, add Gokul Sweets to your Home Screen and open that app before enabling push (iOS/iPadOS 16.4 or later). If push is unsupported, use this inbox.</p>
        {settings && draft && <>
            <div className="mt-4 flex flex-wrap gap-3">
                <button type="button" disabled={busy || !supported || !settings.pushConfigured || permission === "denied"} onClick={() => void enablePush()} className="min-h-11 rounded-xl border px-4 text-sm disabled:opacity-50">{pushId ? "Check this browser registration" : "Enable push for this browser"}</button>
                {pushId && <button type="button" disabled={busy} onClick={() => void disablePush()} className="min-h-11 rounded-xl border px-4 text-sm">Disable push for this browser</button>}
            </div>
            {!supported && <p className="mt-2 text-xs">This browser does not support push here. Use the inbox for updates.</p>}
            {!settings.pushConfigured && <p className="mt-2 text-xs">Push is not configured yet. The inbox and optional in-page sound remain available.</p>}
            {permission === "denied" && <p className="mt-2 text-sm">Permission is denied. Change it in browser settings if you want push later.</p>}
            <label className="mt-4 flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={draft.soundEnabled} disabled={busy} onChange={event => setDraft({...draft, soundEnabled: event.target.checked})} />Allow in-page chimes</label>
            <button type="button" className="min-h-11 text-sm underline" onClick={() => void activateChime().then(() => setMessage("Sound activated in this tab. If you heard nothing, check device volume and browser settings.")).catch(() => setMessage("Sound is blocked or unsupported. Your inbox still works."))}>Activate and test sound in this tab</button>
            <label className="mt-3 flex min-h-11 items-center gap-3 text-sm"><input type="checkbox" checked={draft.quietHoursEnabled} disabled={busy} onChange={event => setDraft({...draft, quietHoursEnabled: event.target.checked})} />Quiet hours for push and chimes (IST)</label>
            <div className="mt-2 flex flex-wrap gap-4 text-sm">
                <label>Start (IST)<input type="time" aria-label="Quiet hours start (IST)" className="ml-2 rounded-lg border p-2" value={minuteToTime(draft.quietStartMinute)} disabled={busy || !draft.quietHoursEnabled} onChange={event => setDraft({...draft, quietStartMinute: timeToMinute(event.target.value)})} /></label>
                <label>End (IST)<input type="time" aria-label="Quiet hours end (IST)" className="ml-2 rounded-lg border p-2" value={minuteToTime(draft.quietEndMinute)} disabled={busy || !draft.quietHoursEnabled} onChange={event => setDraft({...draft, quietEndMinute: timeToMinute(event.target.value)})} /></label>
            </div>
            <p className="mt-2 text-xs leading-5">Quiet hours default to 10 pm–8 am IST. Updates stay in the inbox without a later burst of alerts. Save to apply changes across your account.</p>
            <button type="button" disabled={busy} onClick={() => void save()} className="mt-3 min-h-11 rounded-xl border px-4 text-sm font-semibold">Save alert preferences</button>
        </>}
        {message && <p role="status" className="mt-3 text-sm">{message}</p>}
    </div>;
}
