"use client";
import BrandLoading from "@/components/common/BrandLoading";
import {T} from "@/lib/language";


import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import NotificationIcon from "@/components/customer/NotificationIcon";
import CustomerAlertPreferences from "@/components/customer/CustomerAlertPreferences";
import {apiClient, ApiError} from "@/services/apiClient";
import {formatBusinessTimestamp} from "@/lib/businessTime";

function inboxRequest<T>(path: string, options: Parameters<typeof apiClient<T>>[1] = {}): Promise<T> {
    const deadline = AbortSignal.timeout(8000);
    return apiClient<T>(path, {...options, signal: options.signal ? AbortSignal.any([options.signal, deadline]) : deadline});
}

const base = "/api/customer/identity";
type Message = {id: number; eventKey: string; kind: string; targetType: "ORDER" | "OCCASION";
    targetId: string; title: string; message: string; deliveryState: string; createdAt: string; readAt: string | null};
type Inbox = {messages: Message[]; unreadCount: number; nextBefore: number | null; readThrough?: number};
type Preferences = {offerInboxEnabled: boolean; marketingConsentGranted: boolean};

export default function CustomerNotificationInbox() {
    const [search, setSearch] = useState("");
    const [unreadOnly, setUnreadOnly] = useState(false);
    const query = new URLSearchParams({search, unreadOnly: String(unreadOnly)}).toString();
    const [inbox, setInbox] = useState<Inbox | null>(null);
    const [preferences, setPreferences] = useState<Preferences | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState("");
    const [unavailable, setUnavailable] = useState(false);
    const load = useCallback(async (signal?: AbortSignal) => {
        const [next, choices] = await Promise.all([
            inboxRequest<Inbox>(`${base}/notifications?${query}`, {credentials: "include", signal}),
            inboxRequest<Preferences>(`${base}/notification-preferences`, {credentials: "include", signal})
        ]);
        setInbox(next); setPreferences(choices); setError(""); window.dispatchEvent(new Event("gokul-inbox-changed"));
    }, [query]);
    useEffect(() => {
        const controller = new AbortController();
        void Promise.all([
            inboxRequest<Inbox>(`${base}/notifications?${query}`, {credentials: "include", signal: controller.signal}),
            inboxRequest<Preferences>(`${base}/notification-preferences`, {credentials: "include", signal: controller.signal})
        ]).then(([next, choices]) => {
            if (!controller.signal.aborted) {setInbox(next); setPreferences(choices); setError("");}
        }).catch(reason => {
            if (controller.signal.aborted) return;
            if (reason instanceof ApiError && reason.status === 404) setUnavailable(true);
            else setError("Your inbox could not load. Check Order history for current updates, or refresh when connected.");
        });
        return () => controller.abort();
    }, [query]);
    async function perform(action: () => Promise<void>) {
        setBusy(true); setError(""); setSaved("");
        try {await action();}
        catch {setError("We could not confirm that change. Please refresh or try again when connected.");}
        finally {setBusy(false);}
    }
    async function acknowledgeTarget(item: Message) {
        setError("");
        try {
            await inboxRequest<void>(`${base}/notifications/read-target`, {method: "PUT", credentials: "include", keepalive: true,
                body: JSON.stringify({targetType: item.targetType, targetId: item.targetId, throughId: item.id})});
            await load();
        } catch {setError("Your order can still be opened. We could not confirm the read acknowledgement; refresh the inbox when connected.");}
    }
    const grouped = Array.from(new Set(inbox?.messages.map(item => `${item.targetType}:${item.targetId}`)))
        .map(target => ({target, events: inbox!.messages.filter(item => `${item.targetType}:${item.targetId}` === target)
            .sort((a, b) => b.id - a.id)}));
    if (unavailable) return <section className="rounded-3xl border border-[#eadfd6] bg-white p-6">
        <h2 className="text-xl font-semibold"><T text="Notification inbox" /></h2><p className="mt-2 text-sm">The inbox is not available yet. Check Order history for current updates.</p></section>;
    if (!inbox && !error) return <BrandLoading className="customer-inbox-loading" label="Loading your inbox…" />;
    return <section className="customer-notification-inbox rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8" aria-label="Notification inbox">
        <div className="notification-toolbar flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-semibold"><span className="sr-only"><T text="Notification inbox" /></span>{" "}{inbox && <span className="text-sm font-normal">{inbox.unreadCount} unread</span>}</h2>
            <div className="notification-toolbar-actions flex flex-wrap gap-2"><button type="button" disabled={busy || !inbox?.unreadCount} onClick={() => void perform(async () => {
                const throughId = inbox?.readThrough || Math.max(0, ...(inbox?.messages.map(item => item.id) ?? []));
                await inboxRequest<void>(`${base}/notifications/read-all`, {method: "PUT", credentials: "include", body: JSON.stringify({throughId})});
                await load();
            })} className="min-h-11 rounded-xl border px-4 text-sm font-semibold disabled:opacity-50">Mark all read</button>
            <details className="notification-tools"><summary className="min-h-11 cursor-pointer rounded-xl border px-4 py-3 text-sm font-semibold">More options</summary><button type="button" disabled={busy} onClick={() => void perform(() => load())}
                className="min-h-11 rounded-xl border px-4 text-sm font-semibold disabled:opacity-50">Refresh inbox</button>
            <p className="notification-help mt-2 text-sm leading-6 text-[#756763]">Opening an order marks its existing updates read. Completed orders clear automatically. Routine unread updates expire after seven days; payment and refund exceptions remain. Times are in IST.</p></details></div>
        </div>
        {error && <p role="alert" className="mt-4 rounded-xl border border-[#c76752] p-3 text-sm">{error} <button type="button" disabled={busy} onClick={()=>void perform(()=>load())} className="min-h-11 px-3 font-semibold underline"><T text="Try again"/></button></p>}
        {!inbox && !error && <p role="status" className="mt-4">Loading your inbox…</p>}
        {inbox?.messages.length === 0 && <p className="mt-5 rounded-xl bg-[#fffaf2] p-4 text-sm">{search || unreadOnly ? "No updates match this view. Try All updates or another search." : "You’re all caught up. Your order updates will appear here."}</p>}
        <div className="notification-filters mt-5 space-y-3">
            <div className="notification-view-switch flex gap-2" aria-label="Filter notifications">
                <button type="button" disabled={busy} aria-pressed={!unreadOnly} onClick={() => setUnreadOnly(false)} className="min-h-11 flex-1 rounded-xl border px-3 text-sm font-semibold">All updates</button>
                <button type="button" disabled={busy} aria-pressed={unreadOnly} onClick={() => setUnreadOnly(true)} className="min-h-11 flex-1 rounded-xl border px-3 text-sm font-semibold">Unread</button>
            </div>
            <label className="block text-sm"><span className="sr-only">Search all updates</span><input type="search" disabled={busy} maxLength={100} value={search} onChange={event => setSearch(event.target.value)} placeholder="Search order or update" className="min-h-11 w-full rounded-xl border px-3" /></label>
        </div>
        <div className="notification-timeline mt-5 space-y-6">
            {[true, false].map(unread => {
                const groups = grouped.filter(group => group.events.some(item => !item.readAt) === unread);
                if (!groups.length) return null;
                return <section key={String(unread)} aria-label={unread ? "Unread updates" : "Read updates"}>
                    <h3 className="notification-section-heading mb-3 text-sm font-semibold">{unread ? "Unread" : "Read"} <span>· {groups.length}</span></h3>
                    <ul className="space-y-3">{groups.map(({target, events}) => {
                        const item = events[0];
                        const unreadCount = events.filter(event => !event.readAt).length;
                        const tone = /FAIL|CANCEL|REFUND|CHANGED/.test(item.kind) ? "attention" : /PAID|CONFIRMED|READY|PICKED_UP|DELIVERED/.test(item.kind) ? "success" : "update";
                        return <li key={target} className="notification-group overflow-hidden rounded-2xl border border-[#eadfd6]" data-notification-tone={tone} data-unread={unreadCount > 0}>
                            <article className="notification-card p-4 sm:p-5">
                                <div className="flex items-start gap-3">
                                    <span className="notification-kind-icon flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-[#f5e9dd] text-[#7a1625]"><NotificationIcon kind={item.kind} /></span>
                                    <div className="min-w-0 flex-1">
                                        <p className="notification-kind-label text-xs text-[#756763]">{item.targetType === "ORDER" ? "Order update" : "Bulk order update"}</p>
                                        <h4 className="notification-title font-semibold">{item.title}</h4>
                                    </div>
                                    {unreadCount > 0 && <span className="notification-unread-dot h-2 w-2 shrink-0 rounded-full bg-[#2359ac]" role="img" aria-label={`${unreadCount} unread update${unreadCount === 1 ? "" : "s"}`} />}
                                </div>
                                <p className="notification-message mt-2 text-sm leading-6">{item.message}</p>
                                <time dateTime={item.createdAt} className="mt-2 block text-xs text-[#756763]">{formatBusinessTimestamp(item.createdAt, {day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit"})} IST</time>
                                <div className="notification-actions mt-3 flex flex-wrap items-center gap-3">
                                    <Link onClick={() => void acknowledgeTarget(item)} className="flex min-h-11 items-center text-sm font-semibold underline" href={item.targetType === "ORDER" ? `/orders/${encodeURIComponent(item.targetId)}` : `/occasions/requests?enquiry=${encodeURIComponent(item.targetId)}`}>
                                        {item.targetType === "ORDER" ? "View order" : "View bulk request"}</Link>
                                    {["PICKED_UP", "DELIVERED"].includes(item.kind) && <Link href={`/orders/${encodeURIComponent(item.targetId)}#order-review`} onClick={() => void acknowledgeTarget(item)} className="notification-review-action flex min-h-11 items-center rounded-full bg-[#143936] px-4 text-sm font-semibold text-white">Share an optional review</Link>}
                                    {!item.readAt && <button type="button" disabled={busy} className="min-h-11 text-sm underline disabled:opacity-50" onClick={() => void perform(async () => {
                                        await inboxRequest<void>(`${base}/notifications/${item.id}/read`, {method: "PUT", credentials: "include"}); await load();
                                    })}>Mark as read</button>}
                                </div>
                            </article>
                            {events.length > 1 && <details className="notification-earlier border-t px-4 py-3">
                                <summary className="cursor-pointer text-sm font-semibold">Earlier updates · {events.length - 1}</summary>
                                <ol className="mt-3 space-y-3">{events.slice(1).map(event => <li key={event.id} className="border-l-2 border-[#dfc4ab] pl-3">
                                    <p className="text-sm font-semibold">{event.title} · {event.readAt ? "Read" : "Unread"}</p><p className="text-sm">{event.message}</p>
                                    <time dateTime={event.createdAt} className="text-xs">{formatBusinessTimestamp(event.createdAt, {day: "numeric", month: "short", hour: "numeric", minute: "2-digit"})} IST</time>
                                    {!event.readAt && <button disabled={busy} type="button" className="ml-3 min-h-11 text-sm underline" onClick={() => void perform(async () => {await inboxRequest<void>(`${base}/notifications/${event.id}/read`, {method: "PUT", credentials: "include"}); await load();})}>Mark as read</button>}
                                </li>)}</ol>
                            </details>}
                        </li>;
                    })}</ul>
                </section>;
            })}
        </div>
        {inbox?.nextBefore && <button type="button" disabled={busy} className="mt-4 min-h-11 rounded-xl border px-4 text-sm"
            onClick={() => void perform(async () => {
                const older = await inboxRequest<Inbox>(`${base}/notifications?${query}&before=${inbox.nextBefore}`, {credentials: "include"});
                setInbox(current => current ? {...older, messages: [...current.messages, ...older.messages.filter(item => !current.messages.some(existing => existing.id === item.id))]} : older);
            })}>Load older messages</button>}
        <details className="mt-6 rounded-2xl border p-4"><summary className="cursor-pointer font-semibold">Notification settings</summary>
        {preferences && <div className="mt-7 rounded-2xl bg-[#fffaf2] p-4">
            <h3 className="font-semibold">Notification preferences</h3>
            <p className="mt-2 text-sm leading-6">Order and payment messages stay in this inbox. Optional browser alerts and sound have separate controls below when available. SMS and email alerts are not enabled here.</p>
            <label className="mt-3 flex min-h-11 items-center gap-3 text-sm">
                <input type="checkbox" checked={preferences.offerInboxEnabled} disabled={busy || !preferences.marketingConsentGranted && !preferences.offerInboxEnabled}
                    aria-describedby="offer-inbox-help" onChange={event => {
                        const enabled = event.target.checked;
                        void perform(async () => {
                            const choices = await inboxRequest<Preferences>(`${base}/notification-preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({offerInboxEnabled: enabled})});
                            setPreferences(choices); setSaved("Notification preference saved.");
                        });
                    }} /> Include optional offers in my inbox when available
            </label>
            <p id="offer-inbox-help" className="mt-2 text-xs leading-5">Offers are optional and off by default. This preference does not grant marketing consent. Offer notifications are not being sent in this release.</p>
            {!preferences.marketingConsentGranted && <p className="mt-2 text-sm">Marketing consent is currently off. Manage it in <Link href="/profile/privacy" className="underline">Privacy and data choices</Link>.</p>}
            {saved && <p role="status" className="mt-2 text-sm">Notification preference saved.</p>}
        </div>}
        <CustomerAlertPreferences /></details>
    </section>;
}
