/** Anonymous stage durations only; never include URLs, branch/order IDs or payloads. */
export function reportLoadingStage(name: string, started: number): void {
    try {
        if (typeof window === "undefined" || typeof performance === "undefined") return;
        window.dispatchEvent(new CustomEvent("gokul-loading-stage", {detail: {
            name, value: performance.now() - started, rating: "measured"
        }}));
    } catch { /* Measurements must never change ordering outcomes. */ }
}
