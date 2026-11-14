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
    const refreshed = useRef<string | null>(null);

    useLayoutEffect(() => {
        if (branchId === undefined) return;
        const deadlines: [number, number, string][] = JSON.parse(schedule);
        const controller = new AbortController();
        let busy = false;
        let transition: ReturnType<typeof setTimeout> | undefined;
        const blockExpired = (now: number) => setCategories(current => blockExpiredMenuService(current, now));

        async function refresh() {
            if (busy || controller.signal.aborted || !navigator.onLine || document.visibilityState !== "visible") return;
            busy = true;
            const signal = AbortSignal.any([controller.signal, AbortSignal.timeout(MENU_READ_RETRY_DEADLINE_MS)]);
            try {
                const fresh = await retryMenuRead(attempt => refreshMenuAvailability(branchId!, attempt), signal);
                if (!controller.signal.aborted) onRefresh(blockExpiredMenuService(fresh, performance.now()));
            } catch {
                if (controller.signal.aborted) return;
                const now = performance.now();
                blockExpired(now);
                if (deadlines.some(([deadline]) => deadline <= now)) onFailure();
            } finally {
                busy = false;
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
            const latestExpired = deadlines.reduce((latest, [, refreshAt, cutoff]) =>
                refreshAt <= now && cutoff > latest ? cutoff : latest, "");
            if (!latestExpired || busy || !navigator.onLine || document.visibilityState !== "visible") return;
            const key = `${branchId}:${latestExpired}`;
            if (refreshed.current === key) return;
            refreshed.current = key;
            void refresh();
        }

        function resume() {
            reconcile();
            void refresh();
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
