"use client";
import { useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useAdminAuth } from "@/contexts/AdminAuthContext";
import { adminFetch } from "@/services/adminApi";
import { workspaceRequest, type WorkspaceItem, type WorkspacePage } from "@/services/menuWorkspaceApi";
import { businessDateOffset } from "@/lib/businessTime";
import { apiClient } from "@/services/apiClient";
import { InventoryInfo } from "@/components/admin/inventory/InventoryHelp";
import styles from "./centre.module.css";
type Row = {
    item: WorkspaceItem;
    selected: boolean;
    quantity: string;
    ready: string;
};
type Group = {
    key: string;
    title: string;
    choices: {
        productId: number;
        label: string;
    }[];
};
type Job = {
    id: string;
    total: number;
    succeeded: number;
    failed: number;
};
type Result = {
    product_id: number;
    name: string;
    status: string;
    error: string | null;
};
type Draft = {
    branch: number;
    from: string;
    through: string;
    method: string;
    applyInventory: boolean;
    applyPolicy: boolean;
    openPurchases: boolean;
    applyHours: boolean;
    enableHours: boolean;
    opens: string;
    closes: string;
    weekdays: number;
    reason: string;
    rows: Row[];
    submissionId: string | null;
    submitted: boolean;
    job: Job | null;
};
async function request<T>(branch: number, path: string, init: RequestInit = {}): Promise<T> { const response = await adminFetch(`/api/admin/inventory/branches/${branch}/centre${path}`, "staff-session", init); if (!response.ok) {
    let message = "Unable to load or save. Retry.";
    try {
        message = (await response.json()).message || message;
    }
    catch { }
    throw new Error(message);
} return response.json(); }
export default function InventoryCentre() {
    const { profile, hasPermission } = useAdminAuth();
    const [branches, setBranches] = useState<{
        id: number;
        name: string;
    }[]>([]);
    const [state, setState] = useState<Draft>({ branch: 0, from: businessDateOffset(0), through: businessDateOffset(0), method: "READY_STOCK", applyInventory: true, applyPolicy: false, openPurchases: false, applyHours: false, enableHours: false, opens: "11:00", closes: "21:30", weekdays: 127, reason: "Reviewed branch inventory plan", rows: [], submissionId: null, submitted: false, job: null });
    const [recent, setRecent] = useState<Job[]>([]);
    const [storageError, setStorageError] = useState(false);
    const [restored, setRestored] = useState(false);
    const [groups, setGroups] = useState<Group[]>([]);
    const [loading, setLoading] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [results, setResults] = useState<Result[]>([]);
    const [resultPage, setResultPage] = useState(0);
    const [search, setSearch] = useState("");
    const [page, setPage] = useState(0);
    const [defaults, setDefaults] = useState({ WEIGHT: "", UNIT: "" });
    const [readyDefaults, setReadyDefaults] = useState({ WEIGHT: "", UNIT: "" });
    const [review, setReview] = useState(false);
    const lock = useRef(false);
    const query = useRef("");
    const jobId = state.job?.id;
    const key = `inventory-centre:${profile?.staffId}`;
    const context = `${state.branch}:${state.from}`;
    useEffect(() => { query.current = context; }, [context]);
    const canManage = hasPermission("MENU_MANAGE") && hasPermission("INVENTORY_MANAGE") && hasPermission("INVENTORY_VIEW");
    const active = !!state.job && state.job.succeeded + state.job.failed < state.job.total;
    const locked = busy || active || state.submitted;
    useEffect(() => { if (!profile)
        return; const c = new AbortController(); void apiClient<{
        id: number;
        name: string;
        active: boolean;
    }[]>("/api/branches", { signal: c.signal }).then(values => { if (c.signal.aborted)
        return; const permitted = values.filter(b => b.active && (profile.roleName === "OWNER_ADMIN" || profile.branchIds.includes(b.id))); setBranches(permitted); let saved: Draft | null = null; try {
        saved = JSON.parse(localStorage.getItem(key) || "null");
    }
    catch { } if (saved && permitted.some(b => b.id === saved!.branch))
        setState(saved);
    else
        setState(s => ({ ...s, branch: permitted[0]?.id ?? 0 })); setRestored(true); }).catch(e => { if (!c.signal.aborted)
        setError(e.message); }); return () => c.abort(); }, [profile, key]);
    useEffect(() => { if (restored)
        void Promise.resolve().then(() => { try {
            localStorage.setItem(key, JSON.stringify(state));
            setStorageError(false);
        }
        catch {
            setStorageError(true);
        } }); }, [state, key, restored]);
    useEffect(() => { if (!state.branch || !restored)
        return; const c = new AbortController(); void adminFetch(`/api/admin/branches/${state.branch}/menu/portion-groups`, "staff-session", { signal: c.signal }).then(async (r) => { if (!r.ok)
        throw new Error("Unable to load existing groups."); const value = await r.json(); if (!c.signal.aborted)
        setGroups(value.groups); }).catch(e => { if (!c.signal.aborted)
        setError(e.message); }); return () => c.abort(); }, [state.branch, restored]);
    useEffect(() => { if (!state.branch || !restored)
        return; const c = new AbortController(); void request<Job[]>(state.branch, "/jobs", { signal: c.signal }).then(values => { if (!c.signal.aborted)
        setRecent(values); }).catch(e => { if (!c.signal.aborted)
        setError(e.message); }); return () => c.abort(); }, [state.branch, restored, jobId]);
    useEffect(() => { if (!jobId || !state.branch)
        return; const c = new AbortController(); let timer: ReturnType<typeof setTimeout>; const branch = state.branch, id = jobId; async function poll() { try {
        const job = await request<Job>(branch, `/jobs/${id}`, { signal: c.signal });
        const rows = await request<Result[]>(branch, `/jobs/${id}/results?page=${resultPage}`, { signal: c.signal });
        if (c.signal.aborted)
            return;
        setState(s => s.branch === branch ? { ...s, job } : s);
        setResults(rows);
        if (job.succeeded + job.failed < job.total)
            timer = setTimeout(() => void poll(), 4000);
    }
    catch (e) {
        if (!c.signal.aborted) {
            setError(e instanceof Error ? e.message : "Unable to refresh progress.");
            timer = setTimeout(() => void poll(), 10000);
        }
    } } void poll(); return () => { c.abort(); clearTimeout(timer); }; }, [jobId, state.branch, resultPage]);
    function update<K extends keyof Draft>(key: K, value: Draft[K]) { setState(s => ({ ...s, [key]: value, submissionId: null })); setReview(false); }
    async function loadCatalogue() { if (!state.branch || loading || locked)
        return; setLoading(true); setError(""); const snapshot = context; try {
        const items: WorkspaceItem[] = [];
        for (let index = 0; index < 10; index++) {
            const params = new URLSearchParams({ date: state.from, page: String(index), size: "50", filter: "ALL" });
            const data = await workspaceRequest<WorkspacePage>(state.branch, `?${params}`);
            if (query.current !== snapshot)
                return;
            if (data.totalElements > 500)
                throw new Error("This page supports 500 branch items. Use the existing individual tools for a larger catalogue.");
            items.push(...data.content);
            if (index + 1 >= data.totalPages)
                break;
        }
        setState(s => ({ ...s, rows: items.map(item => ({ item, selected: false, quantity: item.allocation ? String(item.allocation.approvedQuantity / (item.saleMode === "WEIGHT" ? 1000 : 1)) : "", ready: "" })), submissionId: null }));
        setPage(0);
        setResults([]);
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "Unable to load catalogue.");
    }
    finally {
        setLoading(false);
    } }
    async function reloadFailed() {
        if (!state.job || state.submissionId !== state.job.id || active || loading) return;
        if (state.from < businessDateOffset(0)) { setError("Choose a fresh plan: the original service date is now in the past."); return; }
        setLoading(true); setError("");
        const snapshot = context, id = state.job.id;
        try {
            const failed = new Set<number>();
            for (let index = 0; index < 10; index++) {
                const rows = await request<Result[]>(state.branch, `/jobs/${id}/results?page=${index}`);
                rows.filter(r => r.status === "FAILED").forEach(r => failed.add(r.product_id));
                if (rows.length < 50) break;
            }
            const fresh: WorkspaceItem[] = [];
            for (let index = 0; index < 10; index++) {
                const data = await workspaceRequest<WorkspacePage>(state.branch, `?${new URLSearchParams({date: state.from, page: String(index), size: "50", filter: "ALL"})}`);
                if (data.totalElements > 500) throw new Error("Catalogue exceeds this page’s 500-item limit.");
                fresh.push(...data.content); if (index + 1 >= data.totalPages) break;
            }
            if (query.current !== snapshot) return;
            const drafts = new Map(state.rows.map(r => [r.item.productId, r]));
            setState(s => ({...s, job: null, submitted: false, submissionId: null, rows: fresh.map(item => ({item, selected: failed.has(item.productId), quantity: drafts.get(item.productId)?.quantity ?? "", ready: ""}))}));
            setResults([]); setResultPage(0); setPage(0); setSearch(""); setReview(false);
        } catch (e) { setError(e instanceof Error ? e.message : "Unable to reload failed items."); }
        finally { setLoading(false); }
    }
    function rowValue(id: number, field: "quantity" | "ready", value: string) { update("rows", state.rows.map(row => row.item.productId === id ? { ...row, [field]: value } : row)); }
    function selectGroup(group: Group) { const ids = new Set(group.choices.map(c => c.productId)); update("rows", state.rows.map(row => ids.has(row.item.productId) ? { ...row, selected: true } : row)); }
    const selected = state.rows.filter(r => r.selected);
    const filtered = state.rows.filter(r => `${r.item.name} ${r.item.code} ${r.item.categoryName}`.toLowerCase().includes(search.toLowerCase()));
    const visible = filtered.slice(page * 25, page * 25 + 25);
    const days = Math.round((Date.parse(state.through) - Date.parse(state.from)) / 86400000) + 1;
    const prepared = state.method === "READY_STOCK";
    const today = state.from === businessDateOffset(0);
    function validate() { if (!state.applyInventory && !state.applyHours)
        return "Select inventory or service-hour changes."; if (!selected.length || !Number.isFinite(days) || days < 1 || days > 60 || days * selected.length > 10000)
        return "Select items and a valid date range, within 60 days and 10,000 item-date allocations."; if (state.applyHours && (!state.opens || !state.closes || state.opens === state.closes))
        return "Enter different opening and closing times."; if (!state.weekdays || !state.reason.trim())
        return "Select at least one weekday and provide a reason."; if (state.applyInventory && selected.some(r => Number(r.quantity) <= 0 || !Number.isFinite(Number(r.quantity)) || (r.item.saleMode === "UNIT" && !Number.isInteger(Number(r.quantity))) || (prepared && today && (!Number.isFinite(Number(r.ready)) || Number(r.ready) <= 0 || Number(r.ready) > Number(r.quantity) || (r.item.saleMode === "UNIT" && !Number.isInteger(Number(r.ready)))))))
        return "Enter positive quantities, whole pieces, and today’s actual prepared stock no larger than its allocation."; return ""; }
    async function submit() { if (lock.current || active)
        return; const issue = validate(); if (issue) {
        setError(issue);
        return;
    } lock.current = true; setBusy(true); setError(""); const submissionId = state.submissionId || crypto.randomUUID(); const captured = { ...state, submissionId }; setState(captured); try {
        const body = { submissionId, options: { fromDate: state.from, throughDate: state.through, method: state.method, applyInventory: state.applyInventory, applyPolicy: state.applyPolicy, openPurchases: state.openPurchases, applyHours: state.applyHours, opens: state.applyHours ? state.opens : null, closes: state.applyHours ? state.closes : null, weekdays: state.weekdays, enableHours: state.enableHours, reason: state.reason }, items: selected.map(row => { const factor = row.item.saleMode === "WEIGHT" ? 1000 : 1; return { productId: row.item.productId, branchVersion: row.item.branchVersion, policyVersion: row.item.policyVersion, allocationVersion: row.item.allocationVersion, quantity: state.applyInventory ? Math.round(Number(row.quantity) * factor * 1000) / 1000 : null, readyQuantity: state.applyInventory && prepared && today ? Math.round(Number(row.ready) * factor * 1000) / 1000 : null }; }) };
        const job = await request<Job>(state.branch, "/jobs", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
        setState({ ...captured, job, submitted: true });
        setReview(false);
    }
    catch (e) {
        setError(e instanceof Error ? e.message : "Submission failed. Retry with the same plan.");
    }
    finally {
        setBusy(false);
        lock.current = false;
    } }
    if (!profile)
        return <p>Sign in to manage inventory.</p>;
    if (!canManage)
        return <p>Menu management, inventory view and inventory management permissions are required.</p>;
    return <main className={styles.root}><div className={styles.heading}><div><p className={styles.eyebrow}>Separate bulk workspace</p><h1>Inventory control centre</h1><p>One reviewed plan for quantities, selling method, physical readiness and service hours. Existing individual pages remain available.</p></div><Link href="/admin/inventory/setup">Individual inventory tools ↗</Link></div>
 {storageError && <p role="alert" className={styles.error}>This browser cannot save the draft. Keep this tab open until submission. Submitted backend jobs remain available under Recent jobs.</p>}
 {recent.length > 0 && !state.job && <details className={styles.panel}><summary>Recent backend jobs · recover progress</summary>{recent.map(job => <button key={job.id} onClick={() => { setState(s => ({ ...s, job, submitted: true })); setResultPage(0); }}>{job.id.slice(0, 8)} · {job.succeeded} succeeded · {job.failed} failed · {job.total - job.succeeded - job.failed} remaining</button>)}</details>}
 {error && <p role="alert" className={styles.error}>{error}</p>}
 <fieldset disabled={!restored || locked || loading} className={styles.panel}><h2>1. Choose branch and dates</h2><div className={styles.fields}><label>Branch<select value={state.branch} onChange={e => { setState(s => ({ ...s, branch: Number(e.target.value), rows: [], job: null, submitted: false, submissionId: null })); setGroups([]); setResults([]); }}>{branches.map(b => <option key={b.id} value={b.id}>{b.name}</option>)}</select></label><label>From (IST)<input type="date" min={businessDateOffset(0)} value={state.from} onChange={e => { update("from", e.target.value); setState(s => ({ ...s, rows: [] })); }}/></label><label>Through (IST)<input type="date" min={state.from} max={businessDateOffset(60)} value={state.through} onChange={e => update("through", e.target.value)}/></label></div><p className={styles.help}>Ready confirmations apply to today only. Future dates receive planned allocations; food is not automatically marked physically ready.</p><button onClick={() => void loadCatalogue()} type="button">{loading ? "Loading items…" : "Load branch items"}</button></fieldset>
 <fieldset disabled={!restored || locked || loading} className={styles.panel}><h2>2. Set the plan</h2><label className={styles.check}><input type="checkbox" checked={state.applyInventory} onChange={e => update("applyInventory", e.target.checked)}/>Apply inventory quantities, configuration and readiness</label><p className={styles.help}>Switch this off to update service hours only, without changing stock or entering quantities.</p><div className={styles.fields}><label>Selling method <InventoryInfo helpKey="controlMode"/><select value={state.method} onChange={e => update("method", e.target.value)}><option value="READY_STOCK">Prepared stock</option><option value="DAILY_PRODUCTION">Made to order · daily capacity</option><option value="MANUAL">Manual daily allocation</option></select></label><label>Reason · retained with stock audit <input maxLength={500} value={state.reason} onChange={e => update("reason", e.target.value)}/></label></div><label className={styles.check}><input type="checkbox" checked={state.applyPolicy} onChange={e => update("applyPolicy", e.target.checked)}/>Apply selling method to existing policies; retain buffers, ceilings and lead times</label><label className={styles.check}><input type="checkbox" checked={state.openPurchases} onChange={e => update("openPurchases", e.target.checked)}/>Explicitly open selected items for new purchases</label><p className={styles.help}>Leave this off to retain manual Unavailable overrides. Automation and opening hours never override them. Stock reservations and paid commitments are retained.</p><label className={styles.check}><input type="checkbox" checked={state.applyHours} onChange={e => update("applyHours", e.target.checked)}/>Apply common service hours to selected items</label>{state.applyHours && <><div className={styles.fields}><label>Opens (IST)<input type="time" value={state.opens} onChange={e => update("opens", e.target.value)}/></label><label>Closes (IST)<input type="time" value={state.closes} onChange={e => update("closes", e.target.value)}/></label></div><div className={styles.days}>{["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"].map((day, index) => <label key={day}><input type="checkbox" checked={!!(state.weekdays & (1 << index))} onChange={e => update("weekdays", e.target.checked ? state.weekdays | (1 << index) : state.weekdays & ~(1 << index))}/>{day}</label>)}</div><label className={styles.check}><input type="checkbox" checked={state.enableHours} onChange={e => update("enableHours", e.target.checked)}/>Enable service-hour enforcement for the whole branch</label><p className={styles.help}>Opening is inclusive; closing is exclusive. Overnight times use the opening weekday. If branch enforcement is off, hours have no effect until enabled. Enabling it also activates previously saved rules for other items. Sold-out switches and dependencies are retained.</p></>}</fieldset>
 <fieldset disabled={!restored || locked || loading} className={styles.panel}><h2>3. Select items and review quantities</h2><div className={styles.toolbar}><input aria-label="Search items" placeholder="Search name, code or category" value={search} onChange={e => { setSearch(e.target.value); setPage(0); }}/><button type="button" onClick={() => update("rows", state.rows.map(r => filtered.some(f => f.item.productId === r.item.productId) ? { ...r, selected: true } : r))}>Select all matching</button><button type="button" onClick={() => update("rows", state.rows.map(r => ({ ...r, selected: false })))}>Clear selection</button><span>{selected.length} selected</span></div><details><summary>Select an existing portion group</summary><div className={styles.toolbar}>{groups.map(group => <button type="button" key={group.key} onClick={() => selectGroup(group)}>{group.title} · {group.choices.length} variants</button>)}</div><p className={styles.help}>Each variant keeps its own quantity, price and stock. Group identity is preserved; this page never guesses groups from item names.</p></details>
 {state.applyInventory && <details><summary>Fill values for selected items</summary>{(["WEIGHT", "UNIT"] as const).map(unit => <div key={unit} className={styles.fields}><label>Allocation · {unit === "WEIGHT" ? "kg" : "pieces"}<input type="number" min="0" step={unit === "WEIGHT" ? ".001" : "1"} value={defaults[unit]} onChange={e => setDefaults(s => ({ ...s, [unit]: e.target.value }))}/></label><button type="button" onClick={() => update("rows", state.rows.map(r => r.selected && r.item.saleMode === unit ? { ...r, quantity: defaults[unit] } : r))}>Fill allocation</button>{prepared && today && <><label>Verified prepared · {unit === "WEIGHT" ? "kg" : "pieces"}<input type="number" min="0" step={unit === "WEIGHT" ? ".001" : "1"} value={readyDefaults[unit]} onChange={e => setReadyDefaults(s => ({ ...s, [unit]: e.target.value }))}/></label><button type="button" onClick={() => update("rows", state.rows.map(r => r.selected && r.item.saleMode === unit ? { ...r, ready: readyDefaults[unit] } : r))}>Fill verified stock count</button></>}</div>)}</details>}
 <div className={styles.items}>{visible.map(row => { const group = groups.find(g => g.choices.some(c => c.productId === row.item.productId)); const unit = row.item.saleMode === "WEIGHT" ? "kg" : "pieces"; return <article className={styles.item} key={row.item.productId}><label className={styles.check}><input type="checkbox" checked={row.selected} onChange={e => update("rows", state.rows.map(r => r.item.productId === row.item.productId ? { ...r, selected: e.target.checked } : r))}/><strong>{row.item.name}</strong></label><p className={styles.help}>{row.item.code} · {row.item.categoryName} · {unit}{group ? ` · Group: ${group.title}` : ""} · {row.item.available ? "New purchases enabled" : "Manually unavailable"}</p>{state.applyInventory && <div className={styles.fields}><label>Allocation ({unit}) <InventoryInfo helpKey="approved"/><input aria-label={`${row.item.name} allocation`} type="number" min=".001" step={unit === "kg" ? ".001" : "1"} value={row.quantity} onChange={e => rowValue(row.item.productId, "quantity", e.target.value)}/></label>{prepared && today && <label>Physically ready ({unit}) <InventoryInfo helpKey="ready"/><input aria-label={`${row.item.name} ready`} type="number" min=".001" step={unit === "kg" ? ".001" : "1"} value={row.ready} onChange={e => rowValue(row.item.productId, "ready", e.target.value)}/></label>}</div>}</article>; })}</div><div className={styles.toolbar}><button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Previous</button><span>Page {page + 1} · 25 items per page</span><button disabled={(page + 1) * 25 >= filtered.length} onClick={() => setPage(p => p + 1)}>Next</button></div></fieldset>
 {!state.submitted && <section className={styles.panel}><h2>4. Review and run</h2><p>{selected.length} items × {Number.isFinite(days) ? Math.max(0, days) : 0} days · {selected.length * Math.max(0, days || 0)} item-date allocations</p><p className={styles.help}>The backend processes five items per batch. Each item is atomic across the selected dates. Failure of one item does not undo successful items. The job survives refresh and server restart.</p><label className={styles.check}><input type="checkbox" checked={review} disabled={locked} onChange={e => setReview(e.target.checked)}/>I reviewed quantities, service hours and any physical-ready confirmations.</label><button className={styles.primary} disabled={!review || locked || loading} onClick={() => void submit()}>{busy ? "Submitting…" : state.applyInventory ? "Apply configuration, allocation and readiness" : "Apply service hours"}</button></section>}
 {state.job && <section className={styles.panel}><h2>Backend job progress</h2><p>{state.job.succeeded} succeeded · {state.job.failed} failed · {state.job.total - state.job.succeeded - state.job.failed} remaining</p><progress max={state.job.total} value={state.job.succeeded + state.job.failed} aria-label="Inventory job progress"/><p className={styles.help}>Job {state.job.id}. Results are shown in pages of 50. Completed details are retained for 30 days; summary counts and inventory audits remain.</p>{results.map(row => <p key={row.product_id} className={row.status === "FAILED" ? styles.error : styles.help}><strong>{row.name}</strong> · {row.status}{row.error ? ` · ${row.error}` : ""}</p>)}<div className={styles.toolbar}><button disabled={resultPage === 0} onClick={() => setResultPage(p => p - 1)}>Previous results</button><button disabled={results.length < 50} onClick={() => setResultPage(p => p + 1)}>Next results</button>{!active && state.job.failed > 0 && state.submissionId === state.job.id && <button disabled={loading} onClick={() => void reloadFailed()}>Reload failed items only</button>}{!active && <button disabled={loading} onClick={() => { setState(s => ({ ...s, rows: [], job: null, submissionId: null, submitted: false })); setResults([]); setReview(false); setResultPage(0); }}>Start a fresh plan</button>}</div><p className={styles.help}>With the original draft, reload failed items to select them with fresh versions and verify today’s physical stock again. When recovering another job, start a fresh plan and select its failed items. Successful items are excluded from automatic retry. Existing individual tools remain available.</p></section>}
 </main>;
}
