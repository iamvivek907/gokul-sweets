"use client";

import {useEffect, useRef} from "react";
import {nextPaymentPollDelayMs} from "@/lib/paymentPolling";

export type PaymentPollResult = {success: boolean; retryAfterMs?: number | null; permanent?: boolean} | undefined;

/** One timer for one pending payment. Resets only when payment identity or lifecycle changes. */
export function usePaymentPolling(enabled: boolean, paymentId: number | undefined, status: string,
                                  deadlineMs: number, openingPayment: boolean,
                                  refresh: () => Promise<PaymentPollResult>) {
    const refreshRef = useRef(refresh);
    useEffect(() => {refreshRef.current = refresh;}, [refresh]);

    useEffect(() => {
        if (!enabled || !paymentId || status !== "PENDING" || openingPayment) return;
        let stopped = false;
        let timer: number | undefined;
        let successes = 0;
        let failures = 0;
        let lastAttempt = 0;
        const canCheck = () => !document.hidden && navigator.onLine !== false;
        const schedule = (delay: number) => {
            window.clearTimeout(timer);
            timer = window.setTimeout(() => {void check();}, delay);
        };
        async function check() {
            if (stopped || !canCheck() || Date.now() >= deadlineMs) return;
            lastAttempt = Date.now();
            const result = await refreshRef.current();
            if (stopped || result?.permanent) return;
            if (result?.success) {successes++; failures = 0;}
            else failures++;
            if (Date.now() >= deadlineMs) return;
            schedule(Math.max(nextPaymentPollDelayMs(successes, failures, Math.random()), result?.retryAfterMs ?? 0));
        }
        function resume() {
            if (stopped || !canCheck() || Date.now() >= deadlineMs) return;
            if (lastAttempt && Date.now() - lastAttempt < 15_000) return;
            schedule(0);
        }
        if (canCheck() && Date.now() < deadlineMs) schedule(15_000);
        document.addEventListener("visibilitychange", resume);
        window.addEventListener("online", resume);
        return () => {
            stopped = true;
            window.clearTimeout(timer);
            document.removeEventListener("visibilitychange", resume);
            window.removeEventListener("online", resume);
        };
    }, [enabled, paymentId, status, deadlineMs, openingPayment]);
}
