"use client";

import CustomerNotificationLink from "./CustomerNotificationLink";
import {useEffect, useState} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {ApiError} from "@/services/apiClient";
import {readCustomerInbox} from "@/services/customerInbox";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import NotificationIcon from "@/components/customer/NotificationIcon";

export default function CustomerNotificationBell() {
    const features = useStorefrontFeatures();
    const [count, setCount] = useState<number | null>(null);
    useEffect(() => {
        if (!features?.notificationInbox) return;
        let active = true;
        let paused = false;
        let pending: AbortController | null = null;
        const refresh = async () => {
            if (!active || paused || pending || document.visibilityState !== "visible" || !navigator.onLine) return;
            const controller = new AbortController();
            pending = controller;
            try {
                const inbox = await readCustomerInbox(controller.signal);
                if (active && pending === controller) setCount(inbox.unreadCount);
            } catch (error) {
                if (active && pending === controller) {
                    setCount(null);
                    if (error instanceof ApiError && [401, 403, 404].includes(error.status)) paused = true;
                }
            } finally {if (pending === controller) pending = null;}
        };
        void refresh();
        const timer = window.setInterval(() => void refresh(), 30000);
        const identityChanged = () => {
            pending?.abort(); pending = null; paused = false; setCount(null); void refresh();
        };
        const stopIdentity = subscribeCustomerIdentityChanges(identityChanged, {revalidateOnResume: false});
        const inboxChanged = () => {pending?.abort(); pending = null; void refresh();};
        window.addEventListener("gokul-inbox-changed", inboxChanged);
        const resume = () => {if (document.visibilityState === "visible") {paused = false; void refresh();}};
        const pageshow = (event: PageTransitionEvent) => {if (event.persisted) resume();};
        window.addEventListener("focus", resume);
        window.addEventListener("pageshow", pageshow);
        window.addEventListener("online", resume);
        document.addEventListener("visibilitychange", resume);
        return () => {active = false; pending?.abort(); window.clearInterval(timer);
            stopIdentity();
            window.removeEventListener("gokul-inbox-changed", inboxChanged);
            window.removeEventListener("focus", resume);
            window.removeEventListener("pageshow", pageshow);
            window.removeEventListener("online", resume); document.removeEventListener("visibilitychange", resume);};
    }, [features?.notificationInbox]);
    if (!features?.notificationInbox || count === null) return null;
    return <CustomerNotificationLink label={`Notifications, ${count} unread`}
        className="customer-notification-bell relative flex h-11 w-11 shrink-0 items-center justify-center rounded-full border border-[#eadfd6] bg-white text-[#7a1625] shadow-sm transition-colors hover:bg-[#fff0dc] focus-visible:outline-2 focus-visible:outline-offset-2">
        <NotificationIcon />
        {count > 0 && <span className="absolute -right-1 -top-1 min-w-5 rounded-full bg-[#7a1625] px-1 text-center text-[10px] font-bold leading-5 text-white" aria-hidden="true">{count > 9 ? "9+" : count}</span>}
    </CustomerNotificationLink>;
}
