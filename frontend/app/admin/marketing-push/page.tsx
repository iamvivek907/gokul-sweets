"use client";

import {useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {getActiveBranches} from "@/services/branchApi";
import {adminManagementApi} from "@/services/adminManagementApi";
import type {Branch} from "@/types/branch";

type Settings = {enabled: boolean; language: string; weeklyLimit: number; lunch: boolean; snacks: boolean; dinner: boolean; sweets: boolean; version: number};
type Copy = {key: string; topic: string; title: string; body: string};
type View = {settings: Settings; pushConfigured: boolean; masterEnabled: boolean; currentWindow: string | null; samples: Copy[];
    recent: {topic: string; title: string; message: string; reason: string; state: string; created_at: string}[]};
const input = "min-h-11 min-w-0 w-full rounded-xl border border-[#eadfd6] bg-white px-3";
const action = "min-h-11 rounded-xl bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50";

export default function MarketingPushPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const permitted = hasPermission("REBATE_MANAGE");
    const [branches, setBranches] = useState<Branch[]>([]);
    const [branch, setBranch] = useState("");
    const [view, setView] = useState<View | null>(null);
    const [draft, setDraft] = useState<Settings | null>(null);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const [revision, setRevision] = useState(0);
    const lock = useRef(false);
    const path = `/api/admin/marketing-push/${branch}`;
    useEffect(() => {
        if (!authorization || !permitted) return;
        let active = true;
        getActiveBranches().then(rows => {
            if (!active) return;
            setBranches(rows);
            let saved = "";
            try {saved = sessionStorage.getItem("gokul-marketing-branch") ?? "";} catch { /* storage optional */ }
            setBranch(rows.some(row => String(row.id) === saved) ? saved : String(rows[0]?.id ?? ""));
        }).catch(() => {if (active) setError("Could not load branches. Reload to retry.");});
        return () => {active = false;};
    }, [authorization, permitted]);
    useEffect(() => {
        if (!authorization || !permitted || !branch) return;
        const controller = new AbortController();
        adminManagementApi<View>(path, authorization, {signal: controller.signal}).then(data => {
            if (!controller.signal.aborted) {setView(data); setDraft(data.settings);}
        }).catch(cause => {if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : "Could not load settings.");});
        return () => controller.abort();
    }, [authorization, permitted, branch, path, revision]);
    async function perform(evaluate: boolean) {
        if (!authorization || !draft || lock.current) return;
        lock.current = true; setBusy(true); setError(""); setNotice("");
        try {
            if (evaluate) {
                const result = await adminManagementApi<{queued: number}>(`${path}/evaluate`, authorization, {method: "POST"});
                setNotice(result.queued ? `${result.queued} ${result.queued === 1 ? "message" : "messages"} queued. Delivery rechecks all preferences and eligibility.`
                    : "No messages queued. Customers may be outside meal times, opted out, recently ordered or reached their limit.");
            } else {
                const saved = await adminManagementApi<Settings>(path, authorization, {method: "PUT", headers: {"Content-Type": "application/json"}, body: JSON.stringify(draft)});
                setDraft(saved); setNotice(saved.enabled ? "Automatic campaigns enabled for this branch." : "Automatic campaigns paused for this branch.");
            }
            setRevision(value => value + 1);
        } catch (cause) {setError(cause instanceof Error ? cause.message : "Request failed. Reload before retrying.");}
        finally {lock.current = false; setBusy(false);}
    }
    if (!permitted) return <p className="p-6">Offer-management permission is required.</p>;
    const changed = !!draft && JSON.stringify(draft) !== JSON.stringify(view?.settings);
    return <main className="mx-auto max-w-5xl space-y-6 p-4 sm:p-6">
        <header><p className="text-sm font-bold uppercase tracking-widest text-[#7a1625]">Customer engagement</p>
            <h1 className="mt-2 text-3xl font-bold">Smart, cheerful nudges</h1>
            <p className="mt-2 text-[#756763]">Short messages chosen using meal time, past category preferences and the customer’s most recent branch.</p></header>
        <label className="flex flex-col gap-2 font-semibold">Branch<select className={input} value={branch} disabled={busy} onChange={event => {
            setBranch(event.target.value); setView(null); setDraft(null); setError(""); setNotice("");
            try {sessionStorage.setItem("gokul-marketing-branch", event.target.value);} catch { /* storage optional */ }
        }}><option value="">Choose branch</option>{branches.map(row => <option key={row.id} value={row.id}>{row.name}</option>)}</select></label>
        {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{error} <button className="underline" disabled={busy} onClick={() => {setError(""); setView(null); setDraft(null); setRevision(value => value + 1);}}>Reload settings</button></p>}
        {notice && <p role="status" className="rounded-xl bg-green-50 p-4 text-green-900">{notice}</p>}
        {!view && branch && !error && <p role="status">Loading campaign settings…</p>}
        {view && draft && <>
            <section className="space-y-4 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">
                <h2 className="text-xl font-bold">Automatic campaigns</h2>
                <p className="text-sm">Delivery: {view.pushConfigured && view.masterEnabled ? "Push service configured" : "Unavailable — check push keys, feature prerequisites and master switch"}. Current window: {view.currentWindow ?? "Outside sending hours"}.</p>
                <label className="flex min-h-11 items-center gap-3 font-semibold"><input type="checkbox" checked={draft.enabled} disabled={busy} onChange={event => setDraft({...draft, enabled: event.target.checked})} />Enable automatic promotional push</label>
                <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
                    <label className="flex flex-col gap-2">Message language<select className={input} value={draft.language} disabled={busy} onChange={event => setDraft({...draft, language: event.target.value})}><option value="HINGLISH">English titles + Hinglish messages</option><option value="EN">English</option></select></label>
                    <label className="flex flex-col gap-2">Maximum messages per customer in 7 days<select className={input} value={draft.weeklyLimit} disabled={busy} onChange={event => setDraft({...draft, weeklyLimit: Number(event.target.value)})}>{[1,2,3].map(value => <option key={value} value={value}>{value}</option>)}</select></label>
                </div>
                <fieldset disabled={busy}><legend className="font-semibold">Allowed topics and India-time windows</legend>
                    {([['lunch','Lunch · 12:00–13:30'],['snacks','Snacks · 16:00–18:00'],['dinner','Dinner · 19:00–20:30'],['sweets','Sweet suggestions for customers who favour sweets']] as const).map(([key,label]) =>
                        <label key={key} className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={draft[key]} onChange={event => setDraft({...draft,[key]:event.target.checked})} />{label}</label>)}
                </fieldset>
                <p className="text-sm text-[#756763]">One promotional message per day across branches. Skip recent purchases and active orders. Respect customer quiet hours, marketing consent and offer-notification preferences. Only customers with a past completed order and an active push subscription are considered.</p>
                <div className="flex flex-wrap gap-3"><button className={action} disabled={busy || !changed} onClick={() => void perform(false)}>{busy ? "Working…" : "Save settings"}</button>
                    <button className={`${input} font-semibold`} disabled={busy || changed || !draft.enabled || !view.masterEnabled} onClick={() => void perform(true)}>Evaluate eligible customers now</button></div>
                <p className="text-xs text-[#756763]">Evaluation can queue customer messages; it never bypasses time windows or limits. Saving a pause cancels eligibility for queued messages before delivery.</p>
            </section>
            <section><h2 className="text-xl font-bold">Built-in message previews</h2><p className="mt-1 text-sm text-[#756763]">The engine rotates these messages and avoids repeating the same template for 14 days. Save a language change to refresh previews. No external AI provider or customer-data upload is needed.</p>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">{view.samples.map(copy => <article key={copy.key} className="rounded-2xl border border-[#eadfd6] bg-white p-4"><p className="text-xs font-bold text-[#7a1625]">GOKUL SWEETS · {copy.topic}</p><h3 className="mt-2 font-bold">{copy.title}</h3><p className="mt-1 text-sm">{copy.body}</p></article>)}</div>
            </section>
            <section><h2 className="text-xl font-bold">Recent decisions</h2><p className="mt-1 text-sm text-[#756763]">Accepted means the push provider accepted the message; it does not confirm the customer saw it.</p>
                {!view.recent.length && <p className="mt-3">No campaign messages yet.</p>}
                <div className="mt-4 space-y-3">{view.recent.map((row,index) => <article key={index} className="rounded-xl border border-[#eadfd6] p-4"><p className="font-bold">{row.title}</p><p className="text-sm">{row.reason} · {row.state}</p><time className="text-xs text-[#756763]">{new Date(row.created_at).toLocaleString("en-IN",{timeZone:"Asia/Kolkata"})} IST</time></article>)}</div>
            </section>
        </>}
    </main>;
}
