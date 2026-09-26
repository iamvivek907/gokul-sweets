const SESSION_KEY = "gokul-pre-home-choice-v1";

export function shouldShowIntentGateway(enabled: boolean, hasSavedBranch: boolean, chosenInTab: boolean): boolean {
    return enabled && !hasSavedBranch && !chosenInTab;
}

export function hasChosenEntryIntent(): boolean {
    try {
        return sessionStorage.getItem(SESSION_KEY) === "chosen";
    } catch {
        // Browsers that block storage can still enter through any choice.
        return false;
    }
}

export function rememberEntryIntent(): void {
    try {
        sessionStorage.setItem(SESSION_KEY, "chosen");
    } catch {
        // The current page can advance even when storage is unavailable.
    }
}
