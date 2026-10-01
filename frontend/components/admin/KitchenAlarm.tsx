"use client";

import {useCallback, useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getActiveBranches} from "@/services/branchApi";
import {useTranslation} from "@/lib/language";
import {adminFetch} from "@/services/adminApi";

/** Lives in the admin layout so navigating to an order does not silence due work. */
export default function KitchenAlarm() {
    const {authorization, profile, hasPermission} = useAdminAuth();
    const translate = useTranslation();
    const features = useStorefrontFeatures();
    const canStart = hasPermission("ORDER_START_PREPARATION"), canReady = hasPermission("ORDER_MARK_READY");
    const permitted = !!authorization && hasPermission("ORDER_VIEW") && (canStart || canReady) && !!features?.adminPreparationBoard;
    const [enabled, setEnabled] = useState(false);
    const [message, setMessage] = useState("");
    const audio = useRef<AudioContext | null>(null);
    const due = useRef(0);
    const fresh = useRef(0);
    const revision = useRef(0);
    const check = useCallback(async (signal?: AbortSignal) => {
        if (!permitted || !authorization || !profile) return;
        const current = ++revision.current;
        try {
            const activeBranches = await getActiveBranches(signal);
            const branches = profile.roleName === "OWNER_ADMIN" ? activeBranches
                : activeBranches.filter(branch => profile.branchIds.includes(branch.id));
            const counts = await Promise.all(branches.map(async branch => {
                const response = await adminFetch(`/api/admin/orders/planning/alerts?branchId=${branch.id}`, authorization, {signal, cache: "no-store"});
                if (!response.ok) throw new Error();
                const value = await response.json();
                if (![value.needsPreparation, value.readyOverdue].every(count => Number.isSafeInteger(count) && count >= 0)) throw new Error();
                return {waiting: canStart ? value.needsPreparation as number : 0, ready: canReady ? value.readyOverdue as number : 0};
            }));
            if (signal?.aborted || current !== revision.current) return;
            const waiting = counts.reduce((total, count) => total + count.waiting, 0);
            const ready = counts.reduce((total, count) => total + count.ready, 0);
            due.current = waiting + ready;
            fresh.current = Date.now();
            setMessage(due.current ? [waiting ? `${waiting} ${translate("orders need preparation")}` : "", ready ? `${ready} ${translate("orders are overdue for ready")}` : ""].filter(Boolean).join(" · ") + ". " + translate("Start KOT or mark the completed orders ready to stop the alarm.") : translate("No orders need action. Alarm is watching for new work."));
        } catch {
            if (!signal?.aborted && current === revision.current) {
                due.current = 0; fresh.current = 0;
                setMessage("Cannot check preparation alerts. Reconnect and refresh the orders.");
            }
        }
    }, [permitted, authorization, profile, translate, canStart, canReady]);
    useEffect(() => {
        if (!enabled || !permitted) return;
        const controller = new AbortController();
        const refresh = () => {if (navigator.onLine && document.visibilityState === "visible") void check(controller.signal);};
        refresh();
        const timer = window.setInterval(refresh, 15000);
        window.addEventListener("gokul-kitchen-changed", refresh);
        window.addEventListener("online", refresh);
        document.addEventListener("visibilitychange", refresh);
        return () => {controller.abort(); due.current = 0; fresh.current = 0; clearInterval(timer); window.removeEventListener("gokul-kitchen-changed", refresh); window.removeEventListener("online", refresh); document.removeEventListener("visibilitychange", refresh);};
    }, [enabled, permitted, check, profile?.staffId]);
    useEffect(() => {
        if (!enabled || !permitted) return;
        const timer = window.setInterval(() => {
            if (!audio.current || due.current < 1 || Date.now() - fresh.current > 45000 || !navigator.onLine || document.visibilityState !== "visible") return;
            const ring = async () => {
                try {
                    // Prevent duplicate chimes from multiple admin tabs.
                    try {const key = "gokul-kitchen-last-chime"; if (Date.now() - Number(localStorage.getItem(key)) < 1700) return; localStorage.setItem(key, String(Date.now()));} catch { /* private browsing */ }
                    const context = audio.current!;
                    await context.resume();
                    for (let i = 0; i < 3; i++) {
                        const tone = context.createOscillator(), gain = context.createGain();
                        tone.connect(gain); gain.connect(context.destination); tone.type = "triangle"; tone.frequency.value = i % 2 ? 1046 : 784;
                        gain.gain.setValueAtTime(.65, context.currentTime + i * .4);
                        gain.gain.exponentialRampToValueAtTime(.001, context.currentTime + i * .4 + .3);
                        tone.start(context.currentTime + i * .4); tone.stop(context.currentTime + i * .4 + .35);
                    }
                } catch {setEnabled(false); setMessage("Sound was blocked. Enable the kitchen alarm again.");}
            };
            if (navigator.locks) void navigator.locks.request("gokul-kitchen-alarm", {ifAvailable: true}, lock => lock ? ring() : undefined);
            else void ring();
        }, 2000);
        return () => clearInterval(timer);
    }, [enabled, permitted]);
    useEffect(() => () => {void audio.current?.close();}, []);
    if (!permitted) return null;
    return <aside className="mx-4 my-3 rounded-2xl border border-amber-300 bg-amber-50 p-4 text-sm text-slate-900" aria-label="Kitchen alarm">
        <button type="button" disabled={enabled} className="min-h-11 rounded-xl bg-teal-900 px-4 font-bold text-white disabled:opacity-70" onClick={async () => {
            try {audio.current ??= new AudioContext(); await audio.current.resume(); setEnabled(true);}
            catch {setMessage("This device could not enable sound. Use staff push alerts.");}
        }}>{translate(enabled ? "Kitchen alarm enabled" : "Enable kitchen alarm")}</button>
        <p role="status" className="mt-2">{translate(message || "Enable sound once per session. It repeats until waiting orders enter KOT and overdue preparing orders are marked ready.")}</p>
        <p className="mt-1">{translate("Keep the PWA open for continuous sound. Locked-screen alerts use")} <a className="underline" href="/admin/staff-notifications">{translate("staff push settings")}</a>{translate("; phone sound settings apply.")}</p>
    </aside>;
}
