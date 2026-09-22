"use client";

import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {SettingField, SettingToggle} from "@/components/admin/BranchOperationalSettings";
import {adminManagementApi} from "@/services/adminManagementApi";
import {toIndiaDateTimeInput, fromIndiaDateTimeInput} from "@/lib/campaigns";
import type {HomepageCampaign} from "@/types/campaign";

const path = "/api/admin/homepage-campaigns";
const blank = {type: "HERO", title: "", subtitle: "", ctaLabel: "", ctaTarget: "",
    startAt: "", endAt: "", active: false, displayOrder: 0};
const fields = [
    ["title", "Title", "Short campaign heading shown to customers.", "text", 120],
    ["subtitle", "Supporting text", "Optional concise description; keep ordering prominent.", "text", 240],
    ["ctaLabel", "Button label", "For example Explore sweets. Also choose a destination.", "text", 60],
    ["startAt", "Starts at (India time)", "Leave blank to start as soon as the campaign is active.", "datetime-local", 0],
    ["endAt", "Ends at (India time)", "At this time the next active campaign or default content appears. Blank means no scheduled end.", "datetime-local", 0],
    ["displayOrder", "Display priority", "Lower numbers appear first. The campaign ID breaks ties.", "number", 0]
] as const;

export default function HomepageCampaignsPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const allowed = hasPermission("MENU_MANAGE");
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [editing, setEditing] = useState<number | null>(null);
    const [form, setForm] = useState(blank);
    const [busy, setBusy] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const selected = campaigns.find(campaign => campaign.id === editing);

    useEffect(() => {
        if (!authorization || !allowed) return;
        const controller = new AbortController();
        adminManagementApi<HomepageCampaign[]>(path, authorization, {signal: controller.signal})
            .then(data => {setCampaigns(data); setLoaded(true);})
            .catch(error => {if (!controller.signal.aborted) setError(error.message);});
        return () => controller.abort();
    }, [authorization, allowed]);

    function accept(campaign: HomepageCampaign) {
        setCampaigns(current => [...current.filter(value => value.id !== campaign.id), campaign]);
    }

    function edit(campaign: HomepageCampaign) {
        setEditing(campaign.id);
        setForm({type: campaign.type, title: campaign.title, subtitle: campaign.subtitle ?? "", ctaLabel: campaign.ctaLabel ?? "",
            ctaTarget: campaign.ctaTarget ?? "", startAt: toIndiaDateTimeInput(campaign.startAt), endAt: toIndiaDateTimeInput(campaign.endAt),
            active: campaign.active, displayOrder: campaign.displayOrder});
        setError(""); setMessage("");
    }

    async function save(event: React.FormEvent) {
        event.preventDefault();
        if (!authorization) return;
        setBusy(true); setError(""); setMessage("");
        try {
            const campaign = await adminManagementApi<HomepageCampaign>(editing ? `${path}/${editing}` : path, authorization, {
                method: editing ? "PUT" : "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({...form, ctaLabel: form.ctaLabel || null, ctaTarget: form.ctaTarget || null,
                    startAt: fromIndiaDateTimeInput(form.startAt), endAt: fromIndiaDateTimeInput(form.endAt)})
            });
            accept(campaign); edit(campaign); setMessage("Campaign saved. Public visibility still depends on feature flags and its schedule.");
        } catch (error) {setError(error instanceof Error ? error.message : "Unable to save campaign.");}
        finally {setBusy(false);}
    }

    async function media(file: File | null, fallback: boolean, remove = false) {
        if (!authorization || !editing || (!file && !remove)) return;
        setBusy(true); setError(""); setMessage("");
        try {
            if (file && (file.size > 5 * 1024 * 1024 || !["image/jpeg", "image/png", "image/webp", ...(!fallback ? ["image/gif", "video/mp4", "video/webm"] : [])].includes(file.type))) {
                throw new Error("Choose a supported file of 5 MB or smaller.");
            }
            const body = new FormData();
            if (file) body.append("file", file);
            const campaign = await adminManagementApi<HomepageCampaign>(`${path}/${editing}/media?fallback=${fallback}`, authorization,
                remove ? {method: "DELETE"} : {method: "POST", body});
            accept(campaign); setForm(current => ({...current, active: campaign.active}));
            setMessage(remove ? "Media removed. A campaign without required media is deactivated." : "Media uploaded. Save any other form changes separately.");
        } catch (error) {setError(error instanceof Error ? error.message : "Unable to update campaign media.");}
        finally {setBusy(false);}
    }

    if (!allowed) return <p>You do not have permission to manage homepage campaigns.</p>;
    return <main className="space-y-6">
        <h1 className="text-2xl font-bold text-[#7a1625]">Homepage campaigns</h1>
        <p className="text-sm text-[#756763]">Create a draft, upload media, then activate and schedule it. Global campaigns appear only when the new homepage and campaigns are enabled.</p>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        {message && <p role="status" className="text-green-800">{message}</p>}
        <form onSubmit={save} className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <h2 className="mb-4 font-bold">{editing ? "Edit campaign" : "New draft"}</h2>
            <fieldset disabled={busy} className="grid gap-5 sm:grid-cols-2">
                <SettingField label="Placement" help="Hero is the main banner. Feature appears below the menu highlights." htmlFor="campaign-type">
                    <select id="campaign-type" value={form.type} onChange={event => setForm({...form, type: event.target.value})} className="min-h-11 w-full rounded-xl border px-3">
                        <option value="HERO">Hero</option><option value="FEATURE">Feature / special</option>
                    </select>
                </SettingField>
                {fields.map(([key, label, help, type, limit]) => <SettingField key={key} label={label} help={help} htmlFor={`campaign-${key}`}>
                    <input id={`campaign-${key}`} type={type} required={key === "title" || key === "displayOrder"}
                        maxLength={limit || undefined} min={type === "number" ? 0 : undefined} max={type === "number" ? 10000 : undefined}
                        value={form[key]} onChange={event => setForm({...form, [key]: type === "number" ? Number(event.target.value) : event.target.value})}
                        className="min-h-11 w-full rounded-xl border px-3" />
                </SettingField>)}
                <SettingField label="Button destination" help="Send customers to an existing page. External links are not supported." htmlFor="campaign-target">
                    <select id="campaign-target" value={form.ctaTarget} onChange={event => setForm({...form, ctaTarget: event.target.value})} className="min-h-11 w-full rounded-xl border px-3">
                        <option value="">No button</option><option value="/menu">Menu</option><option value="/cart">Cart</option><option value="/about">About us</option>
                    </select>
                </SettingField>
                <SettingToggle label="Active" description="Show during the scheduled period. Upload required media first; GIF/video also needs a static fallback."
                    checked={form.active} disabled={busy} onChange={active => setForm({...form, active})} />
                <div className="flex gap-3">
                    <button className="min-h-11 rounded-xl bg-[#7a1625] px-5 font-bold text-white">{busy ? "Saving..." : "Save campaign"}</button>
                    {editing && <button type="button" className="min-h-11 px-3" onClick={() => {setEditing(null); setForm(blank);}}>New draft</button>}
                </div>
            </fieldset>
        </form>
        {selected && <section className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <h2 className="mb-4 font-bold">Campaign media</h2>
            <div className="grid gap-5 sm:grid-cols-2">{([false, true] as const).map(fallback => <div key={String(fallback)}>
                <SettingField label={fallback ? "Static fallback" : "Image or video"} htmlFor={fallback ? "poster" : "media"}
                    help={fallback ? "JPG, PNG or static WebP, up to 5 MB. Used for reduced motion and playback errors."
                        : "JPG, PNG, WebP, GIF, MP4 or WebM, up to 5 MB. Use a short silent video; keep artwork readable on phones."}>
                    <input key={`${selected.id}-${fallback}`} id={fallback ? "poster" : "media"} type="file" disabled={busy}
                        accept={fallback ? "image/jpeg,image/png,image/webp" : "image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm"}
                        className="min-h-11 w-full py-2" onChange={event => {void media(event.target.files?.[0] ?? null, fallback); event.target.value = "";}} />
                </SettingField>
                {(fallback ? selected.fallbackMediaUrl : selected.mediaUrl) && <div className="flex gap-3">
                    <a className="min-h-11 py-3 underline" href={(fallback ? selected.fallbackMediaUrl : selected.mediaUrl)!} target="_blank" rel="noreferrer">Preview media</a>
                    <button disabled={busy} onClick={() => media(null, fallback, true)} className="min-h-11 px-3 text-red-700 underline">Remove</button>
                </div>}
            </div>)}</div>
        </section>}
        {!loaded && !error && <p role="status">Loading campaigns...</p>}
        {loaded && !campaigns.length && <p>No campaigns yet. The storefront uses its default content.</p>}
        <div className="space-y-3">{[...campaigns].sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id).map(campaign => <article key={campaign.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#eadfd6] bg-white p-4">
            <div><h2 className="font-bold">{campaign.title}</h2><p className="text-sm">{campaign.type} · Priority {campaign.displayOrder} · {campaign.active ? "Active (schedule applies)" : "Inactive"}</p>
                <p className="text-xs text-[#756763]">{toIndiaDateTimeInput(campaign.startAt) || "Immediate"} to {toIndiaDateTimeInput(campaign.endAt) || "No end"} (India time)</p></div>
            <button disabled={busy} onClick={() => edit(campaign)} className="min-h-11 rounded-xl border px-4">Edit</button>
        </article>)}</div>
    </main>;
}
