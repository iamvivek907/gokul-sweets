"use client";

import {useEffect, useState, type FormEvent} from "react";
import {adminFetch} from "@/services/adminApi";

type Boundary = {vertices: {latitude: number; longitude: number}[]; reviewed: boolean};

export function DeliveryBoundarySettings({branchId, zoneId, authorization}:
        {branchId: number; zoneId: number; authorization: string}) {
    const [vertices, setVertices] = useState("");
    const [reviewed, setReviewed] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");
    const path = `/api/admin/branches/${branchId}/delivery-zones/${zoneId}/boundary`;

    useEffect(() => {
        const controller = new AbortController();
        adminFetch(path, authorization, {signal: controller.signal}).then(async response => {
            if (response.status === 404) return;
            if (!response.ok) throw new Error("Boundary could not be loaded.");
            const boundary = await response.json() as Boundary;
            if (!controller.signal.aborted) {
                setVertices(boundary.vertices.map(point => `${point.latitude}, ${point.longitude}`).join("\n"));
                setReviewed(boundary.reviewed);
            }
        }).catch(failure => {if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Boundary unavailable.");});
        return () => controller.abort();
    }, [path, authorization]);

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        const parsed = vertices.trim().split(/\r?\n/).map(line => line.split(",").map(value => Number(value.trim())));
        if (parsed.length < 3 || parsed.length > 100 || parsed.some(pair => pair.length !== 2
                || pair.some(value => !Number.isFinite(value)))) {
            setError("Enter 3 to 100 lines with latitude, longitude on each line."); return;
        }
        setBusy(true); setError(""); setNotice("");
        try {
            const response = await adminFetch(path, authorization, {method: "PUT",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({vertices: parsed.map(([latitude, longitude]) => ({latitude, longitude})), reviewed})});
            if (!response.ok) throw new Error("Boundary could not be saved. Check the coordinates, zone and crossing edges.");
            setNotice("Boundary saved. Customer pins are checked only when reviewed; checkout remains closed.");
        } catch (failure) {setError(failure instanceof Error ? failure.message : "Boundary could not be saved.");}
        finally {setBusy(false);}
    }

    return <section className="mt-6 rounded-xl border border-[#d8c9bd] p-4">
        <h3 className="font-bold">Reviewed address boundary</h3>
        <p className="mt-1 text-sm text-[#756763]">Enter surveyed polygon vertices in order, one latitude, longitude pair per line. A PIN alone cannot confirm a street address.</p>
        {error && <p role="alert" className="mt-2 text-red-800">{error}</p>}
        {notice && <p role="status" className="mt-2 text-[#17645b]">{notice}</p>}
        <form onSubmit={save} className="mt-4 grid gap-3">
            <label className="text-sm font-semibold" htmlFor={`boundary-${zoneId}`}>Polygon vertices</label>
            <textarea id={`boundary-${zoneId}`} rows={5} className="w-full rounded-lg border p-3 font-mono text-sm" required
                placeholder={"26.84, 80.93\n26.84, 80.95\n26.86, 80.95\n26.86, 80.93"}
                value={vertices} onChange={event => {setVertices(event.target.value); setReviewed(false);}} />
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={reviewed}
                onChange={event => setReviewed(event.target.checked)} />Coverage vertices reviewed for this branch</label>
            <button type="submit" disabled={busy} className="min-h-11 rounded-lg bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50">
                {busy ? "Saving..." : "Save boundary"}</button>
        </form>
    </section>;
}
