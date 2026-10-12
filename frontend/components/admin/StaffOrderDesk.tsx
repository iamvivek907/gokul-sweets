"use client";
import { useCallback, useEffect, useRef, useState } from "react";
import Link from "next/link";
import { useSearchParams, usePathname, useRouter } from "next/navigation";
import { T, useTranslation } from "@/lib/language";
import { formatBusinessTime, formatBusinessTimestamp } from "@/lib/businessTime";
import { formatWeight } from "@/lib/orderQuantity";
import { orderDisplayNumber } from "@/lib/orderDisplayNumber";
import { canStartKitchenOrder } from "@/lib/kitchenActions";
import { staffDeskRequest, type DeskRow, type DeskPlan, type DeskItem } from "@/services/staffDeskApi";
import type { AdminOrderDetail, AdminBatchPreparationResponse } from "@/types/adminOrders";
import PickupHandoverAction from "./PickupHandoverAction";
import OrderDemandPanel from "./OrderDemandPanel";
import DeskReschedule from "./DeskReschedule";
import styles from "./StaffOrderDesk.module.css";
const itemQuantity = (i: DeskItem) => i.saleMode === "WEIGHT" ? formatWeight(i.weightGrams) : `${i.quantity} pcs`;
type Props = {
    branchId: number;
    authorization: string;
    canStart: boolean;
    canReady: boolean;
    canPickup: boolean;
    canPlan: boolean;
    canConfigure: boolean;
    canReschedule: boolean;
};
export default function StaffOrderDesk({ branchId, authorization, canStart, canReady, canPickup, canPlan, canConfigure, canReschedule }: Props) {
    const tr = useTranslation(), router = useRouter(), pathname = usePathname(), searchParams = useSearchParams();
    const defaultView = canStart || canReady ? 'prepare' : canPickup ? 'handover' : 'scheduled';
    const requested = searchParams.get('desk'), view = (requested === 'demand' && canPlan) || (requested === 'prepare' && (canStart || canReady)) || (requested === 'handover' && canPickup) || requested === 'scheduled' ? requested : defaultView;
    const [date, setDate] = useState(''), [time, setTime] = useState(''), [pages, setPages] = useState({ waiting: 0, preparing: 0, other: 0 }), [mobileLane, setMobileLane] = useState(canStart ? 'waiting' : 'preparing');
    const [data, setData] = useState<{
        scope: string;
        plans: DeskPlan[];
    } | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState(''), [result, setResult] = useState(''), [busy, setBusy] = useState(false), [selected, setSelected] = useState<Map<string, DeskRow>>(new Map()), [input, setInput] = useState(''), [number, setNumber] = useState(''), [lookup, setLookup] = useState<AdminOrderDetail | null>(null), [modal, setModal] = useState<{
        kind: 'bulk' | 'detail' | 'reschedule';
        row?: DeskRow;
    } | null>(null);
    const inFlight = useRef(false);
    const dialog = useRef<HTMLDialogElement>(null), active = useRef<AbortController | null>(null), life = useRef<AbortController | null>(null), revision = useRef(0), busyRef = useRef(false);
    const scope = JSON.stringify([branchId, view, date, time, pages, number]);
    useEffect(() => { const c = new AbortController(); life.current = c; return () => { c.abort(); active.current?.abort(); }; }, []);
    useEffect(() => { const timer = window.setTimeout(() => setNumber(input), 300); return () => clearTimeout(timer); }, [input]);
    const refresh = useCallback(async () => {
        active.current?.abort();
        const c = new AbortController();
        active.current = c;
        const current = ++revision.current;
        inFlight.current = true;
        if (view === 'demand') {
            inFlight.current = false;
            setLoading(false);
            return;
        }
        try {
            if (number) {
                const detail = await staffDeskRequest<AdminOrderDetail>(`/api/admin/orders/number/${encodeURIComponent(number)}`, authorization, {}, c.signal);
                if (detail.branchId !== branchId)
                    throw new Error('Order not found in this branch.');
                if (current === revision.current && !c.signal.aborted) {
                    setLookup(detail);
                    setData({ scope, plans: [] });
                    setError('');
                }
            }
            else {
                const filters = view === 'prepare' ? [['WAITING', pages.waiting], ['IN_PROGRESS', pages.preparing]] : view === 'handover' ? [['HANDOVER', pages.other]] : [['SCHEDULED', pages.other]];
                const plans = await Promise.all(filters.map(([filter, page]) => { const query = new URLSearchParams({ branchId: String(branchId), filter: String(filter), page: String(page) }); if (date)
                    query.set('date', date); if (time && date)
                    query.set('start', time); return staffDeskRequest<DeskPlan>(`/api/admin/orders/planning?${query}`, authorization, {}, c.signal); }));
                if (current === revision.current && !c.signal.aborted) {
                    // Other staff can drain a queue while this terminal is on a later page.
                    // Move each affected lane back to its last valid page, then refetch it.
                    const keys: ('waiting' | 'preparing' | 'other')[] = view === 'prepare' ? ['waiting', 'preparing'] : ['other'];
                    const corrected = { ...pages };
                    plans.forEach((plan, index) => {
                        const key = keys[index];
                        corrected[key] = Math.min(pages[key], Math.max(0, Math.ceil(plan.total / 20) - 1));
                    });
                    if (keys.some(key => corrected[key] !== pages[key])) {
                        setPages(corrected);
                        return;
                    }
                    setLookup(null);
                    setData({ scope, plans });
                    setError('');
                    setSelected(previous => { const next = new Map(previous); for (const row of plans.flatMap(p => p.orders)) {
                        if (next.has(row.orderNumber)) {
                            if (canStartKitchenOrder(row))
                                next.set(row.orderNumber, row);
                            else
                                next.delete(row.orderNumber);
                        }
                    } return next; });
                }
            }
        }
        catch (e) {
            if (current === revision.current && !c.signal.aborted) {
                setError(e instanceof Error ? e.message : 'Could not load order desk.');
                setLookup(null);
            }
        }
        finally {
            if (current === revision.current && !c.signal.aborted) {
                inFlight.current = false;
                setLoading(false);
            }
        }
    }, [authorization, branchId, view, pages, date, time, number, scope, setLoading, setLookup, setData, setSelected, setError, setPages]);
    useEffect(() => { const initial = window.setTimeout(() => { setLoading(true); setError(''); void refresh(); }, 0); const check = () => { if (document.visibilityState === 'visible' && navigator.onLine && !busyRef.current && !inFlight.current)
        void refresh(); }; const timer = window.setInterval(check, 15000); window.addEventListener('online', check); document.addEventListener('visibilitychange', check); return () => { clearTimeout(initial); clearInterval(timer); active.current?.abort(); window.removeEventListener('online', check); document.removeEventListener('visibilitychange', check); }; }, [refresh]);
    useEffect(() => { if (modal && !dialog.current?.open)
        dialog.current?.showModal(); }, [modal]);
    const plans = data?.scope === scope ? data.plans : [], waiting = plans[0], preparing = plans[1], pending = loading || data?.scope !== scope, disabled = busy || pending || !!error;
    function changeView(next: string) { const params = new URLSearchParams(searchParams); params.set('desk', next); router.replace(`${pathname}?${params}`, { scroll: false }); setPages({ waiting: 0, preparing: 0, other: 0 }); setInput(''); setNumber(''); setModal(null); }
    function close() { if (!busyRef.current) {
        setModal(null);
        dialog.current?.close();
    } }
    async function mutate(path: string, body: object, success: string) { if (busyRef.current)
        return; busyRef.current = true; setBusy(true); setError(''); setResult(''); try {
        await staffDeskRequest(path, authorization, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) }, life.current?.signal);
        if (!life.current?.signal.aborted) {
            setResult(success);
            window.dispatchEvent(new Event('gokul-kitchen-changed'));
            await refresh();
        }
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : 'Could not update order.');
    }
    finally {
        busyRef.current = false;
        if (!life.current?.signal.aborted)
            setBusy(false);
    } }
    async function start() { if (busyRef.current || !canStart || !selected.size)
        return; busyRef.current = true; setBusy(true); setError(''); try {
        const response = await staffDeskRequest<AdminBatchPreparationResponse>('/api/admin/orders/queue/start-selected', authorization, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ branchId, orderNumbers: [...selected.keys()] }) }, life.current?.signal);
        if (life.current?.signal.aborted)
            return;
        setResult(`${response.started} started · ${response.skipped} skipped. ${response.results.filter(r => r.result !== 'STARTED').map(r => `${orderDisplayNumber(selected.get(r.orderNumber) ?? r)}: ${r.message}`).join(' ')}`);
        setSelected(new Map());
        setPages({ waiting: 0, preparing: 0, other: 0 });
        setModal(null);
        dialog.current?.close();
        window.dispatchEvent(new Event('gokul-kitchen-changed'));
        await refresh();
    }
    catch (e) {
        if (!life.current?.signal.aborted)
            setError(e instanceof Error ? e.message : 'Could not start orders.');
    }
    finally {
        busyRef.current = false;
        if (!life.current?.signal.aborted)
            setBusy(false);
    } }
    function card(row: DeskRow) { return <article key={row.orderNumber} className={styles.order}><div className={styles.orderTop}><div className={styles.number}>{canStart && canStartKitchenOrder(row) && <label className={styles.check}><input type="checkbox" disabled={disabled || (!selected.has(row.orderNumber) && selected.size >= 50)} aria-label={`${tr('Select')} ${orderDisplayNumber(row)}`} checked={selected.has(row.orderNumber)} onChange={e => setSelected(current => { const next = new Map(current); if (e.target.checked)
        next.set(row.orderNumber, row);
    else
        next.delete(row.orderNumber); return next; })}/></label>}{orderDisplayNumber(row)}</div><span className={`${styles.badge} ${row.bucket === 'OVERDUE' ? styles.overdue : row.orderStatus === 'READY_FOR_PICKUP' ? styles.ready : ''}`}>{tr(row.bucket === 'OVERDUE' ? 'Overdue' : row.orderStatus === 'PREPARING' ? 'Preparing' : row.orderStatus === 'CONFIRMED' ? (row.bucket === 'SCHEDULED' ? 'Scheduled' : 'To prepare') : row.orderStatus === 'READY_FOR_PICKUP' ? 'Ready' : row.orderStatus === 'PICKED_UP' ? 'Handed over' : row.orderStatus)}</span></div><strong>{row.date} · {formatBusinessTime(row.start)}–{formatBusinessTime(row.end)} IST</strong><p className={styles.muted}>{row.customerName} · {tr(row.fulfillmentType === 'DELIVERY' ? 'Delivery' : 'Pickup')}</p><ul className={styles.items}>{row.items.map((i, index) => <li key={`${i.productId}:${index}`}><strong>{itemQuantity(i)}</strong> {i.productName}</li>)}</ul>{row.orderStatus === 'CONFIRMED' && row.preparationAt && <p className={styles.muted}>{row.earlyPreparation ? tr('Counter only · early packing allowed') : <><T text="Preparation opens"/> {formatBusinessTimestamp(row.preparationAt, { dateStyle: 'medium', timeStyle: 'short' })}</>}</p>}<div className={styles.actions}><button disabled={busy} onClick={() => setModal({ kind: 'detail', row })}><T text="Details"/></button>{canStart && canStartKitchenOrder(row) && <button className={styles.primary} disabled={disabled} onClick={() => { setSelected(new Map([[row.orderNumber, row]])); setModal({ kind: 'bulk' }); }}><T text="Start preparation"/></button>}{canReady && row.orderStatus === 'PREPARING' && <button className={styles.primary} disabled={disabled} onClick={() => void mutate(`/api/admin/orders/${encodeURIComponent(row.orderNumber)}/status`, { status: row.fulfillmentType === 'DELIVERY' ? 'READY_FOR_DELIVERY' : 'READY_FOR_PICKUP' }, tr('Order marked ready.'))}><T text="Mark ready"/></button>}{canPickup && row.orderStatus === 'READY_FOR_PICKUP' && row.fulfillmentType !== 'DELIVERY' && <PickupHandoverAction compact orderNumber={row.orderNumber} customerOrderNumber={row.customerOrderNumber} authorization={authorization} disabled={disabled} onCompleted={() => { window.dispatchEvent(new Event('gokul-kitchen-changed')); void refresh(); }}/>}</div></article>; }
    function pager(plan: DeskPlan | undefined, key: 'waiting' | 'preparing' | 'other') { return <div className={styles.pager}><button disabled={pending || busy || pages[key] === 0} onClick={() => setPages(p => ({ ...p, [key]: p[key] - 1 }))}><T text="Previous"/></button><span>{pages[key] + 1} / {Math.max(1, Math.ceil((plan?.total ?? 0) / 20))}</span><button disabled={pending || busy || (pages[key] + 1) * 20 >= (plan?.total ?? 0)} onClick={() => setPages(p => ({ ...p, [key]: p[key] + 1 }))}><T text="Next"/></button></div>; }
    const selectedItems = new Map<string, {
        name: string;
        mode: string;
        amount: number;
    }>();
    for (const row of selected.values())
        for (const i of row.items) {
            const key = `${i.productId}:${i.saleMode}:${i.productName}`, value = selectedItems.get(key) ?? { name: i.productName, mode: i.saleMode, amount: 0 };
            value.amount += i.saleMode === 'WEIGHT' ? (i.weightGrams ?? 0) : i.quantity;
            selectedItems.set(key, value);
        }
    return <section className={styles.desk}><nav className={styles.tabs} aria-label={tr('Order workspace')}>{(canStart || canReady) && <button aria-pressed={view === 'prepare'} className={view === 'prepare' ? styles.active : ''} disabled={busy} onClick={() => changeView('prepare')}><T text="Prepare / KOT"/></button>}{canPickup && <button aria-pressed={view === 'handover'} className={view === 'handover' ? styles.active : ''} disabled={busy} onClick={() => changeView('handover')}><T text="Handover"/></button>}{(canStart || canReady || canPlan) && <button aria-pressed={view === 'scheduled'} className={view === 'scheduled' ? styles.active : ''} disabled={busy} onClick={() => changeView('scheduled')}><T text="Scheduled"/></button>}{canPlan && <button aria-pressed={view === 'demand'} className={view === 'demand' ? styles.active : ''} disabled={busy} onClick={() => changeView('demand')}><T text="Demand & exports"/></button>}</nav>
        {result && <p role="status" className={styles.success}>{tr(result)}</p>}{error && <p role="alert" className={styles.error}>{tr(error)} <button disabled={busy} onClick={() => void refresh()}><T text="Retry"/></button></p>}
        {view === 'demand' ? canPlan && <OrderDemandPanel branchId={branchId} authorization={authorization} canConfigure={canConfigure}/> : <><div className={styles.filters}><label className={styles.search}><span aria-hidden="true">#</span><input type="search" inputMode="numeric" aria-label={tr('Find an order number')} placeholder={tr('Order number')} value={input} disabled={busy} onChange={e => setInput(e.target.value.replace(/[^0-9]/g, '').slice(0, 18))}/></label><label><T text="Pickup date"/><input type="date" value={date} disabled={busy} onChange={e => { setDate(e.target.value); setTime(''); setPages({ waiting: 0, preparing: 0, other: 0 }); }}/></label><label><T text="Pickup time"/><select value={time} disabled={!date || busy} onChange={e => { setTime(e.target.value); setPages({ waiting: 0, preparing: 0, other: 0 }); }}><option value="">{tr('All times')}</option>{Array.from(new Set(plans[0]?.slots.filter(s => s.date === date).map(s => s.start) ?? [])).sort().map(t => <option key={t} value={t}>{formatBusinessTime(t)}</option>)}</select></label><button disabled={busy} onClick={() => { setDate(''); setTime(''); setInput(''); setNumber(''); setPages({ waiting: 0, preparing: 0, other: 0 }); }}><T text="Clear filters"/></button></div>
        {pending && !error ? <p role="status" className={styles.empty}><T text="Loading order desk…"/></p> : number ? lookup && data?.scope === scope ? <><p className={styles.notice}>{tr(lookup.orderStatus === 'PREPARING' ? 'This order is being prepared.' : lookup.orderStatus === 'CONFIRMED' ? 'Preparation has not started yet.' : lookup.orderStatus === 'READY_FOR_PICKUP' ? 'Ready for pickup' : lookup.orderStatus === 'PICKED_UP' ? 'This order has already been handed over.' : 'This order is not in the ready queue.')}</p>{card({ orderNumber: lookup.orderNumber, customerOrderNumber: lookup.customerOrderNumber, customerName: lookup.customerName, fulfillmentType: lookup.fulfillmentType, orderStatus: lookup.orderStatus, bucket: 'SCHEDULED', date: lookup.pickupDate ?? lookup.deliveryDate ?? '', start: lookup.pickupStartTime ?? lookup.deliveryStartTime ?? '', end: lookup.pickupEndTime ?? lookup.deliveryEndTime ?? '', preparationAt: '', earlyPreparation: false, items: lookup.items.map(i => ({ ...i, saleMode: i.saleMode ?? "UNIT", weightGrams: i.weightGrams ?? null })) })}</> : null : view === 'prepare' ? <>{canStart && <div className={styles.bulk}><button disabled={disabled || !waiting?.orders.length} onClick={() => setSelected(new Map(waiting?.orders.filter(canStartKitchenOrder).slice(0, 20).map(row => [row.orderNumber, row]) ?? []))}><T text="Select 20 eligible on this page"/></button><strong>{selected.size} <T text="selected"/></strong><button disabled={busy} onClick={() => setSelected(new Map())}><T text="Clear selection"/></button><button className={styles.primary} disabled={disabled || !selected.size} onClick={() => setModal({ kind: 'bulk' })}><T text="Start selected + KOT"/></button></div>}<p className={styles.muted}><T text="Earliest pickup first · future kitchen orders stay scheduled"/></p><div className={`${styles.tabs} ${styles.mobileTabs}`}>{['waiting', 'preparing'].map(l => <button key={l} className={mobileLane === l ? styles.active : ''} aria-pressed={mobileLane === l} onClick={() => setMobileLane(l)}>{tr(l === 'waiting' ? 'To prepare' : 'Preparing')} ({(l === 'waiting' ? waiting : preparing)?.total ?? 0})</button>)}</div><div className={styles.lanes}>{[['waiting', waiting], ['preparing', preparing]].map(([l, p]) => { const key = l as 'waiting' | 'preparing', plan = p as DeskPlan | undefined; return <section key={key} className={mobileLane !== key ? styles.mobileHide : ''}><header className={styles.laneHead}><h2>{tr(key === 'waiting' ? 'To prepare' : 'Preparing')}</h2><strong>{plan?.total ?? 0}</strong></header>{plan?.orders.map(card)}{!plan?.orders.length && <p className={styles.empty}><T text="No orders in this queue."/></p>}{pager(plan, key)}</section>; })}</div></> : <><header className={styles.laneHead}><h2>{tr(view === 'handover' ? 'Ready for pickup' : 'Later pickups')}</h2><strong>{waiting?.total ?? 0}</strong></header><div className={styles.lanes}>{waiting?.orders.map(card)}</div>{!waiting?.orders.length && <p className={styles.empty}><T text="No orders in this queue."/></p>}{pager(waiting, 'other')}</>}
        <p className={styles.muted}><T text="Automatic updates · pickup times in IST"/> {plans[0] && formatBusinessTimestamp(plans[0].generatedAt, { timeStyle: 'short' })}</p></>}
        {modal && <dialog ref={dialog} className={styles.modal} aria-labelledby="desk-modal-title" onCancel={e => { e.preventDefault(); close(); }}><header className={styles.toolbar}><h2 id="desk-modal-title">{modal.kind === 'bulk' ? `${tr('Start')} ${selected.size} ${tr('orders in KOT?')}` : orderDisplayNumber(modal.row!)}</h2><button disabled={busy} onClick={close}><T text="Close"/></button></header>{modal.kind === 'bulk' ? <><p>{[...selected.values()].map(orderDisplayNumber).join(', ')}</p><ul className={styles.items}>{[...selectedItems.values()].map((i, index) => <li key={index}><strong>{i.mode === 'WEIGHT' ? formatWeight(i.amount) : `${i.amount} pcs`}</strong> {i.name}</li>)}</ul><p className={styles.muted}><T text="Each order is checked again. Future, cancelled or already-started orders are skipped."/></p>{error && <p role="alert" className={styles.error}>{tr(error)} <button disabled={busy || loading} onClick={() => { setLoading(true); void refresh(); }}><T text="Retry"/></button></p>}<button className={styles.primary} disabled={disabled || !canStart || !selected.size} onClick={() => void start()}>{tr(busy ? 'Starting…' : 'Confirm start')}</button></> : modal.kind === 'reschedule' ? <DeskReschedule row={modal.row!} branchId={branchId} authorization={authorization} onBusy={value => { busyRef.current = value; setBusy(value); }} onDone={() => { busyRef.current = false; setBusy(false); setModal(null); dialog.current?.close(); setResult(tr('Pickup time changed.')); window.dispatchEvent(new Event('gokul-kitchen-changed')); void refresh(); }}/> : <><p>{modal.row!.customerName} · {modal.row!.date} · {formatBusinessTime(modal.row!.start)} IST</p><ul className={styles.items}>{modal.row!.items.map((i, index) => <li key={index}><strong>{itemQuantity(i)}</strong> {i.productName}</li>)}</ul><div className={styles.actions}>{modal.row!.orderStatus !== 'CONFIRMED' && <Link className={styles.link} href={`/admin/kot/order/${encodeURIComponent(modal.row!.orderNumber)}/print`}><T text="Print KOT"/></Link>}{canReschedule && modal.row!.orderStatus === 'CONFIRMED' && modal.row!.fulfillmentType !== 'DELIVERY' && <button onClick={() => setModal({ kind: 'reschedule', row: modal.row })}><T text="Change pickup"/></button>}<Link className={styles.link} href="/admin/orders"><T text="General orders & history"/></Link></div></>}</dialog>}
    </section>;
}
