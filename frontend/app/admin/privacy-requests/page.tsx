"use client";

import {useCallback, useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {listPrivacyRequests, triagePrivacyRequest} from "@/services/adminPrivacyRequestsApi";
import type {PrivacyRequestEntry, PrivacyReviewState} from "@/services/adminPrivacyRequestsApi";

const labels: Record<PrivacyReviewState, string> = {
    RECEIVED: "Received", IN_REVIEW: "In review", NEEDS_REVERIFICATION: "Needs re-verification"
};

export default function AdminPrivacyRequestsPage() {
    const {authorization, ready, hasPermission} = useAdminAuth();
    const canReview = hasPermission("PRIVACY_REQUEST_VIEW");
    const [page, setPage] = useState(0);
    const [entries, setEntries] = useState<PrivacyRequestEntry[]>([]);
    const [loading, setLoading] = useState(true);
    const [pending, setPending] = useState<number | null>(null);
    const [error, setError] = useState<string | null>(null);

    const load = useCallback(async (auth: string, currentPage: number, signal?: AbortSignal) => {
        setLoading(true);
        setError(null);
        try {
            const next = await listPrivacyRequests(auth, currentPage, signal);
            if (!signal?.aborted) setEntries(next);
        } catch (failure) {
            if (!signal?.aborted) setError(failure instanceof Error ? failure.message : "Unable to load requests.");
        } finally {
            if (!signal?.aborted) setLoading(false);
        }
    }, []);

    useEffect(() => {
        if (!authorization || !canReview) return;
        const controller = new AbortController();
        const scheduled = window.setTimeout(() => {void load(authorization, page, controller.signal);}, 0);
        return () => {window.clearTimeout(scheduled); controller.abort();};
    }, [authorization, canReview, load, page]);

    async function changeState(entry: PrivacyRequestEntry, state: Exclude<PrivacyReviewState, "RECEIVED">) {
        if (!authorization || pending !== null) return;
        setPending(entry.id);
        setError(null);
        try {
            const updated = await triagePrivacyRequest(authorization, entry.id, state);
            setEntries(current => current.map(item => item.id === updated.id ? updated : item));
        } catch (failure) {
            setError(failure instanceof Error ? failure.message : "Unable to update request.");
        } finally {setPending(null);}
    }

    if (!ready) return <p className="p-6">Loading admin session…</p>;
    if (!authorization || !canReview) return <p className="p-6">You do not have permission to review privacy requests.</p>;

    return <main className="mx-auto max-w-5xl space-y-5 p-4 sm:p-6">
        <div className="flex flex-wrap items-start justify-between gap-4">
            <div><p className="text-xs font-bold uppercase tracking-widest text-[#a56e2e]">Customer privacy</p>
                <h1 className="mt-1 text-2xl font-bold text-[#241715]">Privacy requests</h1>
                <p className="mt-2 max-w-2xl text-sm text-[#756763]">Review request metadata only. These actions do not export, erase or complete a request; follow the approved privacy process before fulfillment.</p>
            </div>
            <button type="button" disabled={loading} onClick={() => {void load(authorization, page);}}
                className="min-h-11 rounded-xl border border-[#eadfd6] bg-white px-4 font-semibold text-[#7a1625] disabled:opacity-50">Refresh</button>
        </div>
        {error && <p role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">{error}</p>}
        {loading ? <p className="p-5">Loading requests…</p> : entries.length === 0
            ? <p className="rounded-2xl border border-[#eadfd6] bg-white p-6">No requests on this page.</p>
            : <div className="grid gap-4">{entries.map(entry => <article key={entry.id} className="rounded-2xl border border-[#eadfd6] bg-white p-5 shadow-sm">
                <div className="flex flex-wrap items-center justify-between gap-3">
                    <h2 className="font-bold text-[#241715]">#{entry.id} · {entry.kind === "EXPORT" ? "Data export" : "Deletion review"}</h2>
                    <span className="rounded-full bg-[#fff1e9] px-3 py-1 text-xs font-bold text-[#7a1625]">{labels[entry.state]}</span>
                </div>
                <p className="mt-2 break-all text-xs text-[#756763]">Verified subject: {entry.subjectId}</p>
                <p className="mt-1 text-xs text-[#756763]">Received: {new Intl.DateTimeFormat("en-IN", {dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Kolkata"}).format(new Date(entry.receivedAt))} IST</p>
                <div className="mt-4 flex flex-wrap gap-2">
                    <button type="button" disabled={pending !== null || entry.state === "IN_REVIEW"}
                        onClick={() => {void changeState(entry, "IN_REVIEW");}}
                        className="min-h-11 rounded-xl bg-[#7a1625] px-4 text-sm font-semibold text-white disabled:opacity-50">Start review</button>
                    <button type="button" disabled={pending !== null || entry.state === "NEEDS_REVERIFICATION"}
                        onClick={() => {void changeState(entry, "NEEDS_REVERIFICATION");}}
                        className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625] disabled:opacity-50">Needs re-verification</button>
                </div>
            </article>)}</div>}
        <nav aria-label="Privacy request pages" className="flex items-center gap-4">
            <button type="button" disabled={loading || page === 0} onClick={() => setPage(value => value - 1)} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 disabled:opacity-50">Previous</button>
            <span className="text-sm">Page {page + 1}</span>
            <button type="button" disabled={loading || entries.length < 50 || page >= 1000} onClick={() => setPage(value => value + 1)} className="min-h-11 rounded-xl border border-[#eadfd6] px-4 disabled:opacity-50">Next</button>
        </nav>
    </main>;
}
