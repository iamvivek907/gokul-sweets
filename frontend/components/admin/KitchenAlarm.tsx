"use client";
import AdminHelp from "@/components/admin/AdminHelp";

import {useCallback, useEffect, useRef, useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {getActiveBranches} from "@/services/branchApi";
import {T,useTranslation} from "@/lib/language";
import {ADMIN_API_BASE_URL} from "@/lib/constants";
import {adminFetch} from "@/services/adminApi";

/** A suspended browser must not hold the cross-tab alarm lock indefinitely. */
async function resumeAlarm(context:AudioContext) {
    if(context.state==="running")return;
    let timer:ReturnType<typeof setTimeout>|undefined;
    try {await Promise.race([context.resume(),new Promise<never>((_,reject)=>{timer=setTimeout(()=>reject(new Error("Sound blocked")),2000);})]);}
    finally {clearTimeout(timer);}
}

/** Lives in the admin layout so navigating to an order does not silence due work. */
export default function KitchenAlarm() {
    const {authorization, profile, hasPermission} = useAdminAuth();
    const translate = useTranslation();
    const features = useStorefrontFeatures();
    const canStart = hasPermission("ORDER_START_PREPARATION"), canReady = hasPermission("ORDER_MARK_READY");
    const permitted = !!authorization && hasPermission("ORDER_VIEW") && (canStart || canReady) && !!features?.adminPreparationBoard;
    const [enabled, setEnabled] = useState(false);
    const [soundReady,setSoundReady] = useState(false);
    const preferenceKey = `gokul-kitchen-alarm:${ADMIN_API_BASE_URL}:${profile?.staffId}`;
    const generation = useRef(0);
    const [message, setMessage] = useState("");
    const audio = useRef<AudioContext | null>(null);
    const due = useRef(0);
    const fresh = useRef(0);
    const revision = useRef(0);
    const polling = useRef(false);
    const resuming = useRef<Promise<void> | null>(null);
    const [checksHealthy, setChecksHealthy] = useState(true);
    const activateSound = useCallback((gesture = false) => {
        // A real gesture must call resume synchronously, even if an earlier autoplay
        // attempt is still pending. Automatic recovery attempts never overlap.
        if (resuming.current && !gesture) return resuming.current;
        const current = generation.current;
        let context: AudioContext;
        try {
            if (!audio.current || audio.current.state === "closed") {
                context = new AudioContext();
                audio.current = context;
                context.onstatechange = () => {
                    if (audio.current === context) setSoundReady(context.state === "running");
                };
            } else context = audio.current;
        } catch {setSoundReady(false); return Promise.resolve();}
        const attempt = resumeAlarm(context).then(() => {
            if (current === generation.current && audio.current === context) setSoundReady(context.state === "running");
        }, () => {
            if (current === generation.current && audio.current === context) setSoundReady(context.state === "running");
        }).finally(() => {if (resuming.current === attempt) resuming.current = null;});
        resuming.current = attempt;
        return attempt;
    },[]);
    useEffect(() => {
        const current=++generation.current;
        void Promise.resolve().then(()=>{
            if(current!==generation.current)return;
            setEnabled(false);setSoundReady(false);setMessage("");
            if(permitted) {try {if(localStorage.getItem(preferenceKey)==="on")setEnabled(true);} catch { /* Private browsing. */ }}
        });
        function teardown(){generation.current=current+1;const previous=audio.current;audio.current=null;resuming.current=null;if(previous){previous.onstatechange=null;void previous.close();}}
        return teardown;
    },[permitted,preferenceKey]);
    useEffect(()=>{
        if(!enabled||!permitted)return;
        const recover=()=>{void activateSound();};
        const gesture=()=>{void activateSound(true);};
        recover();
        // Audio can be interrupted without an interaction or React state update.
        // Re-check on return and periodically, including in an open background tab.
        const timer=window.setInterval(()=>{
            recover();
            if(fresh.current && Date.now()-fresh.current>45000)setChecksHealthy(false);
        },5000);
        window.addEventListener("pointerdown",gesture);window.addEventListener("keydown",gesture);
        window.addEventListener("focus",recover);window.addEventListener("pageshow",recover);
        document.addEventListener("visibilitychange",recover);
        return()=>{clearInterval(timer);window.removeEventListener("pointerdown",gesture);window.removeEventListener("keydown",gesture);window.removeEventListener("focus",recover);window.removeEventListener("pageshow",recover);document.removeEventListener("visibilitychange",recover);};
    },[enabled,permitted,activateSound]);
    useEffect(()=>{
        const changed=(event:StorageEvent)=>{
            if(event.key!==preferenceKey&&event.key!==null)return;
            generation.current++;let saved=false;try{saved=localStorage.getItem(preferenceKey)==="on";}catch{/* Keep muted when preference cannot be read. */}
            setEnabled(saved);setSoundReady(false);
            if(!saved){due.current=0;fresh.current=0;void audio.current?.suspend();}
        };
        window.addEventListener("storage",changed);return()=>window.removeEventListener("storage",changed);
    },[preferenceKey]);
    const check = useCallback(async (signal?: AbortSignal) => {
        if (!permitted || !authorization || !profile || polling.current) return;
        polling.current = true;
        const current = ++revision.current;
        try {
            const timeoutSignal = AbortSignal.any([...(signal ? [signal] : []), AbortSignal.timeout(10000)]);
            const activeBranches = await getActiveBranches(timeoutSignal);
            const branches = profile.roleName === "OWNER_ADMIN" ? activeBranches
                : activeBranches.filter(branch => profile.branchIds.includes(branch.id));
            const counts = await Promise.all(branches.map(async branch => {
                const response = await adminFetch(`/api/admin/orders/planning/alerts?branchId=${branch.id}`, authorization, {signal: timeoutSignal, cache: "no-store"});
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
            setChecksHealthy(true);
            setMessage(due.current ? [waiting ? `${waiting} ${translate("orders need preparation")}` : "", ready ? `${ready} ${translate("orders are overdue for ready")}` : ""].filter(Boolean).join(" · ") + ". " + translate("Start KOT or mark the completed orders ready to stop the alarm.") : translate("No orders need action. Alarm is watching for new work."));
        } catch {
            if (!signal?.aborted && current === revision.current) {
                // Keep the last verified alarm for at most 45 seconds during a
                // transient failure; never ring indefinitely on stale orders.
                setChecksHealthy(false);
            }
        } finally {polling.current = false;}
    }, [permitted, authorization, profile, translate, canStart, canReady]);
    useEffect(() => {
        if (!enabled || !permitted) return;
        const controller = new AbortController();
        const refresh = () => {if (navigator.onLine) void check(controller.signal);else setChecksHealthy(false);};
        refresh();
        const timer = window.setInterval(refresh, 15000);
        window.addEventListener("gokul-kitchen-changed", refresh);
        window.addEventListener("online", refresh);
        window.addEventListener("offline", refresh);
        window.addEventListener("focus", refresh);
        window.addEventListener("pageshow", refresh);
        document.addEventListener("visibilitychange", refresh);
        const pushed = (event: MessageEvent) => {if(event.data?.type === "gokul-staff-alert") refresh();};
        navigator.serviceWorker?.addEventListener("message", pushed);
        return () => {controller.abort(); due.current = 0; fresh.current = 0; clearInterval(timer); window.removeEventListener("gokul-kitchen-changed", refresh); window.removeEventListener("online", refresh); window.removeEventListener("offline", refresh); window.removeEventListener("focus", refresh); window.removeEventListener("pageshow", refresh); document.removeEventListener("visibilitychange", refresh); navigator.serviceWorker?.removeEventListener("message", pushed);};
    }, [enabled, permitted, check, profile?.staffId]);
    useEffect(() => {
        if (!enabled || !permitted || !soundReady) return;
        const timer = window.setInterval(() => {
            if (!audio.current || due.current < 1 || Date.now() - fresh.current > 45000 || !navigator.onLine) return;
            const ring = async () => {
                const context = audio.current!;
                const current=generation.current;
                try {
                    await resumeAlarm(context);
                    if(current!==generation.current||context!==audio.current||due.current<1||Date.now()-fresh.current>45000||!navigator.onLine)return;
                    if(context.state!=="running"){setSoundReady(false);return;}
                    // A blocked tab must not suppress a healthy tab's sound.
                    try {const key = `gokul-kitchen-last-chime:${preferenceKey}`; if (Date.now() - Number(localStorage.getItem(key)) < 1700) return; localStorage.setItem(key, String(Date.now()));} catch { /* private browsing */ }
                    for (let i = 0; i < 3; i++) {
                        const tone = context.createOscillator(), gain = context.createGain();
                        tone.connect(gain); gain.connect(context.destination); tone.type = "triangle"; tone.frequency.value = i % 2 ? 1046 : 784;
                        gain.gain.setValueAtTime(.65, context.currentTime + i * .4);
                        gain.gain.exponentialRampToValueAtTime(.001, context.currentTime + i * .4 + .3);
                        tone.start(context.currentTime + i * .4); tone.stop(context.currentTime + i * .4 + .35);
                    }
                } catch {if(current===generation.current&&context===audio.current)setSoundReady(false);}
            };
            if (navigator.locks) void navigator.locks.request("gokul-kitchen-alarm", {ifAvailable: true}, lock => lock ? ring() : undefined);
            else void ring();
        }, 2000);
        return () => clearInterval(timer);
    }, [enabled, permitted,soundReady,preferenceKey]);
    if (!permitted) return null;
    return <aside className="mx-4 my-2 rounded-xl border border-[#eadfd6] bg-[#fffaf3] p-3 text-sm text-slate-900" aria-label="Kitchen alarm">
        <div className="flex flex-wrap gap-3"><AdminHelp title="Kitchen sound alarm" description="Your enabled setting stays saved across refreshes and navigation. Sound resumes automatically when the browser permits it, or after your next normal tap or key press. An open background tab also rings while the browser keeps it running. Closed or suspended apps use the device notification sound through staff push. Android silent channels, Do Not Disturb and force-stop can suppress that sound." guidance="Enable once for this staff browser. Only Disable kitchen alarm switches this preference off; staff push settings are separate."/><button type="button" disabled={enabled} className="min-h-11 rounded-xl bg-teal-900 px-4 font-bold text-white disabled:opacity-70" onClick={()=>{setEnabled(true);try{localStorage.setItem(preferenceKey,"on");}catch{/* This visit stays enabled. */}void activateSound(true);}}>{translate(enabled ? "Kitchen alarm enabled" : "Enable kitchen alarm")}</button>
        {enabled&&<button type="button" className="min-h-11 rounded-xl border border-teal-900 px-4 font-bold" onClick={()=>{generation.current++;setEnabled(false);setSoundReady(false);due.current=0;fresh.current=0;void audio.current?.suspend();try{localStorage.setItem(preferenceKey,"off");}catch{/* This visit remains muted. */}setMessage("Kitchen alarm disabled for this staff browser.");}}>{translate("Disable kitchen alarm")}</button>}</div>
        {enabled&&!soundReady&&<div className="mt-2 rounded-lg border border-amber-400 bg-amber-50 p-3" role="status"><p><T text="Alarm enabled, but sound is paused by this browser. Tap Resume kitchen sound. Orders will keep being checked."/></p><button type="button" className="mt-2 min-h-11 rounded-xl bg-teal-900 px-4 font-bold text-white" onClick={()=>{void activateSound(true);}}>{translate("Resume kitchen sound")}</button></div>}
        {enabled&&!checksHealthy&&<p role="alert" className="mt-2 font-semibold"><T text="Live alert checks are delayed or unavailable. Retrying automatically. Verify orders and staff push notifications; sound pauses when order data is more than 45 seconds old."/></p>}
        <p role="status" className="mt-2">{translate(message || "Your choice is remembered for this staff browser. Sound repeats until waiting orders enter KOT and overdue preparing orders are marked ready.")}</p>
        <a className="mt-1 inline-flex min-h-11 items-center underline" href="/admin/staff-notifications">{translate("staff push settings")}</a>
    </aside>;
}
