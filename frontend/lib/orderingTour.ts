export const ORDERING_TOUR_KEY = "gokul-ordering-tour:v1";
export const OPEN_ORDERING_TOUR = "gokul-open-ordering-tour";
const CHANGED = "gokul-ordering-tour-change";
let dismissedThisSession = false;

export function orderingTourSeen(): boolean {
    if (dismissedThisSession) return true;
    try { return window.localStorage.getItem(ORDERING_TOUR_KEY) === "seen"; }
    catch { return false; }
}

export function dismissOrderingTour(): void {
    dismissedThisSession = true;
    try { window.localStorage.setItem(ORDERING_TOUR_KEY, "seen"); }
    catch { /* The current visit still remembers dismissal when storage is blocked. */ }
    window.dispatchEvent(new Event(CHANGED));
}

export function subscribeOrderingTour(callback: () => void): () => void {
    const storage = (event: StorageEvent) => { if (event.key === ORDERING_TOUR_KEY || event.key === null) callback(); };
    window.addEventListener("storage", storage);
    window.addEventListener(CHANGED, callback);
    return () => { window.removeEventListener("storage", storage); window.removeEventListener(CHANGED, callback); };
}

export const orderingTourServerSnapshot = () => true;
