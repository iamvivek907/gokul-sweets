"use client";

import Link from "next/link";
import {useEffect, useRef, useState} from "react";
import {usePathname} from "next/navigation";
import {readOrderPushBinding, switchOrderPushOwner} from "@/lib/orderPushBinding";
import {fetchAdminProfile} from "@/services/adminApi";
import {usePwaInstall} from "@/hooks/usePwaInstall";
import {T} from "@/lib/language";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import NotificationIcon from "@/components/customer/NotificationIcon";
import {STAFF_ALERT_DEVICE_RESTORED, confirmStaffAlertDevice, dismissStaffAlertSetup, enableStaffDeviceAlerts, readStaffAlertDevice, recoverStaffAlertSetup, resolveConfirmedStaffAlertDevice, staffAlertPromptKey, staffAlertsRequest, supportsStaffPush, syncStaffAlertDevice, StaffAlertRequestError, type StaffAlertSettings, type StaffInbox} from "@/services/staffAlertsApi";

export default function StaffNotificationBell() {
    const {profile, hasPermission, verificationFailed} = useAdminAuth();
    const {isStandalone} = usePwaInstall();
    const pathname = usePathname();
    const [needsRepair, setNeedsRepair] = useState(false);
    const [setup, setSetup] = useState<StaffAlertSettings | null>(null);
    const [dismissed, setDismissed] = useState(false);
    const [permission, setPermission] = useState<NotificationPermission>("default");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const requestScope = useRef<AbortController | null>(null);
    const [count, setCount] = useState<number | null>(null);
    useEffect(() => {
        if (!profile) return;
        const canViewOrders = hasPermission("ORDER_VIEW");
        let active = true, busy = false, localHandoffChecked = false, serverHandoffChecked = false;
        let retryAt = 0, failures = 0;
        const controller = new AbortController();
        requestScope.current = controller;
        const refresh = async () => {
            if (busy || !navigator.onLine || document.visibilityState !== "visible") return;
            busy = true;
            try {
                const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]);
                const staffId = profile.staffId;
                const owner = String(staffId);
                const binding = await readOrderPushBinding("staff");
                const previousOwner = binding?.owner ?? null;
                const staged = previousOwner === owner && !!binding?.setupId;
                const stored = await resolveConfirmedStaffAlertDevice(staffId);
                const permitted = supportsStaffPush() && Notification.permission === "granted";
                let device = permitted ? stored : null;
                if (!canViewOrders) {
                    if ((!serverHandoffChecked || !localHandoffChecked) && permitted && Date.now() >= retryAt) {
                        try {
                            if (!serverHandoffChecked) {
                                const worker = await navigator.serviceWorker.getRegistration();
                                const subscription = await worker?.pushManager.getSubscription();
                                if (!subscription) return;
                                await syncStaffAlertDevice(subscription, staffId, signal);
                                serverHandoffChecked = true;
                            } else if ((await fetchAdminProfile(signal))?.staffId !== staffId) {
                                return;
                            }
                            localHandoffChecked = await switchOrderPushOwner("staff", owner, signal, false, previousOwner);
                            failures = 0;
                        } catch {
                            retryAt = Date.now() + Math.min(120_000, 15_000 * 2 ** Math.min(failures++, 3));
                        }
                    }
                    return;
                }
                const settings = await staffAlertsRequest<StaffAlertSettings>(`/settings${device ? `?deviceId=${encodeURIComponent(device)}` : ""}`, {signal});
                if (!active || settings.staffId !== staffId) return;
                if (!localHandoffChecked) {
                    localHandoffChecked = await switchOrderPushOwner("staff", owner, signal, true, previousOwner).catch(() => false);
                }
                if (!settings.enabled) {setCount(null); setSetup(null); setNeedsRepair(false); return;}

                let deviceActive = settings.deviceActive;
                let restored = false;
                if (settings.pushConfigured && permitted && (!serverHandoffChecked || staged || (device && !deviceActive)) && Date.now() >= retryAt) {
                    try {
                        const registration = await navigator.serviceWorker.getRegistration();
                        const push = await registration?.pushManager.getSubscription();
                        signal.throwIfAborted();
                        if (push && await resolveConfirmedStaffAlertDevice(staffId) === stored) {
                            // Pause an earlier owner's verified endpoint before reusing this browser.
                            if (!serverHandoffChecked) await syncStaffAlertDevice(push, staffId, signal);
                            serverHandoffChecked = true;
                            const recovered = staged ? await recoverStaffAlertSetup(push, staffId, signal) : null;
                            if (recovered) {
                                device = recovered;
                                deviceActive = true;
                                restored = true;
                            } else if (device && !deviceActive) {
                                await syncStaffAlertDevice(push, staffId, signal, device);
                                deviceActive = true;
                                restored = true;
                            }
                        } else if (!device) {
                            serverHandoffChecked = true;
                        } else {
                            retryAt = Date.now() + 60_000;
                        }
                        failures = 0;
                    } catch (cause) {
                        const permanentlyInactive = cause instanceof StaffAlertRequestError && [404, 409].includes(cause.status);
                        retryAt = permanentlyInactive ? Infinity : Date.now() + Math.min(120_000, 15_000 * 2 ** Math.min(failures++, 3));
                    }
                }
                if (deviceActive && device) {
                    deviceActive = await confirmStaffAlertDevice(staffId, device, signal);
                    if (!deviceActive) localHandoffChecked = false;
                    if (deviceActive && restored && active) window.dispatchEvent(new Event(STAFF_ALERT_DEVICE_RESTORED));
                }
                if (active && isStandalone && supportsStaffPush()) {
                    let seen = false;
                    try {seen = localStorage.getItem(staffAlertPromptKey(staffId)) === "1";} catch { /* Allow the optional offer. */ }
                    const repair = settings.pushConfigured && Boolean(device) && !deviceActive;
                    setNeedsRepair(repair);
                    setSetup(settings.pushConfigured && settings.applicationServerKey &&
                        ((repair && Notification.permission !== "denied") || (!stored && !deviceActive && !seen && Notification.permission !== "denied"))
                        ? settings : null);
                }
                const inbox = await staffAlertsRequest<StaffInbox>("", {signal});
                if (active) setCount(inbox.unreadCount);
            } catch {if (active) setCount(null);}
            finally {busy = false;}
        };
        const pushed = (event: MessageEvent) => {if (event.data?.type === "gokul-staff-alert") void refresh();};
        navigator.serviceWorker?.addEventListener("message", pushed);
        void refresh(); const timer = window.setInterval(() => void refresh(), 30000);
        window.addEventListener("gokul-staff-inbox-changed", refresh);
        const deviceChanged = () => {
            if (!active) return;
            retryAt = 0;
            void resolveConfirmedStaffAlertDevice(profile.staffId).then(id => {
                if (active && id) {setSetup(null); setNeedsRepair(false);}
            });
        };
        window.addEventListener(STAFF_ALERT_DEVICE_RESTORED, deviceChanged);
        window.addEventListener("online", refresh); document.addEventListener("visibilitychange", refresh);
        return () => {active = false; controller.abort(); window.clearInterval(timer);
            window.removeEventListener("gokul-staff-inbox-changed", refresh); window.removeEventListener("online", refresh);
            document.removeEventListener("visibilitychange", refresh);
            window.removeEventListener(STAFF_ALERT_DEVICE_RESTORED, deviceChanged);
            navigator.serviceWorker?.removeEventListener("message", pushed);};
    }, [profile, hasPermission, isStandalone]);
    async function enable() {
        const signal = requestScope.current?.signal;
        if (!setup || busy || !signal || signal.aborted) return;
        setBusy(true);
        setError("");
        try {
            const result = await enableStaffDeviceAlerts(setup, signal);
            if (signal.aborted) return;
            setPermission(result);
            if (result === "granted") setDismissed(true);
            else if (result === "default") setError("Tap Enable phone alerts and choose Allow when your device asks.");
        } catch {
            if (!signal.aborted) setError("Couldn’t finish setting up phone alerts. Try again when connected.");
        } finally {
            if (!signal.aborted) setBusy(false);
        }
    }

    if (!profile || !hasPermission("ORDER_VIEW")) return null;
    const offer = isStandalone && !verificationFailed && (!dismissed || needsRepair) && setup?.staffId === profile.staffId && pathname !== "/admin/staff-notifications";
    return <>
        {count !== null && <Link href="/admin/staff-notifications" aria-label={`Staff alerts, ${count} unread`}
            className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[#eadfd6] bg-white text-[#7a1625] shadow-sm hover:bg-[#fff0dc] focus-visible:outline-2 focus-visible:outline-offset-2">
            <NotificationIcon />{count > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-[#7a1625] px-1 text-center text-[10px] font-bold leading-5 text-white" aria-hidden="true">{count > 9 ? "9+" : count}</span>}
        </Link>}
        {offer && <section aria-labelledby="staff-phone-alert-title" className="fixed inset-x-4 top-18 z-40 max-w-sm rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4 text-sm text-[#241715] shadow-xl sm:left-auto sm:right-6">
            <h2 id="staff-phone-alert-title" className="font-semibold"><T text={needsRepair ? "Reconnect phone alerts" : "Get order alerts on this phone"}/></h2>
            <p className="mt-1"><T text={needsRepair ? "This phone’s kitchen alerts are inactive. Reconnect to resume order reminders." : "Hear when a paid order arrives or preparation is due, even with the app closed."}/></p>
            {permission === "denied" ? <p className="mt-2" role="status"><T text="Notifications are off. Allow notifications and sound in your phone’s settings, then open Staff notifications to finish setup."/></p>
                : <button type="button" disabled={busy} onClick={() => void enable()} className="mt-3 min-h-11 rounded-xl bg-[#173c39] px-4 font-semibold text-white disabled:opacity-60"><T text={busy ? "Enabling…" : needsRepair ? "Reconnect alerts" : "Enable phone alerts"}/></button>}
            {needsRepair ? <Link href="/admin/staff-notifications" className="ml-3 inline-flex min-h-11 items-center px-2 underline"><T text="Staff notifications"/></Link>
                : <button type="button" disabled={busy} onClick={() => {dismissStaffAlertSetup(profile.staffId); setDismissed(true);}} className="ml-3 min-h-11 px-2 underline"><T text="Not now"/></button>}
            {error && <p className="mt-2" role="status"><T text={error}/></p>}
        </section>}
    </>;
}
