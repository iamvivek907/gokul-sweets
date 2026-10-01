"use client";
import {useId, useRef, useState, type PointerEvent} from "react";
import {useTranslation} from "@/lib/language";
import styles from "./SwipeOrderAction.module.css";
type Props = {label: string; action: "start" | "ready"; busy: boolean; disabled: boolean; onComplete: () => void | Promise<void>};

/** Vertical scrolling, cancellation and a short swipe never submit an order action. */
export default function SwipeOrderAction({label, action, busy, disabled, onComplete}: Props) {
    const translate = useTranslation(), id = useId();
    const track = useRef<HTMLDivElement>(null);
    const gesture = useRef<{id: number; x: number; y: number; distance: number; cancelled: boolean} | null>(null);
    const committing = useRef(false);
    const [distance, setDistance] = useState(0), [dragging, setDragging] = useState(false);
    const [confirm, setConfirm] = useState(false), [sending, setSending] = useState(false);
    const unavailable = disabled || busy || sending;
    const maximum = () => Math.max(1, (track.current?.clientWidth ?? 280) - 56);
    async function complete() {
        if (unavailable || committing.current) return;
        committing.current = true; setSending(true); setConfirm(false);
        try { await onComplete(); }
        finally { committing.current = false; setSending(false); }
    }
    function reset(event: PointerEvent<HTMLButtonElement>) {
        gesture.current = null; setDistance(0); setDragging(false);
        if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    }
    return <div className={styles.action} data-action={action} data-disabled={unavailable}>
        <div ref={track} className={styles.track} data-dragging={dragging} aria-busy={busy || sending}>
            <div className={styles.fill} aria-hidden="true" style={{width: `${distance + 52}px`}} />
            <span className={styles.label}>{translate(busy || sending ? "Updating order…" : label)}</span>
            <span className={styles.chevrons} aria-hidden="true">››</span>
            <button type="button" className={styles.thumb} disabled={unavailable}
                aria-label={translate(action === "start" ? "Start preparation" : "Mark ready")}
                aria-expanded={confirm} aria-controls={confirm ? id : undefined}
                style={{transform: `translateX(${distance}px)`}}
                onClick={event => { if (event.detail === 0 && !unavailable) setConfirm(true); }}
                onPointerDown={event => {
                    if (unavailable || committing.current || gesture.current || !event.isPrimary || event.button !== 0) return;
                    gesture.current = {id: event.pointerId, x: event.clientX, y: event.clientY, distance: 0, cancelled: false};
                    setDragging(true); event.currentTarget.setPointerCapture(event.pointerId);
                }}
                onPointerMove={event => {
                    const current = gesture.current;
                    if (!current || current.id !== event.pointerId || current.cancelled) return;
                    const x = event.clientX - current.x, y = Math.abs(event.clientY - current.y);
                    if (y > 12 && y > Math.abs(x)) { current.cancelled = true; setDistance(0); setDragging(false); return; }
                    current.distance = Math.min(maximum(), Math.max(0, x)); setDistance(current.distance);
                }}
                onPointerUp={event => {
                    const current = gesture.current;
                    if (!current || current.id !== event.pointerId) return;
                    const horizontal = event.clientX - current.x;
                    const valid = !current.cancelled && current.distance >= maximum() * .9 && horizontal >= maximum() * .9
                        && Math.abs(event.clientY - current.y) < Math.max(24, horizontal * .5);
                    reset(event); if (valid) void complete();
                }}
                onPointerCancel={event => { if (gesture.current?.id === event.pointerId) reset(event); }}
                onLostPointerCapture={event => { if (gesture.current?.id === event.pointerId) reset(event); }}>
                {busy || sending ? <span className={styles.spinner} aria-hidden="true" /> : <svg width="24" height="24" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M5 12h14M13 6l6 6-6 6" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" /></svg>}
            </button>
        </div>
        <button type="button" className={styles.alternative} disabled={unavailable} aria-expanded={confirm}
            aria-controls={confirm ? id : undefined} onClick={() => setConfirm(!confirm)}>{translate("Or tap to confirm")}</button>
        {confirm && <div id={id} className={styles.confirm} role="group" aria-label={translate("Confirm order action")}>
            <p>{translate(action === "start" ? "Start preparation and create KOT?" : "Is this order packed and ready?")}</p>
            <div><button type="button" disabled={unavailable} onClick={() => void complete()}>{translate(action === "start" ? "Yes, start KOT" : "Yes, mark ready")}</button>
                <button type="button" disabled={busy || sending} onClick={() => setConfirm(false)}>{translate("Cancel")}</button></div>
        </div>}
    </div>;
}
