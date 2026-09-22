"use client";

import {useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {SettingField} from "@/components/admin/BranchOperationalSettings";
import {adminManagementApi} from "@/services/adminManagementApi";
import {getActiveBranches} from "@/services/branchApi";
import type {Branch} from "@/types/branch";
import type {RebateScope, RebateType} from "@/types/rebate";

interface Offer {
    id: number; code: string; name: string; description: string | null;
    scope: RebateScope; visibility: "PUBLIC" | "CODE_ONLY"; rebateType: RebateType;
    rebateValue: number | null; minimumOrderAmount: number | null; maximumDiscountAmount: number | null;
    maxTotalUses: number | null; maxUsesPerCustomer: number | null;
    branchId: number | null; branchName: string | null; validFrom: string; validUntil: string; active: boolean;
    customerPhones: string[]; slabs: {minimumOrderAmount: number; rebateAmount: number}[];
}
const empty = {
    code: "", name: "", description: "", scope: "GENERAL" as RebateScope,
    visibility: "PUBLIC" as Offer["visibility"], rebateType: "PERCENTAGE" as RebateType,
    rebateValue: "", minimumOrderAmount: "", maximumDiscountAmount: "", maxTotalUses: "", maxUsesPerCustomer: "",
    branchId: "", validFrom: "", validUntil: "", customerPhones: "",
    slabs: [{minimumOrderAmount: "", rebateAmount: ""}]
};
const path = "/api/admin/rebates";
const inputClass = "min-h-11 w-full rounded-xl border border-[#eadfd6] bg-white px-3";
const optionalNumber = (value: string) => value === "" ? null : Number(value);

export default function OffersPage() {
    const {authorization, profile, hasPermission} = useAdminAuth();
    const canView = hasPermission("REBATE_VIEW"), canManage = hasPermission("REBATE_MANAGE");
    const owner = profile?.roleName === "OWNER_ADMIN";
    const [offers, setOffers] = useState<Offer[]>([]);
    const [branches, setBranches] = useState<Branch[]>([]);
    const [form, setForm] = useState(empty);
    const [editing, setEditing] = useState<number | null>(null);
    const [busy, setBusy] = useState(false);
    const inFlight = useRef(false);
    const [loaded, setLoaded] = useState(false);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [reload, setReload] = useState(0);
    const permittedBranches = branches.filter(branch => owner || profile?.branchIds.includes(branch.id));

    useEffect(() => {
        if (!authorization || (!canView && !canManage)) return;
        const controller = new AbortController();
        getActiveBranches(controller.signal).then(setBranches).catch(error => {
            if (!controller.signal.aborted) setError(error.message);
        });
        if (canView) adminManagementApi<Offer[]>(path, authorization, {signal: controller.signal})
            .then(items => {setOffers(items); setLoaded(true);})
            .catch(error => {if (!controller.signal.aborted) setError(error.message);});
        return () => controller.abort();
    }, [authorization, canView, canManage, reload]);

    function edit(offer: Offer) {
        setEditing(offer.id); setMessage(""); setError("");
        setForm({
            code: offer.code, name: offer.name, description: offer.description ?? "", scope: offer.scope,
            visibility: offer.visibility, rebateType: offer.rebateType,
            rebateValue: String(offer.rebateValue ?? ""), minimumOrderAmount: String(offer.minimumOrderAmount ?? ""),
            maximumDiscountAmount: String(offer.maximumDiscountAmount ?? ""), maxTotalUses: String(offer.maxTotalUses ?? ""),
            maxUsesPerCustomer: String(offer.maxUsesPerCustomer ?? ""), branchId: offer.branchId === null ? "all" : String(offer.branchId),
            validFrom: offer.validFrom.slice(0, 16), validUntil: offer.validUntil.slice(0, 16),
            customerPhones: (offer.customerPhones ?? []).join("\n"),
            slabs: offer.slabs?.length ? offer.slabs.map(slab => ({
                minimumOrderAmount: String(slab.minimumOrderAmount), rebateAmount: String(slab.rebateAmount)
            })) : empty.slabs
        });
        document.getElementById("offer-form")?.scrollIntoView({block: "start"});
    }

    async function save(event: React.FormEvent) {
        event.preventDefault();
        if (!authorization || inFlight.current) return;
        if (form.validUntil <= form.validFrom) {setError("End time must be after start time (India time)."); return;}
        inFlight.current = true; setBusy(true); setError(""); setMessage("");
        try {
            const slab = form.rebateType === "SLAB";
            const saved = await adminManagementApi<Offer>(editing ? `${path}/${editing}` : path, authorization, {
                method: editing ? "PUT" : "POST", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    ...form, branchId: form.branchId === "all" ? null : Number(form.branchId),
                    rebateValue: slab ? null : optionalNumber(form.rebateValue),
                    minimumOrderAmount: slab ? null : optionalNumber(form.minimumOrderAmount),
                    maximumDiscountAmount: form.rebateType === "PERCENTAGE" ? optionalNumber(form.maximumDiscountAmount) : null,
                    maxTotalUses: optionalNumber(form.maxTotalUses), maxUsesPerCustomer: optionalNumber(form.maxUsesPerCustomer),
                    customerPhones: form.scope === "CUSTOMER" ? [...new Set(form.customerPhones.split(/[\s,]+/).filter(Boolean))] : [],
                    slabs: slab ? form.slabs.map(value => ({
                        minimumOrderAmount: Number(value.minimumOrderAmount), rebateAmount: Number(value.rebateAmount)
                    })) : []
                })
            });
            setOffers(current => [saved, ...current.filter(value => value.id !== saved.id)]);
            setMessage(editing ? "Offer updated. Existing eligibility rules still apply." : "Offer created and enabled for its scheduled dates.");
            setEditing(null); setForm(empty);
        } catch (error) {
            setError(`${error instanceof Error ? error.message : "We couldn't save this offer."} Your entries are preserved. If the connection was lost, refresh the offer list before retrying the same unique code.`);
        } finally {inFlight.current = false; setBusy(false);}
    }

    async function toggle(offer: Offer) {
        if (!authorization || inFlight.current) return;
        inFlight.current = true; setBusy(true); setError(""); setMessage("");
        try {
            const saved = await adminManagementApi<Offer>(`${path}/${offer.id}/${offer.active ? "deactivate" : "activate"}`, authorization, {method: "PATCH"});
            setOffers(current => current.map(value => value.id === saved.id ? saved : value));
            setMessage(saved.active ? "Offer enabled. Schedule and eligibility still apply." : "Offer disabled.");
        } catch (error) {setError(error instanceof Error ? error.message : "Unable to change offer status.");}
        finally {inFlight.current = false; setBusy(false);}
    }

    if (!canView && !canManage) return <p>You do not have permission to view or manage offers.</p>;
    return <main className="space-y-6 p-4 sm:p-6">
        <div><h1 className="text-2xl font-bold text-[#7a1625]">Offers</h1>
            <p className="mt-2 text-sm text-[#756763]">Create discounts for the existing checkout offer system. Publishing an offer does not send a notification or change product taxes.</p></div>
        {error && <div role="alert" className="rounded-xl bg-red-50 p-4 text-red-800">{error}
            <button disabled={busy} className="ml-3 min-h-11 underline" onClick={() => {setError(""); setReload(value => value + 1);}}>Refresh offers</button></div>}
        {message && <p role="status" className="text-green-800">{message}</p>}
        {canManage && <form id="offer-form" onSubmit={save} className="scroll-mt-24 rounded-2xl border border-[#eadfd6] bg-white p-5">
            <h2 className="mb-4 font-bold">{editing ? "Edit offer" : "Create offer"}</h2>
            <fieldset disabled={busy} className="grid gap-5 sm:grid-cols-2">
                <SettingField label="Offer code" help="Unique code customers can enter at checkout." htmlFor="offer-code">
                    <input id="offer-code" required maxLength={50} value={form.code} onChange={e => setForm({...form, code: e.target.value})} className={inputClass} />
                </SettingField>
                <SettingField label="Offer name" help="Short customer-facing name." htmlFor="offer-name">
                    <input id="offer-name" required maxLength={150} value={form.name} onChange={e => setForm({...form, name: e.target.value})} className={inputClass} />
                </SettingField>
                <SettingField label="Description" help="Explain the offer without promising eligibility for every order." htmlFor="offer-description">
                    <textarea id="offer-description" maxLength={500} value={form.description} onChange={e => setForm({...form, description: e.target.value})} className={inputClass} />
                </SettingField>
                <SettingField label="Branch" help="Only an owner can create an all-branches offer." htmlFor="offer-branch">
                    <select id="offer-branch" required value={form.branchId} onChange={e => setForm({...form, branchId: e.target.value})} className={inputClass}>
                        <option value="">Choose branch</option>{owner && <option value="all">All branches</option>}
                        {permittedBranches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
                        {editing && form.branchId !== "all" && !permittedBranches.some(branch => String(branch.id) === form.branchId)
                            && <option value={form.branchId}>Existing branch #{form.branchId}</option>}
                    </select>
                </SettingField>
                <SettingField label="Discount type" help="Amounts are INR. Slabs use the highest eligible spend threshold." htmlFor="offer-type">
                    <select id="offer-type" value={form.rebateType} onChange={e => setForm({...form, rebateType: e.target.value as RebateType})} className={inputClass}>
                        <option value="PERCENTAGE">Percentage</option><option value="FIXED_AMOUNT">Fixed amount</option><option value="SLAB">Spend-based slabs</option>
                    </select>
                </SettingField>
                {form.rebateType !== "SLAB" && <>
                    <SettingField label={form.rebateType === "PERCENTAGE" ? "Discount (%)" : "Discount (INR)"} help="Positive discount; final checkout enforces eligibility." htmlFor="offer-value">
                        <input id="offer-value" type="number" required min="0.01" max={form.rebateType === "PERCENTAGE" ? 100 : undefined} step="0.01" value={form.rebateValue} onChange={e => setForm({...form, rebateValue: e.target.value})} className={inputClass} />
                    </SettingField>
                    <SettingField label="Minimum eligible spend (INR)" help="Optional threshold, using the existing checkout discount basis." htmlFor="offer-minimum">
                        <input id="offer-minimum" type="number" min="0" step="0.01" value={form.minimumOrderAmount} onChange={e => setForm({...form, minimumOrderAmount: e.target.value})} className={inputClass} />
                    </SettingField>
                    {form.rebateType === "PERCENTAGE" && <SettingField label="Maximum discount (INR)" help="Required cap on percentage offers." htmlFor="offer-cap">
                        <input id="offer-cap" type="number" required min="0.01" step="0.01" value={form.maximumDiscountAmount} onChange={e => setForm({...form, maximumDiscountAmount: e.target.value})} className={inputClass} />
                    </SettingField>}
                </>}
                {form.rebateType === "SLAB" && <div className="space-y-3 sm:col-span-2">
                    <p className="text-sm">Each discount must be smaller than its spend threshold. Thresholds must be unique.</p>
                    {form.slabs.map((slab, index) => <div key={index} className="flex flex-wrap gap-2">
                        {(["minimumOrderAmount", "rebateAmount"] as const).map(key => <label key={key} className="text-sm">
                            {key === "minimumOrderAmount" ? "Minimum spend" : "Discount"} {index + 1} (INR)
                            <input type="number" required min="0.01" step="0.01" value={slab[key]} className={inputClass}
                                onChange={e => setForm({...form, slabs: form.slabs.map((value, i) => i === index ? {...value, [key]: e.target.value} : value)})} />
                        </label>)}
                        <button type="button" disabled={form.slabs.length === 1} className="min-h-11 px-3 underline" onClick={() => setForm({...form, slabs: form.slabs.filter((_, i) => i !== index)})}>Remove slab {index + 1}</button>
                    </div>)}
                    <button type="button" className="min-h-11 underline" onClick={() => setForm({...form, slabs: [...form.slabs, {minimumOrderAmount: "", rebateAmount: ""}]})}>Add slab</button>
                </div>}
                <SettingField label="Visibility" help="Public offers appear when eligible; code-only offers require entering the code." htmlFor="offer-visibility">
                    <select id="offer-visibility" value={form.visibility} onChange={e => setForm({...form, visibility: e.target.value as Offer["visibility"]})} className={inputClass}>
                        <option value="PUBLIC">Public</option><option value="CODE_ONLY">Code only</option>
                    </select>
                </SettingField>
                <SettingField label="Audience" help="Customer-specific offers match the checkout phone number." htmlFor="offer-scope">
                    <select id="offer-scope" value={form.scope} onChange={e => setForm({...form, scope: e.target.value as RebateScope})} className={inputClass}>
                        <option value="GENERAL">All eligible customers</option><option value="CUSTOMER">Specific customers</option>
                    </select>
                </SettingField>
                {form.scope === "CUSTOMER" && <SettingField label="Customer phone numbers" help="One valid 10-digit phone per line. Never include these in public offer copy." htmlFor="offer-phones">
                    <textarea id="offer-phones" required value={form.customerPhones} onChange={e => setForm({...form, customerPhones: e.target.value})} className={inputClass} />
                </SettingField>}
                {(["validFrom", "validUntil"] as const).map(key => <SettingField key={key} label={key === "validFrom" ? "Starts (India time)" : "Ends (India time)"}
                    help="Enter Asia/Kolkata local time, regardless of your device timezone." htmlFor={`offer-${key}`}>
                    <input id={`offer-${key}`} type="datetime-local" required value={form[key]} onChange={e => setForm({...form, [key]: e.target.value})} className={inputClass} />
                </SettingField>)}
                {(["maxTotalUses", "maxUsesPerCustomer"] as const).map(key => <SettingField key={key} label={key === "maxTotalUses" ? "Total uses" : "Uses per customer"}
                    help="Leave blank for no additional limit. Per-customer limit cannot exceed total uses." htmlFor={`offer-${key}`}>
                    <input id={`offer-${key}`} type="number" min="1" step="1" value={form[key]} onChange={e => setForm({...form, [key]: e.target.value})} className={inputClass} />
                </SettingField>)}
                <div className="sm:col-span-2"><p className="mb-3 text-sm text-[#756763]">New offers are enabled immediately but apply only within their schedule. Editing preserves enabled/disabled status.</p>
                    <button className="min-h-12 rounded-xl bg-[#7a1625] px-5 font-bold text-white">{busy ? "Saving..." : editing ? "Save offer" : "Create offer"}</button>
                    {editing && <button type="button" className="ml-3 min-h-12 px-4 underline" onClick={() => {setEditing(null); setForm(empty);}}>Cancel edit</button>}
                </div>
            </fieldset>
        </form>}
        {canView && !loaded && !error && <p role="status">Loading offers...</p>}
        {canView && loaded && !offers.length && <p>No offers yet.</p>}
        <div className="space-y-3">{offers.map(offer => <article key={offer.id} className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <div className="flex flex-wrap items-center justify-between gap-3">
                <div><h2 className="font-bold">{offer.name} <span className="font-mono text-sm">({offer.code})</span></h2>
                    <p className="text-sm">{offer.active ? "Enabled" : "Disabled"} · {offer.branchName ?? "All branches"} · {offer.visibility === "PUBLIC" ? "Public" : "Code only"}</p></div>
                {canManage && (owner || (offer.branchId !== null && profile?.branchIds.includes(offer.branchId))) && <div className="flex gap-2">
                    <button disabled={busy} className="min-h-11 rounded-xl border px-4" onClick={() => edit(offer)}>Edit</button>
                    <button disabled={busy} className="min-h-11 rounded-xl border px-4" onClick={() => toggle(offer)}>{offer.active ? "Deactivate" : "Activate"}</button>
                </div>}
            </div>
            <p className="mt-2 text-sm">{offer.rebateType === "PERCENTAGE" ? `${offer.rebateValue}% off, up to INR ${offer.maximumDiscountAmount}`
                : offer.rebateType === "FIXED_AMOUNT" ? `INR ${offer.rebateValue} off` : `${offer.slabs.length} spend-based slabs`}</p>
            <p className="mt-1 text-xs text-[#756763]">{offer.validFrom.replace("T", " ")} to {offer.validUntil.replace("T", " ")} (India time). Schedule, spend and usage limits apply.</p>
        </article>)}</div>
    </main>;
}
