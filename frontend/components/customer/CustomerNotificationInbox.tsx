"use client";

import {useCallback, useEffect, useState} from "react";
import Link from "next/link";
import CustomerAlertPreferences from "@/components/customer/CustomerAlertPreferences";
import {apiClient, ApiError} from "@/services/apiClient";
import {formatBusinessTimestamp} from "@/lib/businessTime";

const base = "/api/customer/identity";
type Message = {id: number; eventKey: string; kind: string; targetType: "ORDER" | "OCCASION";
    targetId: string; title: string; message: string; deliveryState: string; createdAt: string; readAt: string | null};
type Inbox = {messages: Message[]; unreadCount: number; nextBefore: number | null};
type Preferences = {offerInboxEnabled: boolean; marketingConsentGranted: boolean};

export default function CustomerNotificationInbox() {
    const [inbox, setInbox] = useState<Inbox | null>(null);
    const [preferences, setPreferences] = useState<Preferences | null>(null);
    const [error, setError] = useState("");
    const [busy, setBusy] = useState(false);
    const [saved, setSaved] = useState("");
    const [unavailable, setUnavailable] = useState(false);
    const load = useCallback(async (signal?: AbortSignal) => {
        const [next, choices] = await Promise.all([
            apiClient<Inbox>(`${base}/notifications`, {credentials: "include", signal}),
            apiClient<Preferences>(`${base}/notification-preferences`, {credentials: "include", signal})
        ]);
        setInbox(next); setPreferences(choices); setError("");
    }, []);
    useEffect(() => {
        const controller = new AbortController();
        void Promise.all([
            apiClient<Inbox>(`${base}/notifications`, {credentials: "include", signal: controller.signal}),
            apiClient<Preferences>(`${base}/notification-preferences`, {credentials: "include", signal: controller.signal})
        ]).then(([next, choices]) => {
            if (!controller.signal.aborted) {setInbox(next); setPreferences(choices); setError("");}
        }).catch(reason => {
            if (controller.signal.aborted) return;
            if (reason instanceof ApiError && reason.status === 404) setUnavailable(true);
            else setError("Your inbox could not load. Check Order history for current updates, or refresh when connected.");
        });
        return () => controller.abort();
    }, []);
    async function perform(action: () => Promise<void>) {
        setBusy(true); setError(""); setSaved("");
        try {await action();}
        catch {setError("We could not confirm that change. Please refresh or try again when connected.");}
        finally {setBusy(false);}
    }
    if (unavailable) return <section className="rounded-3xl border border-[#eadfd6] bg-white p-6">
        <h2 className="text-xl font-semibold">Notification inbox</h2><p className="mt-2 text-sm">The inbox is not available yet. Check Order history for current updates.</p></section>;
    return <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8" aria-label="Notification inbox">
        <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 className="text-xl font-semibold">Notification inbox {inbox && <span className="text-sm font-normal">· {inbox.unreadCount} unread</span>}</h2>
            <button type="button" disabled={busy} onClick={() => void perform(() => load())}
                className="min-h-11 rounded-xl border px-4 text-sm font-semibold disabled:opacity-50">Refresh inbox</button>
        </div>
        <p className="mt-2 text-sm leading-6 text-[#756763]">Payment and order updates are saved here for your signed-in account. Open a message’s order or request for its latest status. Times are in IST.</p>
        {error && <p role="alert" className="mt-4 rounded-xl border border-[#c76752] p-3 text-sm">{error}</p>}
        {!inbox && !error && <p role="status" className="mt-4">Loading your inbox…</p>}
        {inbox?.messages.length === 0 && <p className="mt-5 rounded-xl bg-[#fffaf2] p-4 text-sm">No messages yet. New verified payments and branch updates will appear here.</p>}
        <ul className="mt-5 space-y-3">
            {inbox?.messages.map(item => <li key={item.id} className="rounded-2xl border border-[#eadfd6] p-4">
                <div className="flex flex-wrap justify-between gap-2"><h3 className="font-semibold">{item.title}</h3>
                    <span className="text-xs">{item.readAt ? "Read" : "Unread"}</span></div>
                <p className="mt-2 text-sm leading-6">{item.message}</p>
                <time dateTime={item.createdAt} className="mt-2 block text-xs text-[#756763]">
                    {formatBusinessTimestamp(item.createdAt, {day: "numeric", month: "short", year: "numeric", hour: "numeric", minute: "2-digit"})} IST
                </time>
                <div className="mt-3 flex flex-wrap items-center gap-4">
                    <Link className="flex min-h-11 items-center break-all text-sm font-semibold underline" href={item.targetType === "ORDER"
                        ? `/orders/${encodeURIComponent(item.targetId)}` : `/occasions#occasion-${encodeURIComponent(item.targetId)}`}>
                        {item.targetType === "ORDER" ? `Open order ${item.targetId}` : "Open occasion request"}</Link>
                    {!item.readAt && <button type="button" disabled={busy} className="min-h-11 text-sm underline disabled:opacity-50"
                        onClick={() => void perform(async () => {
                            await apiClient<void>(`${base}/notifications/${item.id}/read`, {method: "PUT", credentials: "include"});
                            await load();
                        })}>Mark as read</button>}
                </div>
            </li>)}
        </ul>
        {inbox?.nextBefore && <button type="button" disabled={busy} className="mt-4 min-h-11 rounded-xl border px-4 text-sm"
            onClick={() => void perform(async () => {
                const older = await apiClient<Inbox>(`${base}/notifications?before=${inbox.nextBefore}`, {credentials: "include"});
                setInbox(current => current ? {...older, messages: [...current.messages, ...older.messages.filter(item => !current.messages.some(existing => existing.id === item.id))]} : older);
            })}>Load older messages</button>}
        {preferences && <div className="mt-7 rounded-2xl bg-[#fffaf2] p-4">
            <h3 className="font-semibold">Notification preferences</h3>
            <p className="mt-2 text-sm leading-6">Order and payment messages stay in this inbox. Optional browser alerts and sound have separate controls below when available. SMS and email alerts are not enabled here.</p>
            <label className="mt-3 flex min-h-11 items-center gap-3 text-sm">
                <input type="checkbox" checked={preferences.offerInboxEnabled} disabled={busy || !preferences.marketingConsentGranted && !preferences.offerInboxEnabled}
                    aria-describedby="offer-inbox-help" onChange={event => {
                        const enabled = event.target.checked;
                        void perform(async () => {
                            const choices = await apiClient<Preferences>(`${base}/notification-preferences`, {method: "PUT", credentials: "include", body: JSON.stringify({offerInboxEnabled: enabled})});
                            setPreferences(choices); setSaved("Notification preference saved.");
                        });
                    }} /> Include optional offers in my inbox when available
            </label>
            <p id="offer-inbox-help" className="mt-2 text-xs leading-5">Offers are optional and off by default. This preference does not grant marketing consent. Offer notifications are not being sent in this release.</p>
            {!preferences.marketingConsentGranted && <p className="mt-2 text-sm">Marketing consent is currently off. Manage it in <Link href="/profile/privacy" className="underline">Privacy and data choices</Link>.</p>}
            {saved && <p role="status" className="mt-2 text-sm">Notification preference saved.</p>}
        </div>}
        <CustomerAlertPreferences />
    </section>;
}
