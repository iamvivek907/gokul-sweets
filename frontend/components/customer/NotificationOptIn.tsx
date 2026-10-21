"use client";
import Link from "next/link";
import {useEffect, useState} from "react";
import {usePathname} from "next/navigation";
import {usePwaInstall} from "@/hooks/usePwaInstall";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import {PUSH_REGISTRATION_CHANGED, pushDeviceKey, pushSubscriptionFingerprint, registerCustomerPush} from "@/lib/customerPushRegistration";
import {type AlertSettings} from "@/lib/notificationAlerts";
import {apiClient} from "@/services/apiClient";

// A visit spans route changes. A fresh document or five minutes away starts another visit.
let dismissed = false;
let hiddenAt = 0;
export default function NotificationOptIn() {
    const path = usePathname(), features = useStorefrontFeatures(), pwa = usePwaInstall();
    const [settings, setSettings] = useState<AlertSettings | null>(null);
    const [visible, setVisible] = useState(false), [guest, setGuest] = useState(false);
    const [permission, setPermission] = useState<NotificationPermission>("default");
    const [busy, setBusy] = useState(false), [message, setMessage] = useState("");
    const [revision, setRevision] = useState(0);
    const allowed = Boolean(features?.notificationAlerts && features?.notificationInbox &&
        (features?.futuristicStorefrontV2 || features?.checkoutExperienceV2) && pwa.isStandalone &&
        !/^\/(cart|checkout|orders|admin|staff)(\/|$)/.test(path));
    useEffect(() => {
        const resume = () => {
            if (document.visibilityState === "hidden") {hiddenAt = Date.now(); return;}
            if (hiddenAt && Date.now() - hiddenAt >= 300_000) dismissed = false;
            hiddenAt = 0; setRevision(value => value + 1);
        };
        document.addEventListener("visibilitychange", resume);
        const stop = subscribeCustomerIdentityChanges(() => {setVisible(false); setSettings(null); setRevision(value => value + 1);}, {revalidateOnResume: false});
        const changed = (event: Event) => {
            if ((event as CustomEvent<{enabled?: boolean}>).detail?.enabled === false) dismissed = true;
            setVisible(false); setRevision(value => value + 1);
        };
        window.addEventListener(PUSH_REGISTRATION_CHANGED, changed);
        return () => {stop(); document.removeEventListener("visibilitychange", resume); window.removeEventListener(PUSH_REGISTRATION_CHANGED, changed);};
    }, []);
    useEffect(() => {
        if (!allowed || dismissed || !window.isSecureContext || !("Notification" in window) || !("PushManager" in window) || !("serviceWorker" in navigator)) return;
        let active = true;
        const controller = new AbortController();
        const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]);
        const timer = setTimeout(() => {void (async () => {
            try {
                const me = await apiClient<{authenticated: boolean}>("/api/customer/identity/me", {credentials: "include", signal});
                if (!active) return;
                setGuest(!me.authenticated); setPermission(Notification.permission);
                if (!me.authenticated) {setVisible(true); return;}
                const value = await apiClient<AlertSettings>("/api/customer/identity/notification-alerts", {credentials: "include", signal});
                if (!active || !value.pushConfigured || !value.applicationServerKey) return;
                const registration = await navigator.serviceWorker.getRegistration();
                const subscription = await registration?.pushManager.getSubscription();
                let remembered: string | null = null;
                try {remembered = localStorage.getItem(pushDeviceKey(value.scopeId));} catch { /* Allow an explicit registration attempt. */ }
                if (Notification.permission === "granted" && subscription && remembered) {
                    try {
                        const status = await apiClient<{enabled: boolean; fingerprint: string | null}>(`/api/customer/identity/push-subscriptions/${encodeURIComponent(remembered)}`, {credentials: "include", signal});
                        if (status.enabled && status.fingerprint === await pushSubscriptionFingerprint(subscription)) {if (active) setVisible(false); return;}
                    } catch { /* Show Retry rather than claim a server registration succeeded. */ }
                }
                if (active) {setSettings(value); setVisible(true);}
            } catch { /* Optional prompts never block ordering or claim an unconfirmed registration. */ }
        })();}, 1200);
        return () => {active = false; clearTimeout(timer); controller.abort();};
    }, [allowed, path, revision]);
    async function enable() {
        if (!settings || busy) return;
        if (Notification.permission === "denied") {
            setPermission("denied");
            setMessage("Open your phone’s Settings → Apps → Gokul Sweets → Notifications and allow them. If Gokul is not listed, check your browser’s site notification settings. Then return here.");
            return;
        }
        setBusy(true); setMessage("");
        try {
            const result = await registerCustomerPush(settings);
            setPermission(result);
            if (result === "granted") {setVisible(false); return;}
            setMessage(result === "denied" ? "Notifications are blocked in your device or browser settings. Tap Show me how for help." : "No problem. Tap Enable notifications whenever you’re ready, then choose Allow in the browser prompt.");
        } catch {setPermission(Notification.permission); setMessage("We couldn’t finish enabling notifications. Check your connection and try again.");}
        finally {setBusy(false);}
    }
    if (!allowed || !visible || dismissed) return null;
    return <section className="notification-opt-in permission-surface" aria-label="Enable app notifications">
        <div className="permission-hero"><span className="permission-symbol" aria-hidden="true"><svg width="25" height="25" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4M12 2V1" /></svg></span><div><p className="permission-eyebrow">A little heads-up</p><h2>Your order’s ready. Your phone should know.</h2></div></div>
        <p>Get pickup updates without keeping the app open. You’re always in control.</p>
        <div className="permission-preview" aria-label="Example notification"><span className="permission-app-mark" aria-hidden="true">G</span><div><small>GOKUL SWEETS · EXAMPLE</small><strong>Good food. Zero guesswork.</strong><p>Your order is ready for pickup. See you soon!</p></div></div>
        <div className="permission-actions">
            {guest ? <Link href="/profile">Sign in to enable notifications</Link> : <button type="button" disabled={busy} onClick={() => void enable()}>{busy ? "Enabling…" : permission === "denied" ? "Show me how" : "Enable notifications"}</button>}
            <button type="button" className="permission-secondary" disabled={busy} onClick={() => {dismissed = true; setVisible(false);}}>Not now</button>
        </div>
        <small>Optional. Offer alerts have their own choice in <Link href="/profile/privacy">Privacy and data</Link>. We’ll remind you on your next visit if you skip.</small>
        {message && <p role="status" className="permission-feedback">{message}</p>}
    </section>;
}
