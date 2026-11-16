type Listener = () => void;
const listeners = new Set<Listener>();
let timer: ReturnType<typeof setInterval> | null = null;
function refreshNotifications() {
    for (const refresh of listeners) refresh();
}
function onPush(event: MessageEvent) {
    if (event.data?.type === "gokul-customer-alert") refreshNotifications();
}

/** Only share the wake-up clock; private results remain request-scoped and are never cached. */
export function subscribeNotificationTick(listener: Listener): () => void {
    listeners.add(listener);
    if (timer === null) {
        timer = setInterval(refreshNotifications, 30_000);
        if (typeof navigator !== "undefined") navigator.serviceWorker?.addEventListener("message", onPush);
    }
    return () => {
        listeners.delete(listener);
        if (!listeners.size && timer !== null) {
            clearInterval(timer);
            timer = null;
            if (typeof navigator !== "undefined") navigator.serviceWorker?.removeEventListener("message", onPush);
        }
    };
}
