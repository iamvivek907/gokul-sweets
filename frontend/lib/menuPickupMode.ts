/** Automatic selection is bound to an exact stored pickup. Legacy choices stay fixed. */
export const MENU_PICKUP_MODE_KEY = "gokul-menu-pickup-mode:v1";
const event = "gokul-menu-pickup-mode-change";
export function getMenuPickupModeSnapshot() {return localStorage.getItem(MENU_PICKUP_MODE_KEY) ?? "";}
export function subscribeMenuPickupMode(listener: () => void) {
    window.addEventListener(event, listener); window.addEventListener("storage", listener);
    return () => {window.removeEventListener(event, listener); window.removeEventListener("storage", listener);};
}
export function isSoonestPickup(raw: string, branchId: number | null | undefined, pickup: string, hasPreference: boolean) {
    if (!raw) return !hasPreference && !pickup;
    try {const value = JSON.parse(raw); return value.branchId !== branchId ? !hasPreference && !pickup : value.mode === "soonest" && value.pickup === pickup;}
    catch {return false;}
}
export function setMenuPickupMode(branchId: number, automatic: boolean, pickup = localStorage.getItem("gokul-selected-pickup-slot") ?? "") {
    localStorage.setItem(MENU_PICKUP_MODE_KEY, JSON.stringify({branchId, mode: automatic ? "soonest" : "fixed", pickup}));
    window.dispatchEvent(new Event(event));
}
