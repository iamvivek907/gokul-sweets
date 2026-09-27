"use client";

import {useCallback, useEffect, useState} from "react";
import {apiClient} from "@/services/apiClient";

type Purpose = "MARKETING" | "OCCASION_REMINDERS" | "COARSE_AREA_ANALYTICS";
type Decision = {granted: boolean; policyVersion: string; recordedAt: string | null};
type Choices = Record<Purpose, Decision>;
type PrivacyKind = "EXPORT" | "DELETION_REVIEW";
type PrivacyRequest = {id: number; kind: PrivacyKind; receivedAt: string};

const purposes: {id: Purpose; title: string; detail: string}[] = [
    {id: "MARKETING", title: "Offers and news", detail: "Optional promotional messages. Order updates are separate."},
    {id: "OCCASION_REMINDERS", title: "Occasion reminders", detail: "Optional reminders for dates you choose to share."},
    {id: "COARSE_AREA_ANALYTICS", title: "Area insights", detail: "Optional coarse area insights; never continuous location tracking."}
];

export default function ConsentPreferences() {
    const [choices, setChoices] = useState<Choices | null>(null);
    const [pending, setPending] = useState<Purpose | null>(null);
    const [error, setError] = useState<string | null>(null);
    const [requests, setRequests] = useState<PrivacyRequest[] | null>(null);
    const [requesting, setRequesting] = useState<PrivacyKind | null>(null);

    const refresh = useCallback(async () => {
        try {
            const next = await apiClient<Choices>("/api/customer/identity/consents", {credentials: "include"});
            setChoices(next);
            const existing = await apiClient<PrivacyRequest[]>("/api/customer/identity/privacy-requests", {credentials: "include"});
            setRequests(existing);
            setError(null);
        } catch {
            setChoices(null);
            setRequests(null);
        }
    }, []);

    useEffect(() => {
        const scheduled = window.setTimeout(() => {void refresh();}, 0);
        const onFocus = () => {void refresh();};
        window.addEventListener("focus", onFocus);
        return () => {window.clearTimeout(scheduled); window.removeEventListener("focus", onFocus);};
    }, [refresh]);

    async function choose(purpose: Purpose, granted: boolean) {
        if (pending) return;
        setPending(purpose);
        setError(null);
        try {
            const decision = await apiClient<Decision>(`/api/customer/identity/consents/${purpose}`, {
                method: "PUT", credentials: "include", body: JSON.stringify({granted})
            });
            setChoices(current => current && {...current, [purpose]: decision});
        } catch {
            await refresh();
            setError("Your choice could not be saved. Please try again.");
        } finally {
            setPending(null);
        }
    }

    async function requestPrivacyReview(kind: PrivacyKind) {
        if (requesting) return;
        setRequesting(kind);
        setError(null);
        try {
            const entry = await apiClient<PrivacyRequest>(`/api/customer/identity/privacy-requests/${kind}`, {
                method: "POST", credentials: "include"
            });
            setRequests(current => current && current.some(item => item.id === entry.id) ? current : [...(current ?? []), entry]);
        } catch {
            setError("Your request could not be recorded. Please try again.");
        } finally {
            setRequesting(null);
        }
    }

    if (!choices) return null;
    return <section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Privacy choices">
        <h2 className="text-xl font-semibold text-[#241715]">Privacy choices</h2>
        <p className="mt-2 text-sm leading-6 text-[#756763]">These are optional. Pickup and order updates work without them. You can change your choices anytime.</p>
        <div className="mt-4 space-y-4">
            {purposes.map(({id, title, detail}) => <div key={id} className="flex items-start justify-between gap-4 border-t border-[#eadfd6] pt-4">
                <div><p className="font-semibold text-[#241715]">{title}</p><p className="mt-1 text-sm text-[#756763]">{detail}</p></div>
                <label className="flex shrink-0 items-center gap-2 text-sm text-[#241715]">
                    <input type="checkbox" checked={choices[id]?.granted ?? false} disabled={pending !== null}
                        onChange={event => {void choose(id, event.target.checked);}}
                        className="h-5 w-5 accent-[#7a1625]" aria-label={title} />
                    {pending === id ? "Saving" : choices[id]?.granted ? "On" : "Off"}
                </label>
            </div>)}
        </div>
        {requests && <div className="mt-6 border-t border-[#eadfd6] pt-5">
            <h3 className="font-semibold text-[#241715]">Your data requests</h3>
            <p className="mt-1 text-sm text-[#756763]">Submit a request for manual review. Submitting a deletion review does not immediately erase financial order records.</p>
            <div className="mt-3 flex flex-wrap gap-3">
                {(["EXPORT", "DELETION_REVIEW"] as const).map(kind => {
                    const existing = requests.find(item => item.kind === kind);
                    return <button key={kind} type="button" disabled={Boolean(existing) || requesting !== null}
                        onClick={() => {void requestPrivacyReview(kind);}}
                        className="min-h-11 rounded-xl border border-[#eadfd6] px-4 text-sm font-semibold text-[#7a1625] disabled:opacity-60">
                        {existing ? `${kind === "EXPORT" ? "Export" : "Deletion review"} request received`
                            : requesting === kind ? "Submitting…" : kind === "EXPORT" ? "Request data export" : "Request deletion review"}
                    </button>;
                })}
            </div>
        </div>}
        {error && <p role="alert" className="mt-4 text-sm text-[#9e2732]">{error}</p>}
    </section>;
}
