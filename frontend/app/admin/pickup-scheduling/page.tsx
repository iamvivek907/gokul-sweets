"use client";

import {useEffect, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {SettingField, SettingToggle} from "@/components/admin/BranchOperationalSettings";
import {getAdminBranches} from "@/services/adminBranchesApi";
import {adminManagementApi} from "@/services/adminManagementApi";
import type {AdminBranch} from "@/types/adminBranches";
import type {PickupSlot} from "@/types/pickup";

const fields = [
    ["startDate", "First date", "First pickup date to generate or view.", "date"],
    ["endDate", "Last date", "Last pickup date, inclusive. View up to 61 days at a time.", "date"],
    ["startTime", "Opening time", "First pickup time each day, in India time.", "time"],
    ["endTime", "Closing time", "Last slot must end by this time, in India time.", "time"],
    ["slotDurationMinutes", "Slot duration (minutes)", "The time range must divide evenly into this duration.", "number"],
    ["capacity", "Orders per slot", "Maximum normal pickup orders, not the number of sweets.", "number"],
    ["priorityCapacity", "Priority orders per slot", "Separate capacity for paid priority pickup; zero when disabled.", "number"],
    ["priorityCharge", "Priority charge (INR)", "Additional charge for priority pickup; zero when disabled.", "number"]
] as const;

export default function PickupSchedulingPage() {
    const {authorization, hasPermission} = useAdminAuth();
    const allowed = hasPermission("BRANCH_MANAGE");
    const [branches, setBranches] = useState<AdminBranch[]>([]);
    const [branchId, setBranchId] = useState("");
    const [form, setForm] = useState({startDate: "", endDate: "", startTime: "09:00", endTime: "21:00",
        slotDurationMinutes: "30", capacity: "10", priorityEnabled: false, priorityCapacity: "0", priorityCharge: "0"});
    const [slots, setSlots] = useState<PickupSlot[]>([]);
    const [error, setError] = useState("");
    const [message, setMessage] = useState("");
    const [busy, setBusy] = useState(false);
    const [viewed, setViewed] = useState(false);

    useEffect(() => {
        if (!authorization || !allowed) return;
        const controller = new AbortController();
        getAdminBranches(authorization, controller.signal).then(setBranches)
            .catch(error => {if (!controller.signal.aborted) setError(error.message);});
        return () => controller.abort();
    }, [authorization, allowed]);

    async function load(generate: boolean) {
        if (!authorization) return;
        setBusy(true); setError(""); setMessage("");
        const path = `/api/admin/branches/${branchId}/pickup-slots`;
        try {
            if (generate) {
                const created = await adminManagementApi<PickupSlot[]>(path, authorization, {
                    method: "POST", headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({...form, slotDurationMinutes: Number(form.slotDurationMinutes), capacity: Number(form.capacity),
                        priorityCapacity: form.priorityEnabled ? Number(form.priorityCapacity) : 0,
                        priorityCharge: form.priorityEnabled ? Number(form.priorityCharge) : 0})
                });
                setMessage(`${created.length} slots generated. Existing reservations are preserved.`);
            }
            const data = await adminManagementApi<PickupSlot[]>(`${path}?startDate=${form.startDate}&endDate=${form.endDate}`, authorization);
            setSlots(data); setViewed(true);
        } catch (error) {setError(error instanceof Error ? error.message : "Unable to load pickup schedule.");}
        finally {setBusy(false);}
    }

    if (!allowed) return <p>You do not have permission to manage pickup schedules.</p>;
    return <main className="space-y-6">
        <h1 className="text-2xl font-bold text-[#7a1625]">Pickup scheduling</h1>
        <p className="text-sm text-[#756763]">Generate slots using the existing branch schedule engine. Existing or overlapping slots are skipped, never overwritten.</p>
        {error && <p role="alert" className="text-red-700">{error}</p>}
        {message && <p role="status" className="text-green-800">{message}</p>}
        <form onSubmit={event => {event.preventDefault(); void load(true);}} className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <fieldset disabled={busy} className="grid gap-5 sm:grid-cols-2">
                <SettingField label="Branch" help="Slots and capacity apply only to this branch." htmlFor="branch">
                    <select id="branch" required value={branchId} onChange={event => {setBranchId(event.target.value); setSlots([]); setViewed(false);}} className="min-h-11 w-full rounded-xl border px-3">
                        <option value="">Choose a branch</option>{branches.map(branch => <option key={branch.id} value={branch.id}>{branch.name}</option>)}
                    </select>
                </SettingField>
                {fields.map(([key, label, help, type]) => <SettingField key={key} label={label} help={help} htmlFor={key}>
                    <input id={key} type={type} required value={form[key]}
                        min={type === "number" ? key.startsWith("priority") ? 0 : 1 : undefined}
                        step={key === "priorityCharge" ? "0.01" : undefined}
                        disabled={key.startsWith("priority") && !form.priorityEnabled}
                        onChange={event => {setForm({...form, [key]: event.target.value}); setViewed(false); setSlots([]);}}
                        className="min-h-11 w-full rounded-xl border px-3" />
                </SettingField>)}
                <SettingToggle label="Priority pickup" description="Offer separate priority capacity and a surcharge for generated slots."
                    checked={form.priorityEnabled} disabled={busy} onChange={priorityEnabled => setForm({...form, priorityEnabled})} />
                <div className="flex flex-wrap gap-3">
                    <button className="min-h-11 rounded-xl bg-[#7a1625] px-4 font-bold text-white">{busy ? "Working..." : "Generate slots"}</button>
                    <button type="button" disabled={!branchId || !form.startDate || !form.endDate} onClick={() => load(false)} className="min-h-11 rounded-xl border px-4">View slots</button>
                </div>
            </fieldset>
        </form>
        {viewed && !slots.length && <p>No slots in this date range.</p>}
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">{slots.map(slot => <article key={slot.id} className="rounded-xl border border-[#eadfd6] bg-white p-4">
            <h2 className="font-bold">{slot.slotDate} · {slot.startTime.slice(0, 5)} - {slot.endTime.slice(0, 5)}</h2>
            <p className="text-sm">Normal: {slot.bookedCount} / {slot.capacity} booked</p>
            {slot.priorityEnabled && <p className="text-sm">Priority: {slot.priorityBookedCount} / {slot.priorityCapacity} · INR {slot.priorityCharge}</p>}
            <p className="text-sm">{slot.active ? "Active" : "Disabled"}</p>
        </article>)}</div>
    </main>;
}
