"use client";
import {useEffect, useRef, useState} from "react";
import {usePathname} from "next/navigation";
import {getVerifiedOrderPage} from "@/services/orderApi";
import {getReviewContext} from "@/services/reviewApi";
import {subscribeCustomerIdentityChanges} from "@/lib/customerIdentityEvents";
import OrderReviewCard from "./OrderReviewCard";
import styles from "./ReturnReviewPrompt.module.css";

/** A single latest completed order is considered per opening or long resume. */
export default function ReturnReviewPrompt() {
    const path = usePathname();
    const pathRef = useRef(path);
    useEffect(() => {pathRef.current = path;}, [path]);
    const dialog = useRef<HTMLDialogElement>(null);
    const [order, setOrder] = useState<{orderNumber:string;branchName:string}|null>(null);
    const [revision, setRevision] = useState(0);
    useEffect(() => subscribeCustomerIdentityChanges(() => {setOrder(null); setRevision(n => n + 1);}, {revalidateOnResume:false}), []);
    useEffect(() => {
        let hiddenAt = 0;
        const resume = () => {
            if (document.visibilityState === "hidden") {hiddenAt = Date.now();}
            else if (hiddenAt && Date.now() - hiddenAt > 60000) {hiddenAt = 0; setOrder(null); setRevision(n => n + 1);}
        };
        document.addEventListener("visibilitychange", resume);
        return () => document.removeEventListener("visibilitychange", resume);
    }, []);
    useEffect(() => {
        const c = new AbortController(); let timer: ReturnType<typeof setTimeout>;
        const allowed = () => /^\/(?:|branches(?:\/.*)?|menu)$/.test(pathRef.current);
        const deadline = Date.now() + 90000;
        const find = async () => {
            if (c.signal.aborted || Date.now() > deadline || document.visibilityState !== "visible") return;
            if (!allowed() || document.querySelector("dialog[open],[aria-modal=true],:popover-open")) {timer = setTimeout(find, 800); return;}
            try {
                const signal = AbortSignal.any([c.signal, AbortSignal.timeout(10000)]);
                const page = await getVerifiedOrderPage(null, signal, {status:"COMPLETED",limit:"1"});
                const latest = page.orders?.[0];
                // Never fall back to an older order if the latest one has a review.
                if (!latest || latest.reviewRating != null) return;
                const context = await getReviewContext(latest.orderNumber, signal);
                if (c.signal.aborted || !context.eligible || context.review) return;
                const present = () => {
                    if (c.signal.aborted || Date.now() > deadline || document.visibilityState !== "visible") return;
                    if (!allowed() || document.querySelector("dialog[open],[aria-modal=true],:popover-open")) {timer = setTimeout(present, 800); return;}
                    setOrder({orderNumber:latest.orderNumber,branchName:latest.branchName});
                };
                present();
            } catch { /* Optional prompt must never block ordering or require sign-in. */ }
        };
        timer = setTimeout(find, 3000);
        return () => {c.abort(); clearTimeout(timer);};
    }, [revision]);
    useEffect(() => {
        const surface = dialog.current;
        if (!surface) return;
        if (order && !surface.open) surface.showModal();
        else if (!order && surface.open) surface.close();
        return () => {if (surface.open) surface.close();};
    }, [order]);
    useEffect(() => {
        if (/^\/(?:|branches(?:\/.*)?|menu)$/.test(path)) return;
        let active = true;
        void Promise.resolve().then(() => {if (active) setOrder(null);});
        return () => {active = false;};
    }, [path]);
    return <dialog ref={dialog} className={styles.dialog} aria-label="Rate your last order" onCancel={() => setOrder(null)}>
        {order && <div className={styles.content}>
            <button className={styles.close} type="button" onClick={() => setOrder(null)} aria-label="Close review">×</button>
            <div className={styles.art} aria-hidden="true"><span>✦</span><span>♨</span><span>✧</span></div>
            <p className={styles.kicker}>A little note from Gokul</p>
            <h2>How was your last treat?</h2>
            <p>Your order from {order.branchName} is ready for your thoughts. A few stars can make our day.</p>
            <OrderReviewCard orderNumber={order.orderNumber} prompt />
            <button className={styles.later} type="button" onClick={() => setOrder(null)}>Maybe later</button>
        </div>}
    </dialog>;
}
