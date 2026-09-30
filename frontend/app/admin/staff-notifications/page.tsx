"use client";

import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {applicationServerKey} from "@/lib/notificationAlerts";
import {formatBusinessTimestamp} from "@/lib/businessTime";
import NotificationIcon from "@/components/customer/NotificationIcon";
import {staffAlertsRequest, type StaffAlertSettings, type StaffInbox} from "@/services/staffAlertsApi";

export default function StaffNotificationsPage() {
    const {profile, hasPermission} = useAdminAuth();
    const [search, setSearch] = useState("");
    const [unreadOnly, setUnreadOnly] = useState(false);
    const query = new URLSearchParams({search, unreadOnly: String(unreadOnly)}).toString();
    const [settings, setSettings] = useState<StaffAlertSettings | null>(null);
    const [inbox, setInbox] = useState<StaffInbox | null>(null);
    const [deviceId, setDeviceId] = useState<string | null>(null);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [permission, setPermission] = useState<NotificationPermission>("default");
    const [supported, setSupported] = useState(false);
    const key = `gokul-staff-push:${ADMIN_API_BASE_URL}:${profile?.staffId}`;
    const load = useCallback(async (signal?: AbortSignal) => {
        let device: string | null = null;
        try {device = localStorage.getItem(key); if (device && !/^[0-9a-f-]{36}$/i.test(device)) device = null;} catch { /* inbox works without storage */ }
        const next = await staffAlertsRequest<StaffAlertSettings>(`/settings${device ? `?deviceId=${encodeURIComponent(device)}` : ""}`, {signal});
        if (signal?.aborted) return;
        setSettings(next); setDeviceId(device); setPermission("Notification" in window ? Notification.permission : "default");
        setSupported(window.isSecureContext && "Notification" in window && "PushManager" in window && "serviceWorker" in navigator);
        if (next.enabled) {
            const result = await staffAlertsRequest<StaffInbox>(`?${query}`, {signal});
            if (!signal?.aborted) {setInbox(result);}
        }
    }, [key, query]);
    useEffect(() => {
        if (!profile || !hasPermission("ORDER_VIEW")) return;
        const controller = new AbortController();
        void Promise.resolve().then(() => load(controller.signal)).catch(() => {if (!controller.signal.aborted) setError("Staff alerts could not load. Use the preparation queue, or refresh when connected.");});
        const timer = window.setInterval(() => {
            if (navigator.onLine && document.visibilityState === "visible") void Promise.resolve().then(() => load(controller.signal)).catch(() => {});
        }, 30000);
        return () => {controller.abort(); window.clearInterval(timer);};
    }, [load, profile, hasPermission]);
    async function perform(action: () => Promise<void>) {
        setBusy(true); setError(""); setMessage("");
        try {await action();} catch (reason) {setError(reason instanceof Error ? reason.message : "Could not confirm this change. Please try again.");}
        finally {setBusy(false);}
    }
    async function enablePush() {
        if (!settings?.applicationServerKey || !supported) return;
        let created: PushSubscription | null = null;
        // Permission request must be invoked directly from the click, before any await.
        const requested = Notification.requestPermission();
        await perform(async () => {
            const choice = await requested; setPermission(choice);
            if (choice !== "granted") {setMessage("Push permission was not granted. Staff alerts remain in this inbox and the preparation queue."); return;}
            try {
                const registration = await navigator.serviceWorker.register("/sw.js"); await navigator.serviceWorker.ready;
                let subscription = await registration.pushManager.getSubscription();
                if (!subscription) {subscription = await registration.pushManager.subscribe({userVisibleOnly: true, applicationServerKey: applicationServerKey(settings.applicationServerKey!)}); created = subscription;}
                const keys = subscription.toJSON().keys;
                const result = await staffAlertsRequest<{id: string}>("/push-subscriptions", {method: "POST", body: JSON.stringify({endpoint: subscription.endpoint, publicKey: keys?.p256dh, authSecret: keys?.auth})});
                setDeviceId(result.id);
                try {localStorage.setItem(key, result.id);} catch {
                    await staffAlertsRequest(`/push-subscriptions/${result.id}`, {method: "DELETE"});
                    setDeviceId(null); throw new Error("This browser cannot remember its registration. Staff push was disabled; use the queue.");
                }
                await load(); setMessage("Staff push is registered for this signed-in browser. Delivery depends on device settings and a live staff session.");
            } catch (reason) {if (created) await created.unsubscribe().catch(() => false); throw reason;}
        });
    }
    if (profile && !hasPermission("ORDER_VIEW")) return <p className="p-6">You need order-view permission to use staff alerts.</p>;
    if (settings && settings.staffId !== profile?.staffId) return <p role="status" className="p-6">Loading your staff alerts…</p>;
    return <main className="mx-auto max-w-4xl p-4 sm:p-8">
        <div className="flex flex-wrap items-center justify-between gap-3"><div><p className="text-xs font-bold uppercase tracking-widest text-[#c88a20]">Your branch operations</p><h1 className="mt-1 text-2xl font-bold text-[#173c39]">Staff notifications</h1></div>
            <Link href="/admin/orders" className="min-h-11 rounded-xl border bg-white px-4 py-3 text-sm font-semibold">Open preparation queue</Link></div>
        <p className="mt-3 text-sm leading-6 text-[#756763]">New paid orders, preparation reminders and overdue work from branches you can access. All times use IST. Unread counts are per order. Successful order actions clear the completed task alerts automatically. Reading an alert does not complete the order action.</p>
        {error && <p role="alert" className="mt-4 rounded-xl border border-[#c76752] bg-white p-4 text-sm">{error}</p>}
        {message && <p role="status" className="mt-4 rounded-xl bg-[#eaf3ec] p-4 text-sm">{message}</p>}
        {!settings && !error && <p role="status" className="mt-5">Loading staff alerts…</p>}
        {settings && !settings.enabled && <p className="mt-5 rounded-2xl border bg-white p-5">Staff alerts are not enabled yet. Use the preparation queue.</p>}
        {settings?.enabled && <>
            <details className="mt-6 rounded-3xl border border-[#eadfd6] bg-white p-5 sm:p-6" aria-label="Staff push settings">
                <summary className="cursor-pointer text-lg font-bold">Notification delivery settings</summary>
                <h2 className="mt-3 font-bold">Get alerts when the portal is closed</h2>
                <p className="mt-2 text-sm leading-6">Register each staff browser separately. Alerts stop after logout or session expiry (staff sign-in lasts up to 8 hours). Browser permission, device mute and OS settings control delivery and sound. On iOS/iPadOS 16.4+, use the installed Home Screen app.</p>
                <p className="mt-2 text-sm">Order references and branch timing may appear on your lock screen.</p>
                {!supported && <p className="mt-3 text-sm">Push is unsupported here. This inbox and the preparation queue remain available.</p>}
                {!settings.pushConfigured && <p className="mt-3 text-sm">Push sender configuration is missing. Contact your administrator; no registration is available yet.</p>}
                {permission === "denied" && <p className="mt-3 text-sm">Permission is denied. Change it in browser settings to enable push later.</p>}
                <div className="mt-4 flex flex-wrap gap-3">
                    <button type="button" disabled={busy || !supported || !settings.pushConfigured || permission === "denied"} onClick={() => void enablePush()} className="min-h-11 rounded-xl bg-[#173c39] px-4 text-sm font-semibold text-white disabled:opacity-50">{settings.deviceActive ? "Check staff registration" : "Enable staff push for this browser"}</button>
                    {deviceId && <button type="button" disabled={busy} onClick={() => void perform(async () => {
                        await staffAlertsRequest(`/push-subscriptions/${deviceId}`, {method: "DELETE"});
                        setDeviceId(null); try {localStorage.removeItem(key);} catch { /* server revocation succeeded */ }
                        await load(); setMessage("Staff push disabled for this browser. An already accepted notification may still arrive.");
                    })} className="min-h-11 rounded-xl border px-4 text-sm">Disable staff push</button>}
                </div>
                <div className="mt-5 rounded-2xl bg-[#fffaf2] p-4 text-sm leading-6">
                    <h3 className="font-semibold">Reminders and email follow-up</h3>
                    <p>Preparation reminders start {settings.reminderMinutes} minutes before the preparation window, followed by an alert when preparation is due. If an order is still waiting or preparing after its booked time, overdue alerts appear.</p>
                    <p className="mt-2">{settings.emailTestRouting ? `DEV testing: unresolved email escalation is routed to the configured QA mailbox after ${settings.escalationMinutes} minutes. This does not verify delivery to each staff member.` : settings.emailConfigured ? `Email escalation is configured for your staff account, ${settings.escalationMinutes} minutes after the booked time if action is still missing.` : "Email escalation is not configured for your staff account. Ask your administrator to configure a verified recipient and email sender."} Reading an alert does not stop escalation; starting preparation or marking ready changes which action is still needed.</p>
                </div>
            </details>
            <section className="mt-6" aria-label="Staff notification inbox">
                <div className="flex flex-wrap items-center justify-between gap-3"><h2 className="text-lg font-bold">Updates {inbox && <span className="text-sm font-normal">· {inbox.unreadCount} unread</span>}</h2>
                    <div className="flex flex-wrap gap-2"><button type="button" disabled={busy || !inbox?.unreadCount} onClick={() => void perform(async () => {
                        const throughId=inbox?.readThrough || Math.max(0,...(inbox?.messages.map(item=>item.event.id) ?? []));
                        await staffAlertsRequest("/read-all",{method:"PUT",body:JSON.stringify({throughId})});
                        await load();window.dispatchEvent(new Event("gokul-staff-inbox-changed"));
                    })} className="min-h-11 rounded-xl border bg-white px-4 text-sm disabled:opacity-50">Mark all read</button>
                    <button type="button" disabled={busy} onClick={() => void perform(() => load())} className="min-h-11 rounded-xl border bg-white px-4 text-sm">Refresh alerts</button></div></div>
                {inbox?.messages.length === 0 && <p className="mt-4 rounded-2xl border bg-white p-5 text-sm">You’re all caught up. New paid orders and preparation reminders will appear here.</p>}
                <div className="mt-4 flex flex-wrap gap-3"><label className="flex-1 text-sm">Search all branch updates<input type="search" disabled={busy} maxLength={100} value={search} onChange={event => setSearch(event.target.value)} placeholder="Order number or task" className="mt-1 min-h-11 w-full rounded-xl border bg-white px-3" /></label><label className="flex items-center gap-2 text-sm"><input type="checkbox" disabled={busy} checked={unreadOnly} onChange={event => setUnreadOnly(event.target.checked)} />Unread only</label></div>
                <ul className="mt-4 space-y-3">{Array.from(new Set(inbox?.messages.map(item => item.event.orderNumber))).sort((a, b) => {
                    const rank = (order: string) => Math.max(...inbox!.messages.filter(item => item.event.orderNumber === order).map(item => item.actionRequired ? item.event.kind.includes("OVERDUE") ? 2 : 1 : 0));
                    return rank(b) - rank(a);
                }).map(order => {
                    const events = inbox!.messages.filter(item => item.event.orderNumber === order);
                    return <li key={order} className="overflow-hidden rounded-2xl border"><ul>{events.slice(0, 1).map(item => <li key={item.event.id} className={`rounded-2xl border bg-white p-5 shadow-sm ${item.actionRequired && item.event.kind.includes("OVERDUE") ? "border-[#c76752]" : "border-[#eadfd6]"}`}>
                    <div className="flex items-start gap-3"><span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-[#fff0dc] text-[#7a1625]"><NotificationIcon kind={item.event.kind} /></span><div className="min-w-0 flex-1"><h3 className="font-bold">{item.event.title}</h3><span className={`mt-2 inline-block rounded-full px-2 py-1 text-[10px] font-bold ${item.actionRequired ? "bg-[#7a1625] text-white" : "bg-[#edf3ee] text-[#173c39]"}`}>{item.actionRequired ? "Action needed" : "Action no longer required"}</span></div></div>
                    <p className="mt-3 break-words text-sm leading-6">{item.event.message}</p>
                    <time dateTime={item.event.createdAt} className="mt-3 block text-xs text-[#756763]">{formatBusinessTimestamp(item.event.createdAt, {day: "numeric", month: "short", hour: "numeric", minute: "2-digit"})} IST · {item.readAt ? "Read" : "Unread"}</time>
                    {item.emailState && <p className="mt-2 text-xs">Email follow-up: {item.emailState === "ACCEPTED" ? "accepted by provider; inbox delivery not confirmed" : item.emailState === "FAILED" ? "could not send; use the queue" : item.emailState === "SKIPPED" ? "skipped after action or configuration changed" : "waiting or retrying"}.</p>}
                    <div className="mt-3 flex flex-wrap gap-4"><Link href={item.event.targetUrl??`/admin/orders/${encodeURIComponent(item.event.orderNumber)}`} className="flex min-h-11 items-center text-sm font-bold text-[#173c39] underline">{item.event.enquiryId?"Open bulk request":"Open order"} {item.event.orderNumber}</Link>
                        {!item.readAt && <button type="button" disabled={busy} onClick={() => void perform(async () => {
                            await staffAlertsRequest(`/${item.event.id}/read`, {method: "PUT"}); await load(); window.dispatchEvent(new Event("gokul-staff-inbox-changed"));
                        })} className="min-h-11 text-sm underline">Mark as read</button>}</div>
                </li>)}</ul>{events.length > 1 && <details className="border-t bg-white px-5 py-3"><summary className="cursor-pointer text-sm font-bold">Order timeline · {events.length - 1} earlier alerts</summary><ol className="mt-3 space-y-3">{events.slice(1).map(item => <li key={item.event.id} className="border-l-2 pl-3"><p className="text-sm font-semibold">{item.event.title} · {item.readAt ? "Read" : "Unread"}</p><p className="text-sm">{item.event.message}</p><p className="text-xs">{item.actionRequired ? "Action needed" : "Action no longer required"}</p>{!item.readAt && <button type="button" disabled={busy} className="min-h-11 text-sm underline" onClick={() => void perform(async () => {await staffAlertsRequest(`/${item.event.id}/read`, {method: "PUT"}); await load();})}>Mark as read</button>}</li>)}</ol></details>}</li>;
                })}</ul>
                {inbox?.nextBefore && <button type="button" disabled={busy} className="mt-4 min-h-11 rounded-xl border bg-white px-4 text-sm" onClick={() => void perform(async () => {
                    const older = await staffAlertsRequest<StaffInbox>(`?${query}&before=${inbox.nextBefore}`);
                    setInbox(current => current ? {...older, messages: [...current.messages, ...older.messages.filter(item => !current.messages.some(existing => existing.event.id === item.event.id))]} : older);
                })}>Load older alerts</button>}
            </section>
        </>}
    </main>;
}
