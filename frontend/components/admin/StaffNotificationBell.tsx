"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import NotificationIcon from "@/components/customer/NotificationIcon";
import {staffAlertsRequest, type StaffAlertSettings, type StaffInbox} from "@/services/staffAlertsApi";

export default function StaffNotificationBell() {
    const {profile, hasPermission} = useAdminAuth();
    const [count, setCount] = useState<number | null>(null);
    useEffect(() => {
        if (!profile || !hasPermission("ORDER_VIEW")) return;
        let active = true, busy = false;
        const controller = new AbortController();
        const refresh = async () => {
            if (busy || !navigator.onLine || document.visibilityState !== "visible") return;
            busy = true;
            try {
                const settings = await staffAlertsRequest<StaffAlertSettings>("/settings", {signal: controller.signal});
                if (!settings.enabled) {if (active) setCount(null); return;}
                const inbox = await staffAlertsRequest<StaffInbox>("", {signal: controller.signal});
                if (active) setCount(inbox.unreadCount);
            } catch {if (active) setCount(null);}
            finally {busy = false;}
        };
        void refresh(); const timer = window.setInterval(() => void refresh(), 30000);
        window.addEventListener("gokul-staff-inbox-changed", refresh);
        window.addEventListener("online", refresh); document.addEventListener("visibilitychange", refresh);
        return () => {active = false; controller.abort(); window.clearInterval(timer);
            window.removeEventListener("gokul-staff-inbox-changed", refresh); window.removeEventListener("online", refresh);
            document.removeEventListener("visibilitychange", refresh);};
    }, [profile, hasPermission]);
    if (!profile || !hasPermission("ORDER_VIEW") || count === null) return null;
    return <Link href="/admin/staff-notifications" aria-label={`Staff alerts, ${count} unread`}
        className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[#eadfd6] bg-white text-[#7a1625] shadow-sm hover:bg-[#fff0dc] focus-visible:outline-2 focus-visible:outline-offset-2">
        <NotificationIcon />{count > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-[#7a1625] px-1 text-center text-[10px] font-bold leading-5 text-white" aria-hidden="true">{count > 9 ? "9+" : count}</span>}
    </Link>;
}
