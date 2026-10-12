/** One document-wide prompt owner. Storage records preferences, never installation proof. */
export const INSTALL_PREFERENCES_KEY = "gokul-pwa-install-preferences-v1";
const DAY = 86_400_000;
const COOLDOWN = 7 * DAY;
const LOCK = "gokul-pwa-install-prompt";
type Result = "accepted" | "dismissed" | "failed";
interface InstallPrompt extends Event {
    prompt(): Promise<{outcome: "accepted" | "dismissed"} | void>;
    userChoice?: Promise<{outcome: "accepted" | "dismissed"}>;
}
interface Preferences {
    installBannerDismissedAt: number;
    installPromptLastShownAt: number;
    installPromptResult: Result | null;
    installInteractionCount: number;
    bannerViews: number;
    lastBannerViewAt: number;
    nextPromotionAt: number;
}
export interface PwaInstallState {
    ready: boolean;
    isStandalone: boolean;
    isIOS: boolean;
    isMobile: boolean;
    isInstallPromptAvailable: boolean;
    canInstall: boolean;
    isPromptInProgress: boolean;
    guideOpen: boolean;
    installationState: "unavailable" | "available" | "prompting" | "accepted" | "installed" | "cooldown";
    error: string;
}
const initial: PwaInstallState = {ready: false, isStandalone: false, isIOS: false, isMobile: false,
    isInstallPromptAvailable: false, canInstall: false, isPromptInProgress: false, guideOpen: false,
    installationState: "unavailable", error: ""};
const emptyPreferences = (): Preferences => ({installBannerDismissedAt: 0, installPromptLastShownAt: 0,
    installPromptResult: null, installInteractionCount: 0, bannerViews: 0, lastBannerViewAt: 0, nextPromotionAt: 0});

export function createPwaInstallStore() {
    let browser: Window | null = null;
    let state = initial;
    let deferred: InstallPrompt | null = null;
    let installed = false, accepted = false, prompting = false, guideOpen = false, viewed = false;
    let error = "", cooldownUntil = 0;
    let preferences = emptyPreferences();
    let channel: BroadcastChannel | null = null;
    const listeners = new Set<() => void>();
    const blockers = new Set<object>();
    const now = () => Date.now();
    function readPreferences() {
        try {
            const data = JSON.parse(browser?.localStorage.getItem(INSTALL_PREFERENCES_KEY) ?? "null");
            const next = emptyPreferences();
            if (data && typeof data === "object") {
                for (const key of ["installBannerDismissedAt", "installPromptLastShownAt", "installInteractionCount", "bannerViews", "lastBannerViewAt", "nextPromotionAt"] as const) {
                    if (Number.isFinite(data[key]) && data[key] >= 0) next[key] = data[key];
                }
                if (["accepted", "dismissed", "failed"].includes(data.installPromptResult)) next.installPromptResult = data.installPromptResult;
            }
            preferences = next;
        } catch { /* Private/storage-disabled browsing still works in memory. */ }
        const promptCooldown = preferences.installPromptResult === "failed" ? DAY : COOLDOWN;
        cooldownUntil = Math.max(preferences.installBannerDismissedAt ? preferences.installBannerDismissedAt + COOLDOWN : 0,
            preferences.installPromptLastShownAt ? preferences.installPromptLastShownAt + promptCooldown : 0, preferences.nextPromotionAt);
    }
    function savePreferences() {
        try {browser?.localStorage.setItem(INSTALL_PREFERENCES_KEY, JSON.stringify(preferences));} catch { /* Optional preference. */ }
    }
    function publish() {
        if (!browser) return;
        const nav = browser.navigator as Navigator & {standalone?: boolean};
        const isStandalone = nav.standalone === true || ["standalone", "fullscreen", "minimal-ui"].some(mode => browser!.matchMedia(`(display-mode: ${mode})`).matches);
        const isIOS = /iPhone|iPad|iPod/i.test(nav.userAgent) || /Macintosh/i.test(nav.userAgent) && nav.maxTouchPoints > 1;
        const cooling = cooldownUntil > now();
        const available = blockers.size === 0 && !isStandalone && !installed && !accepted && !cooling && (isIOS || deferred !== null);
        if (isStandalone || installed || accepted || cooling || blockers.size > 0) guideOpen = false;
        const next: PwaInstallState = {ready: true, isStandalone, isIOS, isMobile: browser.matchMedia("(max-width: 640px)").matches,
            isInstallPromptAvailable: deferred !== null && !prompting && !installed && !isStandalone,
            canInstall: available && !prompting, isPromptInProgress: prompting, guideOpen,
            installationState: isStandalone || installed ? "installed" : accepted ? "accepted" : prompting ? "prompting" : cooling ? "cooldown" : available ? "available" : "unavailable", error};
        if (Object.keys(next).some(key => next[key as keyof PwaInstallState] !== state[key as keyof PwaInstallState])) {
            state = next; listeners.forEach(listener => listener());
        }
    }
    function updatePreferences() {readPreferences(); publish();}
    function dismiss() {
        preferences.installBannerDismissedAt = now(); preferences.installInteractionCount++;
        cooldownUntil = now() + COOLDOWN; guideOpen = false; error = ""; savePreferences(); publish();
    }
    function finish(result: Result) {
        preferences.installPromptResult = result;
        cooldownUntil = now() + (result === "failed" ? DAY : COOLDOWN);
        accepted = result === "accepted"; savePreferences(); publish();
    }
    async function consumePrompt() {
        readPreferences(); // Recheck shared cooldown after acquiring the cross-tab lock.
        if (!browser || !deferred || installed || accepted || state.isStandalone || cooldownUntil > now()) return false;
        const event = deferred;
        deferred = null; // A native event is single-use, including failures.
        preferences.installPromptLastShownAt = now(); preferences.installInteractionCount++;
        savePreferences();
        try {
            const response = await event.prompt();
            const choice = event.userChoice ? await event.userChoice : response;
            if (!choice || !["accepted", "dismissed"].includes(choice.outcome)) throw new Error("No install choice");
            finish(installed ? "accepted" : choice.outcome); return installed || choice.outcome === "accepted";
        } catch {
            if (installed) {finish("accepted"); return true;}
            error = "Installation could not be opened. You can keep ordering in your browser.";
            finish("failed"); return false;
        }
    }
    async function promptInstall() {
        if (!browser || !state.canInstall || prompting) return false;
        if (state.isIOS) {guideOpen = true; preferences.installInteractionCount++; savePreferences(); publish(); return false;}
        prompting = true; error = ""; publish();
        try {
            // Chromium Web Locks serializes prompts from multiple tabs. No network or
            // permission request precedes the native prompt; transient activation is retained.
            if (browser.navigator.locks) {
                return await browser.navigator.locks.request(LOCK, {ifAvailable: true}, async lock => lock ? consumePrompt() : false);
            }
            // Older browsers: only the focused document may invoke the prompt.
            if (!browser.document.hasFocus()) return false;
            return await consumePrompt();
        } catch {
            deferred = null;
            preferences.installPromptLastShownAt = now();
            error = "Installation could not be opened. You can keep ordering in your browser.";
            finish("failed"); return false;
        } finally {prompting = false; publish();}
    }
    function closeGuide() {guideOpen = false; publish();}
    function recordBannerView() {
        if (viewed || !state.canInstall) return;
        viewed = true;
        // Count at most one impression per day across routes and tabs; after three
        // ignored visits, suppress the next visit for a week instead of escalating.
        if (!preferences.lastBannerViewAt || now() - preferences.lastBannerViewAt >= DAY) {
            preferences.bannerViews = now() - preferences.lastBannerViewAt > COOLDOWN ? 1 : preferences.bannerViews + 1;
            preferences.lastBannerViewAt = now();
            if (preferences.bannerViews >= 3) preferences.nextPromotionAt = now() + COOLDOWN;
            savePreferences();
        }
    }
    function start(target: Window) {
        if (browser) return;
        browser = target; readPreferences();
        target.addEventListener("beforeinstallprompt", event => {
            event.preventDefault();
            if (!prompting && !installed && !accepted && !state.isStandalone) deferred = event as InstallPrompt;
            publish();
        });
        target.addEventListener("appinstalled", () => {
            installed = true; deferred = null; guideOpen = false;
            preferences.installPromptLastShownAt = now(); preferences.installPromptResult = "accepted";
            savePreferences(); channel?.postMessage("appinstalled"); publish();
        });
        target.addEventListener("storage", event => {if (event.key === INSTALL_PREFERENCES_KEY) updatePreferences();});
        target.addEventListener("focus", updatePreferences);
        target.document.addEventListener("visibilitychange", () => {if (target.document.visibilityState === "visible") updatePreferences();});
        for (const query of ["(max-width: 640px)", ...["standalone", "fullscreen", "minimal-ui"].map(mode => `(display-mode: ${mode})`)]) {
            target.matchMedia(query).addEventListener("change", publish);
        }
        try {
            channel = new BroadcastChannel("gokul-pwa-install");
            channel.onmessage = event => {if (event.data === "appinstalled") {installed = true; deferred = null; publish();}};
        } catch { /* Browser support varies; storage events still share cooldowns. */ }
        publish();
    }
    return {start, subscribe(listener: () => void) {listeners.add(listener); return () => {listeners.delete(listener);};},
        getSnapshot: () => state, getServerSnapshot: () => initial, promptInstall, dismiss, closeGuide, recordBannerView,
        blockPromotion(source: object, blocked: boolean) {if (blocked) blockers.add(source); else blockers.delete(source); publish();}};
}
export const pwaInstall = createPwaInstallStore();
