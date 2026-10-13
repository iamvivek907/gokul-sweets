"use client";

import {useEffect, useRef, useState, type FormEvent} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {adminManagementApi} from "@/services/adminManagementApi";

type Provider = "PHONEPE" | "RAZORPAY";
type Workspace = {
    activeProvider: Provider;
    revision: number;
    providers: {provider: Provider; ready: boolean; issues: string[]}[];
    history: {previousProvider: string; activeProvider: string; revision: number;
        username: string; reason: string; changedAt: string}[];
};
const label = (provider: string) => provider === "PHONEPE" ? "PhonePe" : provider === "RAZORPAY" ? "Razorpay" : provider;

export default function PaymentProvidersPage() {
    const {authorization, profile} = useAdminAuth();
    const owner = profile?.roleName === "OWNER_ADMIN";
    const identity = `${profile?.staffId}:${authorization}`;
    const [loaded, setLoaded] = useState<{identity: string; value: Workspace} | null>(null);
    const [failure, setFailure] = useState<{identity: string; message: string} | null>(null);
    const [attempt, setAttempt] = useState(0);
    const [busy, setBusy] = useState(false);
    const operation = useRef<AbortController | null>(null);
    const data = loaded?.identity === identity ? loaded.value : null;
    const error = failure?.identity === identity ? failure.message : "";

    useEffect(() => {
        if (!authorization || !owner) return;
        const controller = new AbortController();
        operation.current = controller;
        adminManagementApi<Workspace>("/api/admin/payment-providers", authorization,
            {cache: "no-store", signal: AbortSignal.any([controller.signal, AbortSignal.timeout(15000)])})
            .then(value => {if (!controller.signal.aborted) {setLoaded({identity, value}); setFailure(null);}})
            .catch(() => {if (!controller.signal.aborted) setFailure({identity, message: "Unable to load payment settings. Refresh to try again."});})
            .finally(() => {if (operation.current === controller) operation.current = null;});
        return () => {controller.abort(); operation.current?.abort(); operation.current = null;};
    }, [authorization, owner, identity, attempt]);

    async function activate(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!authorization || !owner || !data || operation.current) return;
        const form = new FormData(event.currentTarget);
        const controller = new AbortController();
        operation.current = controller;
        setBusy(true);
        setFailure(null);
        try {
            const value = await adminManagementApi<Workspace>("/api/admin/payment-providers", authorization, {
                method: "PUT", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({provider: form.get("provider"), expectedRevision: data.revision, reason: form.get("reason")}),
                signal: AbortSignal.any([controller.signal, AbortSignal.timeout(20000)])
            });
            if (!controller.signal.aborted) setLoaded({identity, value});
        } catch (error) {
            if (!controller.signal.aborted) {
                setLoaded(null);
                setFailure({identity, message: `${error instanceof Error ? error.message : "Unable to confirm the switch."} Refresh to confirm the current provider before trying again.`});
            }
        } finally {
            if (operation.current === controller) {operation.current = null; setBusy(false);}
        }
    }

    if (!profile) return <main className="p-6"><p role="status">Loading your account…</p></main>;
    if (!owner) return <main className="p-6"><p role="alert">Only owners can manage payment providers.</p></main>;

    return <main className="mx-auto max-w-4xl space-y-6 p-4 text-[#241715] sm:p-8">
        <header><p className="text-sm font-semibold text-[#7a1625]">System</p>
            <h1 className="mt-1 text-2xl font-bold">Payment providers</h1>
            <p className="mt-2 text-sm">Choose how new payments are collected across all branches. Existing payments and refunds continue with their original provider.</p>
        </header>
        {error && <p role="alert" className="rounded-xl border border-[#a94d39] bg-[#fff1e9] p-4 text-[#7a1625]">{error}</p>}
        <button type="button" disabled={busy} onClick={() => {if (!operation.current) {setLoaded(null); setFailure(null); setAttempt(value => value + 1);}}}
            className="min-h-11 rounded-xl border border-[#756763] bg-white px-4 font-semibold disabled:opacity-60">Refresh settings</button>
        {!data && !error && <p role="status">Loading payment settings…</p>}
        {data && <>
            <section className="rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5" aria-label="Active payment provider">
                <p className="text-sm">Active for new payments</p><p className="mt-1 text-2xl font-bold text-[#7a1625]">{label(data.activeProvider)}</p>
                <p className="mt-2 text-sm">Switches take effect when new payment attempts start. An attempt already in progress stays with its original provider.</p>
            </section>
            <form key={data.revision} onSubmit={event => void activate(event)} className="space-y-4">
                <fieldset disabled={busy} className="space-y-3"><legend className="mb-3 text-lg font-bold">Select a provider</legend>
                    {data.providers.map(provider => <label key={provider.provider} className="block rounded-2xl border border-[#eadfd6] bg-white p-4">
                        <span className="flex items-center gap-3"><input type="radio" name="provider" value={provider.provider}
                            defaultChecked={provider.provider === data.activeProvider} disabled={!provider.ready} required className="h-5 w-5 accent-[#7a1625]"/>
                            <span className="font-bold">{label(provider.provider)}</span>
                            <span className={`ml-auto rounded-full px-3 py-1 text-xs font-semibold ${provider.ready ? "bg-[#e4f2ec] text-[#143936]" : "bg-[#fff1e9] text-[#7a1625]"}`}>{provider.ready ? "Configured" : "Setup needed"}</span>
                        </span>
                        {provider.issues.length > 0 && <ul className="mt-3 list-disc space-y-1 pl-6 text-sm">{provider.issues.map(issue => <li key={issue}>{issue}</li>)}</ul>}
                    </label>)}
                </fieldset>
                <p className="text-sm">Configuration checks do not confirm gateway uptime or merchant approval. Complete a payment and refund test before activating a provider for customers. Credentials stay in the backend deployment settings.</p>
                <label className="block text-sm font-semibold">Reason for switching
                    <input name="reason" required maxLength={300} disabled={busy} placeholder="For example, moving new payments to Razorpay"
                        className="mt-2 block min-h-11 w-full rounded-xl border border-[#756763] bg-white px-3 text-[#241715] placeholder:text-[#756763]"/>
                </label>
                <button type="submit" disabled={busy || !data.providers.some(provider => provider.ready)}
                    className="min-h-11 rounded-xl bg-[#7a1625] px-5 font-bold text-white disabled:opacity-60">{busy ? "Updating provider…" : "Activate selected provider"}</button>
            </form>
            <section aria-label="Recent provider changes"><h2 className="text-lg font-bold">Recent changes</h2>
                {data.history.length === 0 ? <p className="mt-2 text-sm">No provider switches yet.</p> : <ol className="mt-3 space-y-3">{data.history.map(change => <li key={change.revision} className="rounded-xl border border-[#eadfd6] bg-white p-4">
                    <p className="font-semibold">{label(change.previousProvider)} → {label(change.activeProvider)}</p>
                    <p className="mt-1 text-sm">{change.reason}</p>
                    <p className="mt-2 text-xs text-[#756763]">{change.username} · {new Date(change.changedAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST</p>
                </li>)}</ol>}
            </section>
        </>}
    </main>;
}
