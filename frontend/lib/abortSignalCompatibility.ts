/** Backfill cancellation APIs used by customer flows on older mobile browsers. */
export function installAbortSignalCompatibility(Signal: typeof AbortSignal = AbortSignal): void {
    if (typeof Signal.timeout !== "function") {
        Object.defineProperty(Signal, "timeout", {configurable: true, writable: true, value: (milliseconds: number) => {
            if (!Number.isFinite(milliseconds) || milliseconds < 0) throw new RangeError("Invalid timeout");
            const controller = new AbortController();
            setTimeout(() => controller.abort(new DOMException("Request timed out", "TimeoutError")), milliseconds);
            return controller.signal;
        }});
    }
    if (typeof Signal.any !== "function") {
        Object.defineProperty(Signal, "any", {configurable: true, writable: true, value: (sources: AbortSignal[]) => {
            const controller = new AbortController();
            const signals = Array.from(new Set(sources));
            const alreadyAborted = signals.find(signal => signal.aborted);
            if (alreadyAborted) {controller.abort(alreadyAborted.reason); return controller.signal;}
            const listeners = new Map<AbortSignal, () => void>();
            const cleanup = () => {for (const [signal, listener] of listeners) signal.removeEventListener("abort", listener); listeners.clear();};
            for (const signal of signals) {
                const listener = () => {cleanup(); controller.abort(signal.reason);};
                listeners.set(signal, listener);
                signal.addEventListener("abort", listener, {once: true});
            }
            return controller.signal;
        }});
    }
}

installAbortSignalCompatibility();
