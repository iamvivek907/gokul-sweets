"use client";

import {useEffect, useState} from "react";
import Image from "next/image";
import {adminManagementApi} from "@/services/adminManagementApi";
import type {AdminBranch} from "@/types/adminBranches";

type Snapshot = {branchId: number; draftImageUrl: string | null; draftMobileUrl: string | null;
    draftAltText: string | null; draftDescription: string | null; publishedImageUrl: string | null;
    publishedMobileUrl: string | null; publishedAltText: string | null; publishedDescription: string | null;
    editVersion: number; publishedRevision: number};
type Publication = {revision: number; imageUrl: string; altText: string; actorStaffId: number};

export default function BranchExperienceEditor({branch, authorization, allowed}: {
    branch: AdminBranch; authorization: string; allowed: boolean;
}) {
    const path = `/api/admin/branches/${branch.id}/experience`;
    const [snapshot, setSnapshot] = useState<Snapshot | null>(null);
    const [history, setHistory] = useState<Publication[]>([]);
    const [altText, setAltText] = useState("");
    const [description, setDescription] = useState("");
    const [desktop, setDesktop] = useState<File | null>(null);
    const [mobile, setMobile] = useState<File | null>(null);
    const [preview, setPreview] = useState<string | null>(null);
    const [busy, setBusy] = useState(false);
    const [message, setMessage] = useState("");
    const [error, setError] = useState("");

    useEffect(() => {
        if (!desktop) return;
        const reader = new FileReader();
        // Preview bytes stay in this browser until the editor explicitly saves the draft.
        reader.onload = () => setPreview(typeof reader.result === "string" ? reader.result : null);
        reader.readAsDataURL(desktop);
        return () => reader.abort();
    }, [desktop]);

    useEffect(() => {
        if (!allowed) return;
        const controller = new AbortController();
        Promise.all([adminManagementApi<Snapshot>(path, authorization, {signal: controller.signal}),
            adminManagementApi<Publication[]>(`${path}/publications`, authorization, {signal: controller.signal})])
            .then(([data, revisions]) => {
                if (controller.signal.aborted) return;
                setSnapshot(data); setHistory(revisions);
                setAltText(data.draftAltText ?? ""); setDescription(data.draftDescription ?? "");
            }).catch(() => {if (!controller.signal.aborted) setError("Branch artwork could not load.");});
        return () => controller.abort();
    }, [allowed, authorization, path]);

    async function change(action: "draft" | "publish" | "restore", revision?: number) {
        if (!snapshot || busy) return;
        setBusy(true); setError(""); setMessage("");
        try {
            let next = snapshot;
            const headers = () => ({"If-Match": String(next.editVersion)});
            if (action === "draft") {
                next = await adminManagementApi<Snapshot>(path, authorization, {method: "PUT",
                    headers: {...headers(), "Content-Type": "application/json"}, body: JSON.stringify({altText, description})});
                setSnapshot(next);
                for (const [file, isMobile] of [[desktop, false], [mobile, true]] as const) {
                    if (!file) continue;
                    if (!(["image/jpeg", "image/png", "image/webp"].includes(file.type)) || file.size > 5 * 1024 * 1024)
                        throw new Error("Use a JPG, PNG or static WebP image of 5 MB or less.");
                    const body = new FormData(); body.append("file", file);
                    next = await adminManagementApi<Snapshot>(`${path}/media?mobile=${isMobile}`, authorization,
                        {method: "POST", headers: headers(), body});
                    setSnapshot(next);
                }
                setDesktop(null); setMobile(null);
                setMessage("Draft saved. The public branch stays unchanged until you publish.");
            } else {
                next = await adminManagementApi<Snapshot>(action === "publish" ? `${path}/publish`
                    : `${path}/publications/${revision}/restore`, authorization, {method: "POST", headers: headers()});
                setSnapshot(next); setAltText(next.draftAltText ?? ""); setDescription(next.draftDescription ?? "");
                setHistory(await adminManagementApi<Publication[]>(`${path}/publications`, authorization));
                setMessage(action === "publish" ? "Branch artwork published." : "Previous publication restored as a new revision.");
            }
        } catch (cause) {setError(cause instanceof Error ? cause.message : "Could not save branch artwork. Reload and try again.");}
        finally {setBusy(false);}
    }

    return <section className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-5" aria-label="Branch artwork and published details">
        <h3 className="text-lg font-bold">Branch artwork and public details</h3>
        <p className="mt-1 text-sm text-[#756763]">Choose a real photo of {branch.name}. Save a draft, preview it, then publish deliberately. This uses the existing R2 media storage.</p>
        {!allowed ? <p className="mt-4 text-sm">Campaign media permission is required to upload; ask an authorized editor.</p>
            : !snapshot ? <p className="mt-4 text-sm">Loading branch content…</p> : <>
                <div className="mt-4 grid gap-4 sm:grid-cols-2">
                    <div><p className="text-sm font-semibold">Draft preview</p>
                        {(preview ?? snapshot.draftImageUrl) ? <Image unoptimized width={640} height={360} src={preview ?? snapshot.draftImageUrl!} alt={altText || "Draft branch cover"} className="mt-2 aspect-[16/9] w-full rounded-xl object-cover" />
                            : <div className="mt-2 grid aspect-[16/9] place-items-center rounded-xl bg-[#e8f0eb] text-sm">No draft image</div>}</div>
                    <div><p className="text-sm font-semibold">Published on customer pages · revision {snapshot.publishedRevision}</p>
                        {snapshot.publishedImageUrl ? <Image unoptimized width={640} height={360} src={snapshot.publishedImageUrl} alt={snapshot.publishedAltText ?? branch.name} className="mt-2 aspect-[16/9] w-full rounded-xl object-cover" />
                            : <div className="mt-2 grid aspect-[16/9] place-items-center rounded-xl bg-[#e8f0eb] text-sm">No published image</div>}</div>
                </div>
                <div className="mt-4 grid gap-3 sm:grid-cols-2">
                    <label className="text-sm font-semibold">Desktop cover<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => {setPreview(null); setDesktop(event.target.files?.[0] ?? null);}} className="mt-2 block w-full" /></label>
                    <label className="text-sm font-semibold">Optional phone cover<input type="file" accept="image/jpeg,image/png,image/webp" onChange={event => setMobile(event.target.files?.[0] ?? null)} className="mt-2 block w-full" /></label>
                    <label className="text-sm font-semibold">Image description for accessibility<input value={altText} maxLength={180} onChange={event => setAltText(event.target.value)} className="mt-2 block min-h-11 w-full rounded-xl border p-3" /></label>
                    <label className="text-sm font-semibold">About this branch<textarea value={description} maxLength={500} onChange={event => setDescription(event.target.value)} rows={3} className="mt-2 block w-full rounded-xl border p-3" /></label>
                </div>
                <div className="mt-4 flex flex-wrap gap-3"><button type="button" disabled={busy} onClick={() => void change("draft")} className="min-h-11 rounded-xl border px-4 font-semibold">Save draft</button>
                    <button type="button" disabled={busy || !snapshot.draftImageUrl || !snapshot.draftAltText} onClick={() => void change("publish")} className="min-h-11 rounded-xl bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50">Publish draft</button></div>
                {history.length > 0 && <div className="mt-5"><h4 className="font-semibold">Publication history</h4>{history.map(item => <div key={item.revision} className="mt-2 flex items-center justify-between border-t pt-2 text-sm">
                    <span>Revision {item.revision} · {item.altText}</span><button type="button" disabled={busy || item.revision === snapshot.publishedRevision} onClick={() => void change("restore", item.revision)} className="font-semibold text-[#7a1625] underline disabled:opacity-50">Restore</button></div>)}</div>}
            </>}
        {message && <p role="status" className="mt-3 text-sm text-green-800">{message}</p>}
        {error && <p role="alert" className="mt-3 text-sm text-red-800">{error}</p>}
    </section>;
}
