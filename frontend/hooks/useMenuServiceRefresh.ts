"use client";
import {useLayoutEffect, useRef, type Dispatch, type SetStateAction} from "react";
import {refreshMenuAvailability} from "@/services/menuApi";
import {MENU_READ_RETRY_DEADLINE_MS, retryMenuRead} from "@/services/availabilityApi";
import {blockExpiredMenuService, menuServiceDeadline} from "@/lib/datedMenuAvailability";
import type {MenuCategory} from "@/types/menu";

/** Invalidate each service decision on time, independently of network recovery. */
export function useMenuServiceRefresh(
    branchId: number | undefined,
    categories: MenuCategory[],
    setCategories: Dispatch<SetStateAction<MenuCategory[]>>,
    onRefresh: (categories: MenuCategory[]) => void,
    onFailure: () => void
) {
    const boundaries = categories.flatMap(category => category.products.flatMap(product => {
        const deadline = menuServiceDeadline(product);
        const status = product.serviceAvailability;
        if (deadline === null || !status?.nextChangeAt) return [];
        const elapsed = (status.receivedMonotonic ?? deadline) - (status.requestStartedMonotonic ?? status.receivedMonotonic ?? deadline);
        return [[deadline, deadline + Math.max(0, elapsed), status.nextChangeAt] as const];
    }));
    const schedule = JSON.stringify(boundaries);
    const attempted = useRef<{branchId: number; cutoff: string} | null>(null);

    useLayoutEffect(() => {
        if (branchId === undefined) return;
        const activeBranchId = branchId;
        const deadlines: [number, number, string][] = JSON.parse(schedule);
        const controller = new AbortController();
        let busy = false;
        let transition: ReturnType<typeof setTimeout> | undefined;
        const blockExpired = (now: number) => setCategories(current => blockExpiredMenuService(current, now));

        function latestDueCutoff(now: number) {
            return deadlines.reduce((latest, [, refreshAt, cutoff]) =>
                refreshAt <= now && cutoff > latest ? cutoff : latest, "");
        }

        function unattempted(cutoff: string) {
            const last = attempted.current;
            return !!cutoff && (!last || last.branchId !== activeBranchId || cutoff > last.cutoff);
        }

        async function refresh(periodic = false) {
            if (busy || controller.signal.aborted || !navigator.onLine || document.visibilityState !== "visible") return;
            const cutoff = latestDueCutoff(performance.now());
            if (!periodic && !unattempted(cutoff)) return;
            if (unattempted(cutoff)) attempted.current = {branchId: activeBranchId, cutoff};
            busy = true;
            const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
            let fresh: MenuCategory[] | undefined;
            try {
                fresh = await retryMenuRead(attempt => refreshMenuAvailability(activeBranchId, attempt), signal);
            } catch {
                // Decide whether to surface the failure after checking for a newer cutoff.
            } finally {
                busy = false;
            }
            if (controller.signal.aborted) return;
            const now = performance.now();
            const pending = latestDueCutoff(now);
            const observedAt = fresh?.find(category => category.products.some(product => product.serviceAvailability?.evaluatedAt))
                ?.products.find(product => product.serviceAvailability?.evaluatedAt)?.serviceAvailability?.evaluatedAt;
            const needsFollowup = unattempted(pending)
                && !(observedAt && Date.parse(observedAt) >= Date.parse(pending));
            if (needsFollowup && navigator.onLine && document.visibilityState === "visible") {
                blockExpired(now);
                void refresh();
                return;
            }
            if (fresh) {
                if (unattempted(pending) && !needsFollowup) attempted.current = {branchId: activeBranchId, cutoff: pending};
                onRefresh(blockExpiredMenuService(fresh, now));
            } else {
                blockExpired(now);
                if (deadlines.some(([deadline]) => deadline <= now)) onFailure();
            }
        }

        function reconcile() {
            clearTimeout(transition);
            const now = performance.now();
            if (deadlines.some(([deadline]) => deadline <= now)) blockExpired(now);
            // Block at the conservative cutoff, then refresh once the cutoff has passed
            // even if the server observed it just before the response reached us.
            const next = deadlines.reduce((earliest, [blockAt, refreshAt]) =>
                Math.min(earliest, blockAt > now ? blockAt : refreshAt > now ? refreshAt : Infinity), Infinity);
            if (Number.isFinite(next)) transition = setTimeout(reconcile, Math.min(2147483647, Math.ceil(next - now)));
            void refresh();
        }

        function resume() {
            reconcile();
            void refresh(true);
        }

        reconcile();
        const poll = setInterval(resume, 30000);
        window.addEventListener("online", resume);
        document.addEventListener("visibilitychange", resume);
        return () => {
            controller.abort();
            clearTimeout(transition);
            clearInterval(poll);
            window.removeEventListener("online", resume);
            document.removeEventListener("visibilitychange", resume);
        };
    }, [branchId, schedule, setCategories, onRefresh, onFailure]);
}
