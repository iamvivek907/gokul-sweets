"use client";

import {useEffect, useState, type FormEvent} from "react";
import {getAdminBranchMenu} from "@/services/adminMenuApi";
import {listDeliveryZones, saveDeliveryZone, type DeliveryZone, type ZoneConfiguration} from "@/services/adminDeliveryZonesApi";
import type {AdminBranchProduct} from "@/types/adminMenu";
import {DeliveryCapacitySettings} from "@/components/admin/DeliveryCapacitySettings";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {DeliveryBoundarySettings} from "@/components/admin/DeliveryBoundarySettings";

const empty: ZoneConfiguration = {locality: "", postalCode: "", opensAt: "10:00", closesAt: "20:00",
    active: false, riderPaused: true, productIds: []};

export function DeliveryZonesSettings({branchId, authorization}: {branchId: number; authorization: string}) {
    const capacityEnabled = useStorefrontFeatures()?.deliveryCapacity === true;
    const boundariesEnabled = useStorefrontFeatures()?.deliveryAddressBoundaries === true;
    const [zones, setZones] = useState<DeliveryZone[]>([]);
    const [products, setProducts] = useState<AdminBranchProduct[]>([]);
    const [form, setForm] = useState<ZoneConfiguration>(empty);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        Promise.all([listDeliveryZones(branchId, authorization, controller.signal),
            getAdminBranchMenu(branchId, authorization, controller.signal)])
            .then(([nextZones, menu]) => {if (!controller.signal.aborted) {setZones(nextZones); setProducts(menu);}})
            .catch(failure => {if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Could not load delivery settings.");});
        return () => controller.abort();
    }, [branchId, authorization]);

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (busy) return;
        setBusy(true); setError(""); setNotice("");
        try {
            const updated = await saveDeliveryZone(branchId, authorization, form);
            setZones(current => [...current.filter(zone => zone.id !== updated.id), updated]
                .sort((a, b) => a.locality.localeCompare(b.locality)));
            setNotice("Zone settings saved. Delivery orders remain unavailable until capacity and checkout are ready.");
        } catch (failure) {setError(failure instanceof Error ? failure.message : "Could not save delivery settings.");}
        finally {setBusy(false);}
    }

    const field = "mt-1 min-h-11 w-full rounded-lg border border-[#d8c9bd] bg-white p-2 text-[#241715]";
    const choices = products.filter(product => product.available && product.productActive && product.categoryActive);
    const selectedZone = zones.find(zone => zone.locality === form.locality.trim().toLowerCase()
        && zone.postalCode === form.postalCode);

    return <section className="mt-8 rounded-2xl border border-[#eadfd6] bg-white p-5">
        <p className="text-xs font-bold uppercase tracking-widest text-[#a56e2e]">Delivery configuration</p>
        <h2 className="mt-1 text-xl font-bold">Coverage zones</h2>
        <p className="mt-2 text-sm text-[#756763]">Exact locality and PIN coverage only. A zone does not enable delivery checkout or promise a rider.</p>
        {error && <p role="alert" className="mt-3 text-red-800">{error}</p>}
        {notice && <p role="status" className="mt-3 text-[#17645b]">{notice}</p>}
        <div className="mt-4 flex flex-wrap gap-2">{zones.map(zone => <button type="button" key={zone.id}
            className="min-h-11 rounded-lg border border-[#d8c9bd] px-3 text-sm"
            onClick={() => {setForm({locality: zone.locality, postalCode: zone.postalCode, opensAt: zone.opensAt,
                closesAt: zone.closesAt, active: zone.active, riderPaused: zone.riderPaused, productIds: zone.productIds}); setNotice("");}}>
            {zone.locality} · {zone.postalCode} · {zone.active && !zone.riderPaused ? "Configured" : "Paused"}
        </button>)}</div>
        <form onSubmit={save} className="mt-5 grid gap-4 md:grid-cols-2">
            <label className="text-sm font-semibold">Locality<input className={field} required minLength={2} maxLength={120}
                value={form.locality} onChange={event => setForm(current => ({...current, locality: event.target.value}))} /></label>
            <label className="text-sm font-semibold">Six-digit PIN<input className={field} required pattern="[0-9]{6}" inputMode="numeric"
                value={form.postalCode} onChange={event => setForm(current => ({...current, postalCode: event.target.value}))} /></label>
            <label className="text-sm font-semibold">Opens (IST)<input className={field} required type="time" value={form.opensAt}
                onChange={event => setForm(current => ({...current, opensAt: event.target.value}))} /></label>
            <label className="text-sm font-semibold">Closes (IST)<input className={field} required type="time" value={form.closesAt}
                onChange={event => setForm(current => ({...current, closesAt: event.target.value}))} /></label>
            <fieldset className="md:col-span-2"><legend className="text-sm font-semibold">Eligible branch products</legend>
                <div className="mt-2 grid max-h-48 gap-2 overflow-auto rounded-lg border p-3 sm:grid-cols-2">{choices.map(product => <label key={product.productId} className="flex gap-2 text-sm">
                    <input type="checkbox" checked={form.productIds.includes(product.productId)} onChange={event =>
                        setForm(current => ({...current, productIds: event.target.checked
                            ? [...current.productIds, product.productId]
                            : current.productIds.filter(id => id !== product.productId)}))} />{product.productName}
                </label>)}</div></fieldset>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.active}
                onChange={event => setForm(current => ({...current, active: event.target.checked}))} />Area enabled</label>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={form.riderPaused}
                onChange={event => setForm(current => ({...current, riderPaused: event.target.checked}))} />Rider paused</label>
            <div className="md:col-span-2 flex flex-wrap gap-3"><button type="submit" disabled={busy || form.productIds.length === 0}
                className="min-h-11 rounded-lg bg-[#7a1625] px-5 font-semibold text-white disabled:opacity-50">{busy ? "Saving..." : "Save zone"}</button>
                <button type="button" className="min-h-11 rounded-lg border px-4" onClick={() => {setForm(empty); setNotice("");}}>New zone</button></div>
        </form>
        {capacityEnabled && selectedZone && <DeliveryCapacitySettings key={selectedZone.id}
            branchId={branchId} zoneId={selectedZone.id} authorization={authorization} />}
        {boundariesEnabled && selectedZone && <DeliveryBoundarySettings key={`boundary-${selectedZone.id}`}
            branchId={branchId} zoneId={selectedZone.id} authorization={authorization} />}
    </section>;
}
