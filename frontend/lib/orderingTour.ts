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

export const WALKTHROUGH_KEY = "gokul-ordering-walkthrough:v1";
export type WalkthroughStep = "branch" | "menu" | "pickup" | "add" | "cart";
export type Walkthrough = {step: WalkthroughStep; branchId: number | null; expiresAt: number};
let sessionWalkthrough = "";
const steps: WalkthroughStep[] = ["branch", "menu", "pickup", "add", "cart"];
export function getWalkthroughSnapshot(): string {
    try { return localStorage.getItem(WALKTHROUGH_KEY) ?? ""; }
    catch { return sessionWalkthrough; }
}
export const getWalkthroughServerSnapshot = () => "";
export function parseWalkthrough(raw: string, now = Date.now()): Walkthrough | null {
    try {
        const value = JSON.parse(raw) as Walkthrough;
        return steps.includes(value.step) && (value.branchId === null || Number.isSafeInteger(value.branchId) && value.branchId > 0)
            && Number.isFinite(value.expiresAt) && value.expiresAt > now && value.expiresAt <= now + 30 * 60_000 ? value : null;
    } catch { return null; }
}
function writeWalkthrough(value: Walkthrough | null) {
    sessionWalkthrough = value ? JSON.stringify(value) : "";
    try { if (value) localStorage.setItem(WALKTHROUGH_KEY, sessionWalkthrough); else localStorage.removeItem(WALKTHROUGH_KEY); }
    catch { /* A blocked store still supports this visit. */ }
    window.dispatchEvent(new Event(CHANGED));
}
export function subscribeWalkthrough(callback: () => void): () => void {
    const storage = (event: StorageEvent) => { if (event.key === WALKTHROUGH_KEY || event.key === null) callback(); };
    window.addEventListener("storage", storage); window.addEventListener(CHANGED, callback);
    return () => {window.removeEventListener("storage", storage); window.removeEventListener(CHANGED, callback);};
}
export function startWalkthrough(branchId: number | null, onMenu: boolean, hasItems: boolean, smartPickup: boolean) {
    dismissOrderingTour();
    writeWalkthrough({step: !branchId ? "branch" : onMenu ? hasItems ? "cart" : smartPickup ? "pickup" : "add" : "menu", branchId, expiresAt: Date.now() + 30 * 60_000});
}
export function stopWalkthrough() { dismissOrderingTour(); writeWalkthrough(null); }
export function followWalkthrough(value: Walkthrough, branchId: number | null, onMenu: boolean, hasItems: boolean, smartPickup: boolean): WalkthroughStep {
    if (!branchId) return "branch";
    if (onMenu && hasItems) return "cart";
    if (value.branchId !== branchId || value.step === "branch") return onMenu ? smartPickup ? "pickup" : "add" : "menu";
    if (value.step === "menu" && onMenu) return smartPickup ? "pickup" : "add";
    if (value.step === "cart" && !hasItems) return smartPickup ? "pickup" : "add";
    return value.step;
}
export function updateWalkthrough(step: WalkthroughStep, branchId: number | null) {
    const current = parseWalkthrough(getWalkthroughSnapshot());
    if (current && (current.step !== step || current.branchId !== branchId)) writeWalkthrough({...current, step, branchId});
}
/** Called only after the existing picker successfully validates and saves a customer choice. */
export function confirmWalkthroughPickup(branchId: number) {
    const current = parseWalkthrough(getWalkthroughSnapshot());
    if (current?.step === "pickup" && current.branchId === branchId) updateWalkthrough("add", branchId);
}
