"use client";
import {T} from "@/lib/language";


import {useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {SettingField} from "@/components/admin/BranchOperationalSettings";
import {adminManagementApi} from "@/services/adminManagementApi";
import {toIndiaDateTimeInput, fromIndiaDateTimeInput} from "@/lib/campaigns";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getActiveBranches} from "@/services/branchApi";
import type {Branch} from "@/types/branch";
import type {HomepageCampaign, CampaignPublication} from "@/types/campaign";
import CampaignFramingEditor from "@/components/admin/CampaignFramingEditor";
import type {CampaignFrame} from "@/lib/campaignFraming";

const path = "/api/admin/homepage-campaigns";
const blank = {type: "HERO", title: "", subtitle: "", ctaLabel: "", ctaTarget: "",
    startAt: "", endAt: "", active: false, displayOrder: 0, altText: "", branchId: "",
    mainX: 50, mainY: 50, mainZoom: 100, mainFit: "COVER" as "COVER" | "CONTAIN",
    mobileX: 50, mobileY: 50, mobileZoom: 100, mobileFit: "COVER" as "COVER" | "CONTAIN"};
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
    const controlled = useStorefrontFeatures()?.controlledCampaignPublishing === true;
    const [campaigns, setCampaigns] = useState<HomepageCampaign[]>([]);
    const [branches, setBranches] = useState<Branch[]>([]);
    const [history, setHistory] = useState<CampaignPublication[]>([]);
    const [editing, setEditing] = useState<number | null>(null);
    const [form, setForm] = useState(blank);
    const [busy, setBusy] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [mainFile, setMainFile] = useState<File | null>(null);
    const [fallbackFile, setFallbackFile] = useState<File | null>(null);
    const [mobileFile, setMobileFile] = useState<File | null>(null);
    const inFlight = useRef(false);
    const persisted = useRef<HomepageCampaign | null>(null);
    const creationKey = useRef<string | null>(null);
    const uploadKeys = useRef(new WeakMap<File, string>());
    const uploaded = useRef<{main: File | null; fallback: File | null; mobile: File | null}>({main: null, fallback: null, mobile: null});
    const selected = campaigns.find(campaign => campaign.id === editing);
    const versionHeaders = (): Record<string, string> => controlled ? {"If-Match": String(persisted.current?.editVersion ?? 0)} : {};

    useEffect(() => {
        if (!authorization || !allowed) return;
        const controller = new AbortController();
        adminManagementApi<HomepageCampaign[]>(path, authorization, {signal: controller.signal})
            .then(data => {setCampaigns(data); setLoaded(true);})
            .catch(error => {if (!controller.signal.aborted) setError(error.message);});
        return () => controller.abort();
    }, [authorization, allowed]);

    useEffect(() => {
        if (!controlled || !authorization || !allowed) return;
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(setBranches).catch(error => {
            if (!controller.signal.aborted) setError(error.message);
        });
        return () => controller.abort();
    }, [authorization, allowed, controlled]);

    async function loadHistory(id: number) {
        if (!authorization || !controlled) return;
        try {setHistory(await adminManagementApi<CampaignPublication[]>(`${path}/${id}/publications`, authorization));}
        catch (error) {setError(error instanceof Error ? error.message : "Could not load publication history.");}
    }

    function accept(campaign: HomepageCampaign) {
        setCampaigns(current => [...current.filter(value => value.id !== campaign.id), campaign]);
    }

    function edit(campaign: HomepageCampaign) {
        persisted.current = campaign;
        uploaded.current = {main: null, fallback: null, mobile: null};
        setMainFile(null); setFallbackFile(null); setMobileFile(null);
        setEditing(campaign.id);
        setForm({type: campaign.type, title: campaign.title, subtitle: campaign.subtitle ?? "", ctaLabel: campaign.ctaLabel ?? "",
            ctaTarget: campaign.ctaTarget ?? "", startAt: toIndiaDateTimeInput(campaign.startAt), endAt: toIndiaDateTimeInput(campaign.endAt),
            active: campaign.active, displayOrder: campaign.displayOrder,
            altText: campaign.altText ?? "", branchId: campaign.branchId?.toString() ?? "",
            mainX: campaign.mainX ?? 50, mainY: campaign.mainY ?? 50, mainZoom: campaign.mainZoom ?? 100, mainFit: campaign.mainFit ?? "COVER",
            mobileX: campaign.mobileX ?? 50, mobileY: campaign.mobileY ?? 50, mobileZoom: campaign.mobileZoom ?? 100, mobileFit: campaign.mobileFit ?? "COVER"});
        setError(""); setMessage("");
        setHistory([]); void loadHistory(campaign.id);
    }

    async function save(publish: boolean) {
        if (!authorization || inFlight.current) return;
        inFlight.current = true;
        setBusy(true); setError(""); setMessage("");
        try {
            validateFile(mainFile, false); validateFile(fallbackFile, true); validateMobileFile(mobileFile);
            const type = mainFile?.type ?? persisted.current?.mediaType;
            const animated = type === "image/gif" || type?.startsWith("video/");
            if (publish && !mainFile && !persisted.current?.mediaUrl) throw new Error("Choose banner media before publishing, or save a draft without media.");
            if (publish && animated && !fallbackFile && !persisted.current?.fallbackMediaUrl) throw new Error("Choose a static fallback image for GIF/video before publishing.");
            if (controlled && publish && !form.altText.trim()) throw new Error("Add a short image description before publishing.");
            if (controlled && Boolean(form.ctaLabel.trim()) !== Boolean(form.ctaTarget)) throw new Error("Choose both a button label and destination, or neither.");
            const metadata = {...form, active: false, ctaLabel: form.ctaLabel || null, ctaTarget: form.ctaTarget || null,
                startAt: fromIndiaDateTimeInput(form.startAt), endAt: fromIndiaDateTimeInput(form.endAt),
                altText: controlled ? form.altText : null, branchId: controlled && form.branchId ? Number(form.branchId) : null};
            const retain = (value: HomepageCampaign) => {
                persisted.current = value; setEditing(value.id); accept(value);
            };
            setMessage("Saving draft...");
            const id = persisted.current?.id;
            creationKey.current ??= crypto.randomUUID();
            retain(await adminManagementApi<HomepageCampaign>(id ? `${path}/${id}` : path, authorization, {
                method: id ? "PUT" : "POST", headers: {"Content-Type": "application/json", "Idempotency-Key": creationKey.current,
                    ...(id ? versionHeaders() : {})}, body: JSON.stringify(metadata)
            }));
            // A successful step is retained immediately: retrying publication must not upload it again.
            for (const fallback of [true, false]) {
                const file = fallback ? fallbackFile : mainFile;
                const key = fallback ? "fallback" : "main";
                if (!file || uploaded.current[key] === file) continue;
                setMessage(fallback ? "Uploading static fallback..." : "Uploading banner...");
                const body = new FormData(); body.append("file", file);
                if (!uploadKeys.current.has(file)) uploadKeys.current.set(file, crypto.randomUUID());
                retain(await adminManagementApi<HomepageCampaign>(`${path}/${persisted.current!.id}/media?fallback=${fallback}`,
                    authorization, {method: "POST", body, headers: {"Idempotency-Key": uploadKeys.current.get(file)!, ...versionHeaders()}}));
                uploaded.current[key] = file;
            }
            if (controlled && mobileFile && uploaded.current.mobile !== mobileFile) {
                setMessage("Uploading mobile media...");
                const body = new FormData(); body.append("file", mobileFile);
                if (!uploadKeys.current.has(mobileFile)) uploadKeys.current.set(mobileFile, crypto.randomUUID());
                retain(await adminManagementApi<HomepageCampaign>(`${path}/${persisted.current!.id}/mobile-media`, authorization,
                    {method: "POST", body, headers: {"Idempotency-Key": uploadKeys.current.get(mobileFile)!, ...versionHeaders()}}));
                uploaded.current.mobile = mobileFile;
            }
            if (publish) {
                setMessage("Publishing campaign...");
                retain(await adminManagementApi<HomepageCampaign>(`${path}/${persisted.current!.id}`, authorization, {
                    method: "PUT", headers: {"Content-Type": "application/json", ...versionHeaders()}, body: JSON.stringify({...metadata, active: true})
                }));
            }
            setForm(current => ({...current, active: publish}));
            if (controlled && publish && persisted.current?.id) void loadHistory(persisted.current.id);
            setMessage(publish ? "Campaign published. Its schedule and storefront flags control when it appears." : "Draft saved. It is not visible to customers.");
        } catch (error) {
            setMessage(persisted.current ? "Completed steps are saved. Your fields and chosen files are kept for retry." : "");
            setError(error instanceof Error ? error.message : "Unable to save campaign.");
        }
        finally {inFlight.current = false; setBusy(false);}
    }

    async function restore(revision: number) {
        if (!controlled || !authorization || !editing || inFlight.current) return;
        inFlight.current = true; setBusy(true); setError(""); setMessage("");
        try {
            const campaign = await adminManagementApi<HomepageCampaign>(`${path}/${editing}/publications/${revision}/restore`,
                authorization, {method: "POST", headers: versionHeaders()});
            accept(campaign); persisted.current = campaign;
            setMessage("Earlier publication restored. Your current draft remains available for editing.");
        } catch (error) {setError(error instanceof Error ? error.message : "Could not restore this publication.");}
        finally {inFlight.current = false; setBusy(false);}
    }

    async function removeMobile() {
        if (!controlled || !authorization || !editing || inFlight.current) return;
        inFlight.current = true; setBusy(true); setError("");
        try {
            const campaign = await adminManagementApi<HomepageCampaign>(`${path}/${editing}/mobile-media`, authorization, {method: "DELETE", headers: versionHeaders()});
            accept(campaign); persisted.current = campaign; setMobileFile(null); uploaded.current.mobile = null;
            setMessage("Mobile draft media removed. Any published version stays available until you publish again.");
        } catch (error) {setError(error instanceof Error ? error.message : "Could not remove the mobile media.");}
        finally {inFlight.current = false; setBusy(false);}
    }

    async function media(file: File | null, fallback: boolean, remove = false) {
        if (!authorization || !editing || inFlight.current || (!file && !remove)) return;
        inFlight.current = true;
        setBusy(true); setError(""); setMessage("");
        try {
            validateFile(file, fallback);
            const body = new FormData();
            if (file) body.append("file", file);
            const campaign = await adminManagementApi<HomepageCampaign>(`${path}/${editing}/media?fallback=${fallback}`, authorization,
                remove ? {method: "DELETE", headers: versionHeaders()} : {method: "POST", body, headers: versionHeaders()});
            accept(campaign); persisted.current = campaign; setForm(current => ({...current, active: campaign.active}));
            if (fallback) {setFallbackFile(null); uploaded.current.fallback = null;}
            else {setMainFile(null); uploaded.current.main = null;}
            setMessage(remove ? (controlled ? "Draft media removed. The last published version stays live." : "Media removed. A campaign without required media is deactivated.") : "Media uploaded. Save any other form changes separately.");
        } catch (error) {setError(error instanceof Error ? error.message : "Unable to update campaign media.");}
        finally {inFlight.current = false; setBusy(false);}
    }

    if (!allowed) return <p>You do not have permission to manage homepage campaigns.</p>;
    return <main className="space-y-6">
        <h1 className="text-2xl font-bold text-[#7a1625]"><T text="Homepage campaigns" /></h1>
        <p className="text-sm text-[#756763]">Choose your artwork and preview it here. Save privately or publish when ready. Files upload only when you save or publish.</p>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        {message && <p role="status" className="text-green-800">{message}</p>}
        <form onSubmit={event => {event.preventDefault(); void save(false);}} className="rounded-2xl border border-[#eadfd6] bg-white p-5">
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
                        <option value="">No button</option><option value="/menu"><T text="Menu" /></option><option value="/cart"><T text="Cart" /></option><option value="/about">About us</option>
                    </select>
                </SettingField>
                {controlled && <>
                    <SettingField label="Image description" help="Describe what customers see; required before publishing." htmlFor="campaign-alt">
                        <input id="campaign-alt" maxLength={180} value={form.altText} onChange={event => setForm({...form, altText: event.target.value})}
                            className="min-h-11 w-full rounded-xl border px-3" />
                    </SettingField>
                    <SettingField label="Shop" help="Choose a shop, or show the campaign at every shop." htmlFor="campaign-branch">
                        <select id="campaign-branch" value={form.branchId} onChange={event => setForm({...form, branchId: event.target.value})}
                            className="min-h-11 w-full rounded-xl border px-3">
                            <option value="">Every shop</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
                        </select>
                    </SettingField>
                </>}
                <div className="sm:col-span-2 grid gap-5 lg:grid-cols-2">
                    <div className="space-y-4">
                        <UploadCard id="media" label="Desktop hero image or video" hint="JPG, PNG, WebP or GIF up to 5 MB · MP4 or WebM up to 50 MB"
                            accept="image/jpeg,image/png,image/webp,image/gif,video/mp4,video/webm" file={mainFile} saved={Boolean(selected?.mediaUrl)}
                            onFile={file => {validateFile(file, false); setMainFile(file); setForm(current => ({...current, mainX: 50, mainY: 50, mainZoom: 100, mainFit: "COVER"}));}} onError={setError} />
                        <CampaignFramingEditor label="Desktop framing" file={mainFile} savedUrl={selected?.mediaUrl} mediaType={selected?.mediaType}
                            portrait={false} frame={{x: form.mainX, y: form.mainY, zoom: form.mainZoom, fit: form.mainFit}}
                            onChange={(frame: CampaignFrame) => setForm(current => ({...current, mainX: frame.x, mainY: frame.y, mainZoom: frame.zoom, mainFit: frame.fit}))} />
                        {selected?.mediaUrl && <button type="button" onClick={() => media(null, false, true)} className="min-h-11 text-red-700 underline">Remove saved desktop media</button>}
                    </div>
                    {controlled && <div className="space-y-4">
                        <UploadCard id="campaign-mobile" label="Phone hero image or video" hint="Portrait 9:16 recommended · images up to 5 MB · MP4/WebM up to 50 MB"
                            accept="image/jpeg,image/png,image/webp,video/mp4,video/webm" file={mobileFile} saved={Boolean(selected?.mobileMediaUrl)}
                            onFile={file => {validateMobileFile(file); setMobileFile(file); setForm(current => ({...current, mobileX: 50, mobileY: 50, mobileZoom: 100, mobileFit: "COVER"}));}} onError={setError} />
                        <CampaignFramingEditor label="Phone framing" file={mobileFile} savedUrl={selected?.mobileMediaUrl} mediaType={selected?.mobileMediaType ?? "image/png"}
                            portrait frame={{x: form.mobileX, y: form.mobileY, zoom: form.mobileZoom, fit: form.mobileFit}}
                            onChange={(frame: CampaignFrame) => setForm(current => ({...current, mobileX: frame.x, mobileY: frame.y, mobileZoom: frame.zoom, mobileFit: frame.fit}))} />
                        {selected?.mobileMediaUrl && <button type="button" onClick={() => void removeMobile()} className="min-h-11 text-red-700 underline">Remove saved phone media</button>}
                    </div>}
                    <div className="lg:col-span-2 rounded-xl border border-[#dce8e2] bg-[#f5faf7] p-4">
                        <UploadCard id="poster" label="Static fallback image" hint="Required for videos and GIFs · JPG, PNG or WebP up to 5 MB · shown when motion is reduced"
                            accept="image/jpeg,image/png,image/webp" file={fallbackFile} saved={Boolean(selected?.fallbackMediaUrl)}
                            onFile={file => {validateFile(file, true); setFallbackFile(file);}} onError={setError} />
                        <LocalPreview file={fallbackFile} savedUrl={selected?.fallbackMediaUrl} type="image/png" />
                        {selected?.fallbackMediaUrl && <button type="button" onClick={() => media(null, true, true)} className="min-h-11 text-red-700 underline">Remove saved fallback</button>}
                    </div>
                </div>
                <div className="flex gap-3">
                    <button className="min-h-11 rounded-xl border px-5 font-bold">Save draft</button>
                    <button type="button" onClick={event => {if (event.currentTarget.form?.reportValidity()) void save(true);}}
                        className="min-h-11 rounded-xl bg-[#7a1625] px-5 font-bold text-white">{form.active ? "Save & publish" : "Publish campaign"}</button>
                    {editing && <button type="button" className="min-h-11 px-3" onClick={() => {
                        setEditing(null); setForm(blank); persisted.current = null; creationKey.current = null; uploaded.current = {main: null, fallback: null, mobile: null};
                        setMainFile(null); setFallbackFile(null); setMobileFile(null); setHistory([]); setMessage(""); setError("");
                    }}>New draft</button>}
                </div>
                <p className="text-xs text-[#756763]">{controlled ? "Saving a draft keeps your last published version live. Publishing switches to the completed draft together. Failed steps can be retried." : "Saving a draft unpublishes it. If an upload or publishing step fails, completed steps are kept for retry. Unchanged files are not uploaded again."}</p>
            </fieldset>
        </form>
        {controlled && selected && <section className="rounded-2xl border bg-white p-5" aria-label="Publication history">
            <h2 className="font-bold">Preview and earlier versions</h2>
            <p className="text-sm text-[#756763]">Use the desktop and phone framing previews above before publishing. Earlier versions remain available for restore.</p>
            <ul className="mt-4 space-y-2">{history.map(version => <li key={version.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <span>Version {version.id} · {new Date(version.publishedAt).toLocaleString("en-IN", {timeZone: "Asia/Kolkata"})} IST · {version.title}</span>
                <button type="button" disabled={busy || selected.publishedRevision === version.id} onClick={() => void restore(version.id)}
                    className="min-h-11 rounded-lg border px-3 disabled:opacity-50">{selected.publishedRevision === version.id ? "Live" : "Restore"}</button>
            </li>)}</ul>
        </section>}
        {!loaded && !error && <p role="status">Loading campaigns...</p>}
        {loaded && !campaigns.length && <p>No campaigns yet. The storefront uses its default content.</p>}
        <div className="space-y-3">{[...campaigns].sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id).map(campaign => <article key={campaign.id}
            className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-[#eadfd6] bg-white p-4">
            <div><h2 className="font-bold">{campaign.title}</h2><p className="text-sm">{campaign.type} · Priority {campaign.displayOrder} · {campaign.active ? "Active (schedule applies)" : "Inactive"}</p>
                <p className="text-xs text-[#756763]">{toIndiaDateTimeInput(campaign.startAt) || "Immediate"} to {toIndiaDateTimeInput(campaign.endAt) || "No end"} (India time)</p></div>
            <button disabled={busy} onClick={() => edit(campaign)} className="min-h-11 rounded-xl border px-4"><T text="Edit" /></button>
        </article>)}</div>
    </main>;
}

function validateFile(file: File | null, fallback: boolean) {
    const video = !fallback && (file?.type === "video/mp4" || file?.type === "video/webm");
    if (file && (file.size > (video ? 50 : 5) * 1024 * 1024 || !["image/jpeg", "image/png", "image/webp", ...(!fallback ? ["image/gif", "video/mp4", "video/webm"] : [])].includes(file.type))) {
        throw new Error("Choose an image up to 5 MB or MP4/WebM video up to 50 MB.");
    }
}

function validateMobileFile(file: File | null) {
    if (!file) return;
    const video = file.type === "video/mp4" || file.type === "video/webm";
    const image = ["image/jpeg", "image/png", "image/webp"].includes(file.type);
    if ((!video && !image) || file.size > (video ? 50 : 5) * 1024 * 1024) {
        throw new Error("Choose a mobile image up to 5 MB or MP4/WebM video up to 50 MB.");
    }
}

function UploadCard({id, label, hint, accept, file, saved, onFile, onError}: {
    id: string; label: string; hint: string; accept: string; file: File | null; saved: boolean;
    onFile: (file: File) => void; onError: (message: string) => void;
}) {
    const choose = (picked: File | null) => {
        if (!picked) return;
        try {onFile(picked); onError("");}
        catch (error) {onError(error instanceof Error ? error.message : "Unsupported file.");}
    };
    return <div className="rounded-2xl border-2 border-dashed border-[#aac6bb] bg-[#f7fbf8] p-5 transition hover:border-[#4c8c7a] hover:bg-[#edf7f1]">
        <input id={id} type="file" accept={accept} className="sr-only" onChange={event => {
            choose(event.target.files?.[0] ?? null); event.target.value = "";
        }} />
        <label htmlFor={id} className="flex min-h-32 cursor-pointer flex-col items-center justify-center gap-2 text-center text-[#173a37]">
            <span aria-hidden="true" className="grid h-11 w-11 place-items-center rounded-full bg-[#dceee5] text-2xl">↑</span>
            <strong className="text-base">{label}</strong>
            <span className="rounded-full bg-[#143936] px-4 py-2 text-sm font-semibold text-white">Choose file</span>
            <span className="text-xs leading-5 text-[#526e65]">{hint}</span>
        </label>
        <div onDragOver={event => event.preventDefault()} onDrop={event => {event.preventDefault(); choose(event.dataTransfer.files[0] ?? null);}}
            className="mt-3 min-h-11 rounded-xl bg-white p-3 text-center text-xs text-[#526e65]">
            {file ? `${file.name} · ${(file.size / 1024 / 1024).toFixed(1)} MB selected` : saved ? "Saved media · choose or drop a replacement" : "Drop a file here or use Choose file"}
        </div>
    </div>;
}

function LocalPreview({file, savedUrl, type, portrait = false}: {file: File | null; savedUrl?: string | null; type?: string | null; portrait?: boolean}) {
    const [local, setLocal] = useState<{file: File; url: string} | null>(null);
    useEffect(() => {
        if (!file) return;
        const url = URL.createObjectURL(file);
        let active = true;
        queueMicrotask(() => {if (active) setLocal({file, url});});
        return () => {active = false; URL.revokeObjectURL(url);};
    }, [file]);
    const url = file ? local?.file === file ? local.url : null : savedUrl;
    if (!url) return null;
    return (file?.type ?? type)?.startsWith("video/")
        ? <video src={url} muted loop playsInline autoPlay preload="metadata" className={`mt-3 max-h-48 rounded-xl object-cover ${portrait ? "aspect-[9/16] w-full" : ""}`} aria-label="Banner preview" />
        // Local object URLs never send preview bytes to R2.
        // eslint-disable-next-line @next/next/no-img-element
        : <img src={url} alt="Campaign preview" className={`mt-3 max-h-48 rounded-xl ${portrait ? "aspect-[9/16] w-full object-cover" : "object-contain"}`} />;
}
