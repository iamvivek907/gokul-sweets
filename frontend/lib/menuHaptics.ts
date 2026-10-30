/** A short menu-only tap. Unsupported or blocked vibration never affects ordering. */
export function menuTapFeedback(): void {
    if (typeof window === "undefined" || window.location.pathname !== "/menu") return;
    try {
        if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;
        if (typeof navigator.vibrate === "function") navigator.vibrate(12);
    } catch { /* Device/browser policy can reject vibration. */ }
}
