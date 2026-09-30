"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {apiClient} from "@/services/apiClient";
import NotificationIcon from "@/components/customer/NotificationIcon";

export default function CustomerNotificationBell() {
    const features = useStorefrontFeatures();
    const [count, setCount] = useState<number | null>(null);
    useEffect(() => {
        if (!features?.notificationInbox) return;
        let active = true;
        let busy = false;
        const controller = new AbortController();
        const refresh = async () => {
            if (busy || document.visibilityState !== "visible" || !navigator.onLine) return;
            busy = true;
            try {
                const me = await apiClient<{authenticated: boolean}>("/api/customer/identity/me", {credentials: "include", signal: controller.signal});
                if (!me.authenticated) {if (active) setCount(null); return;}
                const inbox = await apiClient<{unreadCount: number}>("/api/customer/identity/notifications", {credentials: "include", signal: controller.signal});
                if (active) setCount(inbox.unreadCount);
            } catch {if (active) setCount(null);}
            finally {busy = false;}
        };
        void refresh();
        const timer = window.setInterval(() => void refresh(), 30000);
        window.addEventListener("gokul-customer-identity-changed", refresh);
        window.addEventListener("gokul-inbox-changed", refresh);
        window.addEventListener("online", refresh);
        document.addEventListener("visibilitychange", refresh);
        return () => {active = false; controller.abort(); window.clearInterval(timer);
            window.removeEventListener("gokul-customer-identity-changed", refresh);
            window.removeEventListener("gokul-inbox-changed", refresh);
            window.removeEventListener("online", refresh); document.removeEventListener("visibilitychange", refresh);};
    }, [features?.notificationInbox]);
    if (!features?.notificationInbox || count === null) return null;
    return <Link href="/profile#account-notifications" aria-label={`Notifications, ${count} unread`}
        className="relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[#eadfd6] bg-white text-[#7a1625] shadow-sm transition-colors hover:bg-[#fff0dc] focus-visible:outline-2 focus-visible:outline-offset-2">
        <NotificationIcon />
        {count > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-[#7a1625] px-1 text-center text-[10px] font-bold leading-5 text-white" aria-hidden="true">{count > 9 ? "9+" : count}</span>}
    </Link>;
}
