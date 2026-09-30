"use client";

import {useEffect, useRef, useState} from "react";
import {adminFetch} from "@/services/adminApi";
import {addDays, prettyDate} from "@/components/occasion/OccasionDatePicker";

type CalendarDay = {date: string; orderCount: number; needsReview: number; committedOrders: number};
type CalendarMonth = {month: string; days: CalendarDay[]};

export default function OccasionPlanningDatePicker({date, today, branchId, authorization, refreshKey, onDate}: {
    date: string; today: string; branchId: number; authorization: string; refreshKey: string; onDate: (date: string) => void;
}) {
    const [open, setOpen] = useState(false);
    const [month, setMonth] = useState(date.slice(0, 7));
    const trigger = useRef<HTMLButtonElement>(null);
    const minYear = Number(today.slice(0, 4)) - 2;
    const minDay = Math.min(Number(today.slice(8)), new Date(Date.UTC(minYear, Number(today.slice(5, 7)), 0)).getUTCDate());
    const min = `${minYear}-${today.slice(5, 7)}-${String(minDay).padStart(2, "0")}`;
    const max = addDays(today, 365);
    const close = () => {setOpen(false); trigger.current?.focus();};
    return <div>
        <button ref={trigger} type="button" aria-expanded={open} aria-controls="bulk-planning-calendar"
            onClick={() => {setMonth(date.slice(0, 7)); setOpen(!open);}}
            className="min-h-11 rounded-xl bg-[#173c39] px-4 font-semibold text-white">Open calendar</button>
        {open && <section id="bulk-planning-calendar" aria-label="Booking calendar"
            onKeyDown={event => {if (event.key === "Escape") close();}}
            className="mt-4 max-w-xl rounded-2xl border bg-white p-3 shadow-sm sm:p-5">
            <div className="flex items-start justify-between gap-3"><div><h3 className="font-bold">Choose a pickup date</h3>
                <p className="mt-1 text-xs text-stone-600">Counts include all bookings and requests for this branch. Select a date to see its full order cards and production totals.</p></div>
                <button type="button" onClick={close} aria-label="Close booking calendar" className="min-h-11 rounded-lg border px-3">✕</button></div>
            <MonthGrid key={`${branchId}:${month}`} month={month} date={date} min={min} max={max} branchId={branchId}
                authorization={authorization} refreshKey={refreshKey} onMonth={setMonth} onDate={value => {onDate(value); close();}} />
        </section>}
    </div>;
}

function MonthGrid({month, date, min, max, branchId, authorization, refreshKey, onMonth, onDate}: {
    month: string; date: string; min: string; max: string; branchId: number; authorization: string; refreshKey: string;
    onMonth: (month: string) => void; onDate: (date: string) => void;
}) {
    const [data, setData] = useState<CalendarMonth | null>(null);
    const [error, setError] = useState("");
    const [retry, setRetry] = useState(0);
    useEffect(() => {
        const controller = new AbortController();
        async function load() {
            try {
                const response = await adminFetch(`/api/admin/branches/${branchId}/occasion-enquiries/calendar?month=${month}`, authorization, {signal: controller.signal});
                if (!response.ok) throw Error("Could not refresh calendar counts. Try again.");
                const next = await response.json() as CalendarMonth;
                if (!controller.signal.aborted) {setData(next); setError("");}
            } catch (failure) {
                if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Could not load calendar counts.");
            }
        }
        void load();
        return () => controller.abort();
    }, [month, branchId, authorization, refreshKey, retry]);
    const first = new Date(`${month}-01T12:00:00Z`);
    const previous = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() - 1, 1)).toISOString().slice(0, 7);
    const next = new Date(Date.UTC(first.getUTCFullYear(), first.getUTCMonth() + 1, 1)).toISOString().slice(0, 7);
    return <>
        <div className="my-4 flex items-center justify-between gap-2">
            <button type="button" aria-label="Previous calendar month" disabled={previous < min.slice(0, 7)} onClick={() => onMonth(previous)} className="min-h-11 rounded-lg border px-3 disabled:opacity-30">←</button>
            <div className="flex gap-2"><label className="sr-only" htmlFor="planning-month">Month</label>
                <select id="planning-month" value={month.slice(5)} onChange={event => onMonth(`${month.slice(0, 4)}-${event.target.value}`)} className="min-h-11 max-w-36 rounded-lg border px-2">
                    {Array.from({length: 12}, (_, n) => {const value = String(n + 1).padStart(2, "0"); const candidate = `${month.slice(0, 4)}-${value}`;
                        return <option key={value} value={value} disabled={candidate < min.slice(0, 7) || candidate > max.slice(0, 7)}>{new Date(`2026-${value}-01T12:00:00Z`).toLocaleDateString("en-IN", {month: "long", timeZone: "UTC"})}</option>;})}
                </select><label className="sr-only" htmlFor="planning-year">Year</label>
                <select id="planning-year" value={month.slice(0, 4)} onChange={event => {const candidate = `${event.target.value}-${month.slice(5)}`; onMonth(candidate < min.slice(0, 7) ? min.slice(0, 7) : candidate > max.slice(0, 7) ? max.slice(0, 7) : candidate);}} className="min-h-11 rounded-lg border px-2">
                    {Array.from({length: Number(max.slice(0, 4)) - Number(min.slice(0, 4)) + 1}, (_, n) => <option key={n}>{Number(min.slice(0, 4)) + n}</option>)}
                </select></div>
            <button type="button" aria-label="Next calendar month" disabled={next > max.slice(0, 7)} onClick={() => onMonth(next)} className="min-h-11 rounded-lg border px-3 disabled:opacity-30">→</button>
        </div>
        {error && <p role="alert" className="mb-3 text-sm text-red-800">{error} <button type="button" onClick={() => setRetry(value => value + 1)} className="underline">Retry</button></p>}
        {!data && !error && <p role="status" className="mb-3 text-sm">Loading order counts…</p>}
        {data && <><div className="grid grid-cols-7 gap-1 text-center text-xs text-stone-600">{["Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"].map(day => <span key={day} className="py-2">{day}</span>)}</div>
            <div className="grid grid-cols-7 gap-1">{Array.from({length: first.getUTCDay()}, (_, n) => <span key={`blank-${n}`} />)}
                {data.days.map(day => <button key={day.date} type="button" disabled={day.date < min || day.date > max}
                    aria-label={`${prettyDate(day.date)} · ${day.orderCount} orders · ${day.needsReview} awaiting quote · ${day.committedOrders} paid bookings`}
                    aria-pressed={date === day.date} onClick={() => onDate(day.date)}
                    className={`flex min-h-16 flex-col items-center justify-center rounded-lg border text-sm disabled:opacity-30 ${date === day.date ? "border-[#173c39] bg-[#173c39] text-white" : day.orderCount > 0 ? "border-[#b5cfc3] bg-[#eef6f1] text-[#173c39]" : "border-stone-100 text-stone-600 hover:bg-stone-50"}`}>
                    <span className="font-semibold">{Number(day.date.slice(8))}</span><span className="mt-1 text-[10px] sm:text-xs">{day.orderCount} <span className="sr-only sm:not-sr-only">orders</span></span>
                </button>)}</div>
            <p className="mt-3 text-xs text-stone-600">Green dates have bookings or requests · India time. Cancelled and completed requests remain available in the date’s order history.</p></>}
    </>;
}
