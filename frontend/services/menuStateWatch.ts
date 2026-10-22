import {apiClient} from "@/services/apiClient";

/** Watch only an open menu. Older/disabled servers stop at 404; minute refresh remains available.
 * One request at a time, bounded timeout, backoff on failures and cancellation on context changes.
 */
export function watchMenuState(branchId: number, date: string, changed: () => void, signal: AbortSignal) {
    let version: string | undefined;
    let delay = 2000;
    let timer: ReturnType<typeof setTimeout> | undefined;
    const stop = () => {if (timer !== undefined) clearTimeout(timer);};
    signal.addEventListener("abort", stop, {once: true});
    async function poll() {
        if (signal.aborted) return;
        if (navigator.onLine && document.visibilityState === "visible") {
            try {
                const state = await apiClient<{version: string}>(`/api/branches/${branchId}/menu-state?date=${encodeURIComponent(date)}`,
                    {signal: AbortSignal.any([signal, AbortSignal.timeout(5000)])});
                if (signal.aborted) return;
                if (typeof state.version !== "string" || !state.version) throw new Error("Missing menu version");
                // Refresh the first observed state too: the initial preview might predate this token.
                if (version !== state.version) {version = state.version; changed();}
                delay = 2000;
            } catch (error) {
                if (signal.aborted) return;
                if (typeof error === "object" && error !== null && "status" in error && error.status === 404) return;
                delay = Math.min(30000, delay * 2);
            }
        }
        if (!signal.aborted) timer = setTimeout(() => {void poll();}, delay);
    }
    void poll();
}
