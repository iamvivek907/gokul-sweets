"use client";

import {useCallback, useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminManagementApi} from "@/services/adminManagementApi";

interface Config {enabled: boolean; dailyTime: string; retentionDays: number; revision: number}
interface View {config: Config; timeZone: string; status: string; startedAt: string | null; finishedAt: string | null;
    trigger: string | null; error: string | null; deleted: Record<string, number>; limitPerCategory: number; running: boolean}
interface Preview {revision: number; cutoff: string; eligible: Record<string, number>; limitPerCategory: number}
const labels: Record<string,string> = {customerNotifications: "Customer updates", customerDeliveries: "Customer delivery records",
    staffAlerts: "Staff alerts", staffReads: "Staff read records", staffDeliveries: "Staff delivery records"};
const button = "min-h-11 rounded-xl border border-[#eadfd6] bg-white px-4 font-semibold text-[#7a1625] disabled:opacity-50";
function date(value: string | null) {return value ? new Intl.DateTimeFormat("en-IN", {dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata"}).format(new Date(value)) + " IST" : "—";}

export default function DataCleanupPage() {
    const {authorization, profile, ready} = useAdminAuth();
    const owner = profile?.roleName === "OWNER_ADMIN";
    const [view, setView] = useState<View | null>(null);
    const [draft, setDraft] = useState<Config | null>(null);
    const [preview, setPreview] = useState<Preview | null>(null);
    const [busy, setBusy] = useState<string | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [notice, setNotice] = useState<string | null>(null);
    const operation = useRef<AbortController | null>(null);
    const load = useCallback(async () => {
        if (!authorization || !owner || operation.current) return;
        const controller = new AbortController();
        operation.current = controller;
        setBusy("refresh");
        try {
            const result = await adminManagementApi<View>("/api/admin/data-cleanup", authorization,
                {signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)])});
            if (!controller.signal.aborted) {setView(result); setDraft(result.config); setPreview(null); setError(null);}
        } catch (failure) {
            if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Unable to load cleanup settings.");
        } finally {
            if (operation.current === controller) {operation.current = null; setBusy(null);}
        }
    }, [authorization, owner]);
    useEffect(() => {
        const timer = window.setTimeout(() => {void load();}, 0);
        return () => {window.clearTimeout(timer); operation.current?.abort(); operation.current = null;};
    }, [load]);
    const dirty = !!draft && !!view && (draft.enabled !== view.config.enabled || draft.dailyTime !== view.config.dailyTime || draft.retentionDays !== view.config.retentionDays);
    async function action(kind: "save" | "preview" | "run") {
        if (!authorization || !draft || operation.current) return;
        const controller = new AbortController();
        operation.current = controller;
        setBusy(kind); setError(null); setNotice(null);
        try {
            if (kind === "preview") {
                const result = await adminManagementApi<Preview>("/api/admin/data-cleanup/preview", authorization, {method: "POST", signal: controller.signal});
                if (controller.signal.aborted) return;
                setPreview(result);
            } else {
                const result = await adminManagementApi<View>(`/api/admin/data-cleanup${kind === "run" ? "/run" : ""}`, authorization,
                    {method: kind === "run" ? "POST" : "PUT", signal: controller.signal, headers: {"Content-Type": "application/json"},
                        body: JSON.stringify(kind === "run" ? {revision: preview?.revision} : draft)});
                if (controller.signal.aborted) return;
                setView(result); setDraft(result.config); setPreview(null);
                setNotice(kind === "run" ? "Cleanup finished. Deleted counts are shown below." : "Daily cleanup settings saved.");
            }
        } catch (failure) {
            if (controller.signal.aborted) return;
            if (kind === "run") {
                setPreview(null);
                try {
                    const result = await adminManagementApi<View>("/api/admin/data-cleanup", authorization,
                        {signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)])});
                    if (controller.signal.aborted) return;
                    setView(result); setDraft(result.config);
                } catch {if (controller.signal.aborted) return;}
            }
            setError(failure instanceof Error ? failure.message : "Unable to complete cleanup action.");
        } finally {if (operation.current === controller) {operation.current = null; setBusy(null);}}
    }
    if (!ready) return <p className="p-6">Loading admin session…</p>;
    if (!owner || !authorization) return <p className="p-6">Only the owner can manage data cleanup.</p>;
    return <main className="mx-auto max-w-4xl space-y-5 p-4 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-3">
            <div><h1 className="text-2xl font-bold text-[#241715]">Data cleanup</h1>
                <p className="mt-2 text-sm text-[#756763]">Remove old routine notifications for completed orders.</p></div>
            <button className={button} disabled={!!busy} onClick={() => {setNotice(null); void load();}}>{busy === "refresh" ? "Refreshing…" : "Refresh"}</button>
        </div>
        {error && <p role="alert" className="rounded-xl bg-red-50 p-4 text-sm text-red-800">{error}</p>}
        {notice && <p role="status" className="rounded-xl bg-green-50 p-4 text-sm text-green-800">{notice}</p>}
        {!view || !draft ? <p>Loading settings…</p> : <>
            <section aria-labelledby="schedule-title" className="space-y-4 rounded-2xl border border-[#eadfd6] bg-white p-5">
                <h2 id="schedule-title" className="text-lg font-bold">Daily schedule</h2>
                <label className="flex min-h-11 items-center gap-3"><input type="checkbox" checked={draft.enabled} disabled={!!busy}
                    onChange={event => {setDraft({...draft, enabled: event.target.checked}); setPreview(null);}} />Run automatically every day</label>
                <div className="grid gap-4 sm:grid-cols-2">
                    <label className="space-y-2 text-sm font-semibold"><span className="block">Daily time (IST)</span>
                        <input type="time" value={draft.dailyTime} disabled={!!busy} onChange={event => {setDraft({...draft, dailyTime: event.target.value}); setPreview(null);}}
                            className="min-h-11 w-full rounded-xl border border-[#eadfd6] px-3" /></label>
                    <label className="space-y-2 text-sm font-semibold"><span className="block">Keep history for (days)</span>
                        <input type="number" min={30} max={3650} step={1} value={draft.retentionDays} disabled={!!busy}
                            onChange={event => {setDraft({...draft, retentionDays: Number(event.target.value)}); setPreview(null);}}
                            className="min-h-11 w-full rounded-xl border border-[#eadfd6] px-3" /></label>
                </div>
                <p className="text-sm text-[#756763]">Default: 90 days. Minimum: 30 days. Runs once per day while the server is awake; if it starts after the configured time, it catches up that day.</p>
                <button className={button} disabled={!!busy || !dirty || !draft.dailyTime || !Number.isInteger(draft.retentionDays) || draft.retentionDays < 30 || draft.retentionDays > 3650}
                    onClick={() => {void action("save");}}>{busy === "save" ? "Saving…" : "Save schedule"}</button>
            </section>
            <section aria-labelledby="manual-title" className="space-y-4 rounded-2xl border border-[#eadfd6] bg-white p-5">
                <h2 id="manual-title" className="text-lg font-bold">Run now</h2>
                <p className="text-sm text-[#756763]">Preview the next batch using your saved settings. You can run manually even when the daily schedule is paused.</p>
                {dirty && <p className="text-sm text-amber-800">Save your changes before previewing.</p>}
                <button className={button} disabled={!!busy || dirty || view.running} onClick={() => {void action("preview");}}>
                    {busy === "preview" ? "Checking…" : "Preview eligible records"}</button>
                {preview && <div className="space-y-3">
                    <p className="text-sm">Eligible history before {date(preview.cutoff)}. Customer updates must also have been read before this date.</p>
                    <dl className="grid grid-cols-1 gap-2 sm:grid-cols-2">{Object.entries(preview.eligible).map(([key, value]) => <div key={key} className="flex justify-between gap-3 rounded-xl bg-[#fffaf3] p-3 text-sm">
                        <dt>{labels[key] || key}</dt><dd className="font-bold">{value.toLocaleString("en-IN")}</dd></div>)}</dl>
                    <p className="text-sm text-[#756763]">Up to {preview.limitPerCategory} customer updates and {preview.limitPerCategory} staff alerts per run, plus their linked records. Counts may change before execution; larger backlogs clear over later runs.</p>
                    <button className={`${button} bg-[#7a1625]! text-white!`} disabled={!!busy || dirty || preview.revision !== view.config.revision || !Object.values(preview.eligible).some(value => value > 0)}
                        onClick={() => {void action("run");}}>{busy === "run" ? "Cleaning…" : "Clean up these old records"}</button>
                </div>}
            </section>
            <section className="space-y-2 rounded-2xl border border-[#eadfd6] bg-white p-5" aria-labelledby="result-title">
                <h2 id="result-title" className="text-lg font-bold">Last run</h2>
                <p className="text-sm">{view.status === "NEVER" ? "No cleanup has run yet." : `${view.status} · ${view.trigger === "DAILY" ? "Daily schedule" : "Manual run"}`}</p>
                {view.startedAt && <p className="text-sm">Started: {date(view.startedAt)}{view.finishedAt && ` · Finished: ${date(view.finishedAt)}`}</p>}
                {view.status === "RUNNING" && <p className="text-sm">Refresh to check completion. A stopped run can be retried after its five-minute lock expires.</p>}
                {view.error && <p className="text-sm text-red-800">{view.error}</p>}
                {Object.entries(view.deleted).map(([key, value]) => <p className="text-sm" key={key}>{labels[key] || key}: {value.toLocaleString("en-IN")} deleted</p>)}
            </section>
            <p className="rounded-xl bg-[#fffaf3] p-4 text-sm text-[#756763]">Kept: orders, payments, refunds, stock and allocation history, audit trails, unread customer messages, completion messages and occasion messages. Alerts for active orders and pending notification deliveries are also kept.</p>
        </>}
    </main>;
}
