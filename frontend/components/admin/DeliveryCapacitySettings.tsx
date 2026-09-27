"use client";

import {useEffect, useState, type FormEvent} from "react";
import {listDeliveryWindows, saveDeliveryWindow, type DeliveryWindow} from "@/services/adminDeliveryCapacityApi";

export function DeliveryCapacitySettings({branchId, zoneId, authorization}: {branchId: number; zoneId: number; authorization: string}) {
    const [windows, setWindows] = useState<DeliveryWindow[]>([]);
    const [date, setDate] = useState("");
    const [starts, setStarts] = useState("10:00");
    const [ends, setEnds] = useState("11:00");
    const [capacity, setCapacity] = useState(1);
    const [paused, setPaused] = useState(true);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [notice, setNotice] = useState("");

    useEffect(() => {
        const controller = new AbortController();
        listDeliveryWindows(branchId, zoneId, authorization, controller.signal)
            .then(next => {if (!controller.signal.aborted) setWindows(next);})
            .catch(failure => {if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Could not load windows.");});
        return () => controller.abort();
    }, [branchId, zoneId, authorization]);

    async function save(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true); setError(""); setNotice("");
        try {
            const updated = await saveDeliveryWindow(branchId, zoneId, authorization,
                {serviceDate: date, startsAt: starts, endsAt: ends, riderCapacity: capacity, paused});
            setWindows(current => [...current.filter(window => window.id !== updated.id), updated]
                .sort((a, b) => `${a.serviceDate}${a.startsAt}`.localeCompare(`${b.serviceDate}${b.startsAt}`)));
            setNotice("Rider window saved. Delivery checkout remains unavailable.");
        } catch (failure) {setError(failure instanceof Error ? failure.message : "Could not save window.");}
        finally {setBusy(false);}
    }

    return <section className="mt-6 rounded-xl border border-[#d8c9bd] p-4">
        <h3 className="font-bold">Rider windows (IST)</h3>
        <p className="mt-1 text-sm text-[#756763]">Configure each date explicitly. Pausing a window removes it from preview.</p>
        {error && <p role="alert" className="mt-2 text-red-800">{error}</p>}
        {notice && <p role="status" className="mt-2 text-[#17645b]">{notice}</p>}
        <div className="mt-3 flex flex-wrap gap-2">{windows.map(window => <button type="button" key={window.id}
            className="min-h-11 rounded-lg border px-3 text-sm" onClick={() => {setDate(window.serviceDate);
                setStarts(window.startsAt); setEnds(window.endsAt); setCapacity(window.riderCapacity); setPaused(window.paused);}}>
            {window.serviceDate} · {window.startsAt.slice(0, 5)}–{window.endsAt.slice(0, 5)} · {window.paused ? "Paused" : `${window.reservedCount}/${window.riderCapacity} reserved`}
        </button>)}</div>
        <form onSubmit={save} className="mt-4 grid gap-3 sm:grid-cols-2">
            <label className="text-sm">Service date<input className="mt-1 min-h-11 w-full rounded-lg border p-2" type="date" required value={date}
                onChange={event => setDate(event.target.value)} /></label>
            <label className="text-sm">Rider capacity<input className="mt-1 min-h-11 w-full rounded-lg border p-2" type="number" min={1} max={1000} required value={capacity}
                onChange={event => setCapacity(Number(event.target.value))} /></label>
            <label className="text-sm">Start (IST)<input className="mt-1 min-h-11 w-full rounded-lg border p-2" type="time" required value={starts}
                onChange={event => setStarts(event.target.value)} /></label>
            <label className="text-sm">End (IST)<input className="mt-1 min-h-11 w-full rounded-lg border p-2" type="time" required value={ends}
                onChange={event => setEnds(event.target.value)} /></label>
            <label className="flex items-center gap-2 text-sm"><input type="checkbox" checked={paused}
                onChange={event => setPaused(event.target.checked)} />Paused</label>
            <button type="submit" disabled={busy} className="min-h-11 rounded-lg bg-[#7a1625] px-4 font-semibold text-white disabled:opacity-50">
                {busy ? "Saving..." : "Save rider window"}</button>
        </form>
    </section>;
}
