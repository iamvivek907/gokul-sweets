"use client";

import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {SettingField, SettingToggle} from "@/components/admin/BranchOperationalSettings";
import {adminManagementApi, type TaxCategory} from "@/services/adminManagementApi";

const empty = {code: "", name: "", hsnSacCode: "", cgstRate: 0, sgstRate: 0, igstRate: 0, active: true};
const fields = [
    ["code", "Tax code", "Unique code used when assigning tax to products.", "text"],
    ["name", "Name", "Name staff see when choosing a product's tax category.", "text"],
    ["hsnSacCode", "HSN / SAC code", "Classification used for tax reporting; leave blank if not applicable.", "text"],
    ["cgstRate", "CGST (%)", "Central tax percentage. Pickup prices use CGST plus SGST.", "number"],
    ["sgstRate", "SGST (%)", "State tax percentage. Changes affect new checkout calculations, not saved orders.", "number"],
    ["igstRate", "IGST (%)", "Stored for tax reporting. Existing pickup calculations do not apply IGST.", "number"]
] as const;
const path = "/api/admin/tax-categories";

export default function TaxCategoriesPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const [items, setItems] = useState<TaxCategory[]>([]);
    const [form, setForm] = useState(empty);
    const [editing, setEditing] = useState<number | null>(null);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    const [loaded, setLoaded] = useState(false);
    const allowed = hasPermission("MENU_MANAGE");

    useEffect(() => {
        if (!authorization || !allowed) return;
        const controller = new AbortController();
        adminManagementApi<TaxCategory[]>(path, authorization, {signal: controller.signal})
            .then(data => { setItems(data); setLoaded(true); })
            .catch(error => { if (!controller.signal.aborted) setError(error.message); });
        return () => controller.abort();
    }, [authorization, allowed]);

    async function save(event: React.FormEvent) {
        event.preventDefault();
        if (!authorization) return;
        setBusy(true); setError(""); setMessage("");
        try {
            const saved = await adminManagementApi<TaxCategory>(editing ? `${path}/${editing}` : path, authorization, {
                method: editing ? "PUT" : "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(form)
            });
            setItems(current => [...current.filter(item => item.id !== saved.id), saved]);
            setEditing(null); setForm(empty); setMessage("Tax category saved.");
        } catch (error) { setError(error instanceof Error ? error.message : "Unable to save tax category."); }
        finally { setBusy(false); }
    }

    async function toggle(item: TaxCategory) {
        if (!authorization) return;
        setBusy(true); setError(""); setMessage("");
        try {
            const saved = await adminManagementApi<TaxCategory>(`${path}/${item.id}/active`, authorization, {
                method: "PATCH", headers: {"Content-Type": "application/json"},
                body: JSON.stringify({active: !item.active})
            });
            setItems(current => current.map(value => value.id === saved.id ? saved : value));
            if (editing === saved.id) setForm(current => ({...current, active: saved.active}));
            setMessage("Tax category status updated.");
        } catch (error) { setError(error instanceof Error ? error.message : "Unable to update tax category."); }
        finally { setBusy(false); }
    }

    if (!allowed) return <p>You do not have permission to manage tax categories.</p>;
    return <main className="space-y-6">
        <h1 className="text-2xl font-bold text-[#7a1625]">Tax categories</h1>
        <p className="text-sm text-[#756763]">Manage product taxes. Deactivation prevents new checkout for products still assigned to this category. Reassign those products first. Saved order taxes remain unchanged.</p>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        {message && <p role="status" className="text-green-800">{message}</p>}
        <form onSubmit={save} className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <h2 className="mb-4 font-bold">{editing ? "Edit category" : "Create category"}</h2>
            <fieldset disabled={busy} className="grid gap-5 sm:grid-cols-2">
                {fields.map(([key, label, help, type]) => <SettingField key={key} label={label} help={help} htmlFor={key}>
                    <input id={key} type={type} value={form[key]} required={key !== "hsnSacCode"}
                        min={type === "number" ? 0 : undefined} max={type === "number" ? 100 : undefined}
                        step={type === "number" ? "0.01" : undefined}
                        maxLength={key === "code" ? 80 : key === "name" ? 100 : 20}
                        onChange={event => setForm({...form, [key]: type === "number" ? Number(event.target.value) : event.target.value})}
                        className="min-h-11 w-full rounded-xl border border-[#eadfd6] px-3" />
                </SettingField>)}
                <SettingToggle label="Active" description="Allow staff to choose this category for products."
                    checked={form.active} disabled={busy} onChange={active => setForm({...form, active})} />
                <div className="flex gap-3">
                    <button className="min-h-11 rounded-xl bg-[#7a1625] px-5 font-bold text-white">{busy ? "Saving..." : "Save category"}</button>
                    {editing && <button type="button" onClick={() => {setEditing(null); setForm(empty);}} className="min-h-11 px-4">Cancel</button>}
                </div>
            </fieldset>
        </form>
        {!loaded && !error && <p role="status">Loading tax categories...</p>}
        {loaded && !items.length && <p>No tax categories yet.</p>}
        <div className="space-y-3">{items.map(item => <article key={item.id} className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-[#eadfd6] bg-white p-4">
            <div><h2 className="font-bold">{item.name} ({item.code})</h2>
                <p className="text-sm">CGST {item.cgstRate}% + SGST {item.sgstRate}% | IGST {item.igstRate}% | {item.active ? "Active" : "Inactive"}</p></div>
            <div className="flex gap-2">
                <button disabled={busy} onClick={() => {setEditing(item.id); setForm({...item, hsnSacCode: item.hsnSacCode ?? ""});}} className="min-h-11 rounded-xl border px-4">Edit</button>
                <button disabled={busy} onClick={() => toggle(item)} className="min-h-11 rounded-xl border px-4">{item.active ? "Deactivate" : "Activate"}</button>
            </div>
        </article>)}</div>
    </main>;
}
