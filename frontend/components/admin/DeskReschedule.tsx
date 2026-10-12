"use client";
import { useEffect, useRef, useState } from "react";
import { getPickupSlots } from "@/services/pickupApi";
import { staffDeskRequest, type DeskRow } from "@/services/staffDeskApi";
import { businessDateOffset, formatBusinessTime, parseBusinessTimestamp } from "@/lib/businessTime";
import { T, useTranslation } from "@/lib/language";
import type { PickupSlot } from "@/types/pickup";
import styles from "./StaffOrderDesk.module.css";
export default function DeskReschedule({ row, branchId, authorization, onDone, onBusy }: {
    row: DeskRow;
    branchId: number;
    authorization: string;
    onDone: () => void;
    onBusy: (busy: boolean) => void;
}) {
    const tr = useTranslation(), [date, setDate] = useState(row.date), [slots, setSlots] = useState<PickupSlot[]>([]), [slot, setSlot] = useState(""), [reason, setReason] = useState(""), [loading, setLoading] = useState(true), [error, setError] = useState(""), [busy, setBusy] = useState(false), [retry, setRetry] = useState(0), [key, setKey] = useState(() => crypto.randomUUID());
    const life = useRef<AbortController | null>(null), sending = useRef(false);
    useEffect(() => { const c = new AbortController(); life.current = c; return () => c.abort(); }, []);
    useEffect(() => { const c = new AbortController(), timer = window.setTimeout(() => c.abort(), 15000); let mounted = true; void Promise.resolve().then(() => { setLoading(true); setSlot(""); setError(""); return getPickupSlots(branchId, date, c.signal); }).then(data => { if (!c.signal.aborted) {
        setSlots(data.filter(s => s.active && parseBusinessTimestamp(`${s.slotDate}T${s.startTime}`).getTime() > Date.now()));
        setLoading(false);
    } }).catch(e => { if (mounted) {
        setError(e instanceof Error ? e.message : "Could not load pickup slots.");
        setLoading(false);
    } }).finally(() => clearTimeout(timer)); return () => { mounted = false; c.abort(); clearTimeout(timer); }; }, [branchId, date, retry]);
    async function save(e: React.FormEvent) { e.preventDefault(); if (sending.current || !slot)
        return; sending.current = true; setBusy(true); onBusy(true); setError(""); try {
        await staffDeskRequest(`/api/admin/orders/${encodeURIComponent(row.orderNumber)}/reschedule`, authorization, { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ requestKey: key, targetSlotId: Number(slot), reason: reason.trim() }) }, life.current?.signal);
        if (!life.current?.signal.aborted)
            onDone();
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : "Could not change pickup.");
    }
    finally {
        sending.current = false;
        if (!life.current?.signal.aborted) {
            setBusy(false);
            onBusy(false);
        }
    } }
    return <form onSubmit={e => void save(e)}><h3><T text="Customer-requested pickup change"/></h3><p className={styles.muted}><T text="Only before preparation and KOT creation. Stock, capacity and existing charges are checked before saving."/></p><div className={styles.filters}><label><T text="Pickup date"/><input type="date" min={businessDateOffset(0)} value={date} required disabled={busy} onChange={e => { setDate(e.target.value); setKey(crypto.randomUUID()); }}/></label><label><T text="Pickup time"/><select value={slot} required disabled={loading || busy} onChange={e => { setSlot(e.target.value); setKey(crypto.randomUUID()); }}><option value="">{tr(loading ? "Loading slots…" : "Choose a time")}</option>{slots.map(s => <option key={s.id} value={s.id}>{formatBusinessTime(s.startTime)}–{formatBusinessTime(s.endTime)}</option>)}</select></label></div><label className={styles.reason}><T text="Customer request / reason"/><textarea required maxLength={500} disabled={busy} value={reason} onChange={e => { setReason(e.target.value); setKey(crypto.randomUUID()); }}/></label>{error && <p role="alert" className={styles.error}>{tr(error)} <button type="button" disabled={busy} onClick={() => setRetry(n => n + 1)}><T text="Retry"/></button></p>}<button className={styles.primary} disabled={busy || loading || !slot || !reason.trim()}>{tr(busy ? "Saving…" : "Check & save pickup")}</button></form>;
}
