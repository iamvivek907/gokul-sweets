"use client";
import {useEffect, useSyncExternalStore} from "react";
import {pwaInstall} from "@/lib/pwaInstall";

export function usePwaInstall() {
    useEffect(() => {pwaInstall.start(window);}, []);
    const state = useSyncExternalStore(pwaInstall.subscribe, pwaInstall.getSnapshot, pwaInstall.getServerSnapshot);
    return {...state, isInstalled: state.installationState === "installed" || state.installationState === "accepted",
        install: pwaInstall.promptInstall, promptInstall: pwaInstall.promptInstall,
        dismiss: pwaInstall.dismiss, closeGuide: pwaInstall.closeGuide, recordBannerView: pwaInstall.recordBannerView};
}
