"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import NotificationIcon from "@/components/customer/NotificationIcon";
import {STAFF_ALERT_DEVICE_RESTORED, staffAlertDeviceKey, staffAlertsRequest, type StaffAlertSettings, type StaffInbox} from "@/services/staffAlertsApi";

export default function StaffNotificationBell() {
    const {profile, hasPermission} = useAdminAuth();
    const [count, setCount] = useState<number | null>(null);
    useEffect(() => {
        if (!profile || !hasPermission("ORDER_VIEW")) return;
        let active = true, busy = false, checkedDevice = false;
        const deviceKey = staffAlertDeviceKey(profile.staffId);
        const controller = new AbortController();
        const refresh = async () => {
            if (busy || !navigator.onLine || document.visibilityState !== "visible") return;
            busy = true;
            try {
                const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(8000)]);
                let device: string | null = null;
                if (!checkedDevice && typeof Notification !== "undefined" && Notification.permission === "granted") {
                    try {device = localStorage.getItem(deviceKey);} catch { /* Inbox works without device storage. */ }
                    if (device && !/^[0-9a-f-]{36}$/i.test(device)) device = null;
                }
                const settings = await staffAlertsRequest<StaffAlertSettings>(`/settings${device ? `?deviceId=${encodeURIComponent(device)}` : ""}`, {signal});
                if (!settings.enabled) {if (active) setCount(null); return;}
                if (device && !settings.deviceActive && settings.pushConfigured) {
                    try {
                        const registration = await navigator.serviceWorker?.getRegistration();
                        const push = await registration?.pushManager.getSubscription();
                        signal.throwIfAborted();
                        if (push && localStorage.getItem(deviceKey) === device) {
                            const keys = push.toJSON().keys;
                            await staffAlertsRequest("/push-subscriptions", {
                                method: "POST", signal,
                                body: JSON.stringify({registrationId: device, endpoint: push.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})
                            });
                            if (active) window.dispatchEvent(new Event(STAFF_ALERT_DEVICE_RESTORED));
                        }
                    } catch { /* Device setup stays available in Staff notifications. */ }
                }
                checkedDevice = true;
                const inbox = await staffAlertsRequest<StaffInbox>("", {signal});
                if (active) setCount(inbox.unreadCount);
            } catch {if (active) setCount(null);}
            finally {busy = false;}
        };
        const pushed = (event: MessageEvent) => {if (event.data?.type === "gokul-staff-alert") void refresh();};
        navigator.serviceWorker?.addEventListener("message", pushed);
        void refresh(); const timer = window.setInterval(() => void refresh(), 30000);
        window.addEventListener("gokul-staff-inbox-changed", refresh);
        window.addEventListener("online", refresh); document.addEventListener("visibilitychange", refresh);
        return () => {active = false; controller.abort(); window.clearInterval(timer);
            window.removeEventListener("gokul-staff-inbox-changed", refresh); window.removeEventListener("online", refresh);
            document.removeEventListener("visibilitychange", refresh);
            navigator.serviceWorker?.removeEventListener("message", pushed);};
    }, [profile, hasPermission]);
    if (!profile || !hasPermission("ORDER_VIEW") || count === null) return null;
    return <Link href="/admin/staff-notifications" aria-label={`Staff alerts, ${count} unread`}
        className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[#eadfd6] bg-white text-[#7a1625] shadow-sm hover:bg-[#fff0dc] focus-visible:outline-2 focus-visible:outline-offset-2">
        <NotificationIcon />{count > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-[#7a1625] px-1 text-center text-[10px] font-bold leading-5 text-white" aria-hidden="true">{count > 9 ? "9+" : count}</span>}
    </Link>;
}
