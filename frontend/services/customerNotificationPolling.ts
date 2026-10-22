type Listener = () => void;
const listeners = new Set<Listener>();
let timer: ReturnType<typeof setInterval> | null = null;
/** Only share the wake-up clock; private results remain request-scoped and are never cached. */
export function subscribeNotificationTick(listener: Listener): () => void {
    listeners.add(listener);
    if (timer === null) timer = setInterval(() => {for (const refresh of listeners) refresh();}, 30_000);
    return () => {
        listeners.delete(listener);
        if (!listeners.size && timer !== null) {clearInterval(timer); timer = null;}
    };
}
