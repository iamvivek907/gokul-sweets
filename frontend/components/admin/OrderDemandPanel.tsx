"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import { adminFetch } from "@/services/adminApi";
import { staffDeskRequest, type DemandRow, type DeskPolicy } from "@/services/staffDeskApi";
import { businessDateOffset, parseBusinessDate } from "@/lib/businessTime";
import { formatWeight } from "@/lib/orderQuantity";
import { T, useTranslation } from "@/lib/language";
import styles from "./StaffOrderDesk.module.css";
const amount = (n: number, mode: string) => mode === "WEIGHT" ? formatWeight(n) : `${n} pcs`;
export default function OrderDemandPanel({ branchId, authorization, canConfigure }: {
    branchId: number;
    authorization: string;
    canConfigure: boolean;
}) {
    const tr = useTranslation(), [from, setFrom] = useState(() => businessDateOffset(0)), [to, setTo] = useState(() => businessDateOffset(14)), [day, setDay] = useState(() => businessDateOffset(0));
    const [rows, setRows] = useState<DemandRow[]>([]), [loading, setLoading] = useState(true), [error, setError] = useState(""), [exporting, setExporting] = useState(false), [retry, setRetry] = useState(0), [policyOpen, setPolicyOpen] = useState(false), [policies, setPolicies] = useState<DeskPolicy[]>([]), [policyBusy, setPolicyBusy] = useState(false);
    const life = useRef<AbortController | null>(null);
    useEffect(() => { const c = new AbortController(); life.current = c; return () => c.abort(); }, []);
    const valid = from !== "" && to !== "" && to >= from && (parseBusinessDate(to).getTime() - parseBusinessDate(from).getTime()) / 86400000 <= 30;
    useEffect(() => {
        if (!valid)
            return;
        const c = new AbortController();
        let fetching = false;
        async function load(initial = false) {
            if (fetching || c.signal.aborted)
                return;
            fetching = true;
            if (initial) {
                setLoading(true);
                setError("");
            }
            try {
                const data = await staffDeskRequest<DemandRow[]>(`/api/admin/orders/planning/demand?${new URLSearchParams({ branchId: String(branchId), from, to })}`, authorization, {}, c.signal);
                if (!c.signal.aborted) {
                    setRows(data);
                    setError("");
                }
            }
            catch (e) {
                if (!c.signal.aborted)
                    setError(e instanceof Error ? e.message : "Could not load demand.");
            }
            finally {
                fetching = false;
                if (!c.signal.aborted)
                    setLoading(false);
            }
        }
        void Promise.resolve().then(() => load(true));
        const poll = () => { if (document.visibilityState === 'visible' && navigator.onLine)
            void load(); };
        const timer = window.setInterval(poll, 30000);
        window.addEventListener('online', poll);
        document.addEventListener('visibilitychange', poll);
        return () => { c.abort(); clearInterval(timer); window.removeEventListener('online', poll); document.removeEventListener('visibilitychange', poll); };
    }, [branchId, authorization, from, to, valid, retry]);
    const showPolicies = useCallback(async () => { setPolicyBusy(true); setError(""); try {
        const data = await staffDeskRequest<DeskPolicy[]>(`/api/admin/orders/planning/products?branchId=${branchId}`, authorization, {}, life.current?.signal);
        if (!life.current?.signal.aborted) {
            setPolicies(data);
            setPolicyOpen(true);
        }
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : "Could not load product policies.");
    }
    finally {
        if (!life.current?.signal.aborted)
            setPolicyBusy(false);
    } }, [branchId, authorization]);
    async function togglePolicy(p: DeskPolicy) { if (policyBusy)
        return; setPolicyBusy(true); setError(""); try {
        await staffDeskRequest<void>(`/api/admin/orders/planning/products/${p.productId}?branchId=${branchId}`, authorization, { method: "PUT", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ earlyPreparationAllowed: !p.earlyPreparationAllowed }) }, life.current?.signal);
        if (!life.current?.signal.aborted)
            setPolicies(current => current.map(x => x.productId === p.productId ? { ...x, earlyPreparationAllowed: !x.earlyPreparationAllowed } : x));
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : "Could not save policy.");
    }
    finally {
        if (!life.current?.signal.aborted)
            setPolicyBusy(false);
    } }
    async function download() { if (exporting || !valid)
        return; setExporting(true); setError(""); const c = new AbortController(), abort = () => c.abort(); life.current?.signal.addEventListener('abort', abort, { once: true }); const timer = window.setTimeout(abort, 30000); try {
        const response = await adminFetch(`/api/admin/orders/planning/demand/export?${new URLSearchParams({ branchId: String(branchId), from, to })}`, authorization, { signal: c.signal });
        if (!response.ok)
            throw new Error("Could not export demand. Check access and retry.");
        const blob = await response.blob();
        if (life.current?.signal.aborted)
            return;
        const url = URL.createObjectURL(blob), a = document.createElement('a');
        a.href = url;
        a.download = `gokul-demand-${branchId}-${from}-${to}.xlsx`;
        a.click();
        window.setTimeout(() => URL.revokeObjectURL(url), 10000);
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : "Export failed. Retry.");
    }
    finally {
        clearTimeout(timer);
        life.current?.signal.removeEventListener('abort', abort);
        if (!life.current?.signal.aborted)
            setExporting(false);
    } }
    const dates = Array.from(new Set([from, ...rows.map(r => r.date)])).sort(), active = day >= from && day <= to ? day : from, items = rows.filter(r => r.date === active);
    return <section><div className={styles.toolbar}><div><h2><T text="Daily item demand"/></h2><p className={styles.muted}><T text="Confirmed orders by pickup date. These quantities are demand, not available stock."/></p></div><div className={styles.actions}>{canConfigure && <button disabled={policyBusy} onClick={() => void showPolicies()}><T text="Early packing settings"/></button>}<button className={styles.primary} disabled={!valid || loading || !!error || exporting} onClick={() => void download()}>{tr(exporting ? "Preparing export…" : "Download Excel")}</button></div></div>
        <div className={styles.filters}><label><T text="From"/><input type="date" value={from} onChange={e => setFrom(e.target.value)}/></label><label><T text="To"/><input type="date" value={to} onChange={e => setTo(e.target.value)}/></label><span className={styles.muted}><T text="Up to 31 days · all matching orders are exported"/></span></div>
        {!valid && <p role="alert"><T text="Choose a date range of at most 31 days."/></p>}{error && <p role="alert" className={styles.error}>{tr(error)} <button onClick={() => setRetry(n => n + 1)}><T text="Retry"/></button></p>}
        {valid && (loading ? <p role="status"><T text="Loading demand…"/></p> : <><div className={styles.days}>{dates.map(d => { const list = rows.filter(r => r.date === d), grams = list.filter(r => r.saleMode === "WEIGHT").reduce((s, r) => s + r.ordered, 0), pcs = list.filter(r => r.saleMode !== "WEIGHT").reduce((s, r) => s + r.ordered, 0); return <button key={d} aria-pressed={active === d} className={active === d ? styles.active : ""} onClick={() => setDay(d)}><strong>{d}</strong><span>{[grams ? formatWeight(grams) : "", pcs ? `${pcs} pcs` : ""].filter(Boolean).join(" · ") || tr("No orders")}</span></button>; })}</div><h3>{active}</h3>{!items.length ? <p className={styles.empty}><T text="No confirmed item demand on this date."/></p> : <div className={styles.demand}>{items.map(r => <article key={`${r.productId}:${r.saleMode}:${r.productName}`}><h3>{r.productName}</h3><dl><div><dt><T text="Ordered"/></dt><dd>{amount(r.ordered, r.saleMode)}</dd></div><div><dt><T text="Not started"/></dt><dd>{amount(r.waiting, r.saleMode)}</dd></div><div><dt><T text="Preparing"/></dt><dd>{amount(r.preparing, r.saleMode)}</dd></div><div><dt><T text="Ready"/></dt><dd>{amount(r.ready, r.saleMode)}</dd></div><div><dt><T text="Completed / dispatched"/></dt><dd>{amount(r.completed, r.saleMode)}</dd></div></dl></article>)}</div>}</>)}
        {policyOpen && <section className={styles.policy}><div className={styles.toolbar}><h3><T text="Counter products allowed for early packing"/></h3><button disabled={policyBusy} onClick={() => setPolicyOpen(false)}><T text="Close"/></button></div><p className={styles.muted}><T text="Enable only counter items safe to pack early on the pickup day. Any kitchen item keeps a mixed order scheduled."/></p>{policies.map(p => <label key={p.productId} className={styles.policyRow}><input type="checkbox" checked={p.earlyPreparationAllowed} disabled={policyBusy} onChange={() => void togglePolicy(p)}/>{p.productName}</label>)}</section>}
    </section>;
}
