"use client";

import {
    useEffect,
    useState,
    useSyncExternalStore
} from "react";


interface BeforeInstallPromptEvent
    extends Event {

    prompt: () => Promise<{
        outcome:
            | "accepted"
            | "dismissed";
    }>;
}


interface NavigatorWithStandalone
    extends Navigator {

    standalone?: boolean;
}


/*
 * Allows us to know when browser-only
 * rendering is safe.
 *
 * During SSR + hydration:
 * false
 *
 * After hydration:
 * true
 */
function subscribeClient():
    () => void {

    return () => {};
}


function getClientSnapshot():
    boolean {

    return true;
}


function getServerSnapshot():
    boolean {

    return false;
}


function detectInstalled():
    boolean {

    const standaloneMode =
        window.matchMedia(
            "(display-mode: standalone)"
        ).matches;


    const iosStandalone =
        (
            navigator as
                NavigatorWithStandalone
        ).standalone === true;


    return (
        standaloneMode ||
        iosStandalone
    );
}


function detectIOS():
    boolean {

    return /iPhone|iPad|iPod/i.test(
        navigator.userAgent
    );
}


export function usePwaInstall() {

    const isClient =
        useSyncExternalStore(
            subscribeClient,
            getClientSnapshot,
            getServerSnapshot
        );


    const [
        deferredPrompt,
        setDeferredPrompt
    ] =
        useState<
            BeforeInstallPromptEvent | null
        >(null);


    const [
        installedByEvent,
        setInstalledByEvent
    ] =
        useState(false);


    useEffect(() => {

        function handleBeforeInstallPrompt(
            event: Event
        ) {

            event.preventDefault();


            setDeferredPrompt(
                event as
                    BeforeInstallPromptEvent
            );
        }


        function handleAppInstalled() {

            setDeferredPrompt(
                null
            );


            setInstalledByEvent(
                true
            );
        }


        window.addEventListener(
            "beforeinstallprompt",
            handleBeforeInstallPrompt
        );


        window.addEventListener(
            "appinstalled",
            handleAppInstalled
        );


        return () => {

            window.removeEventListener(
                "beforeinstallprompt",
                handleBeforeInstallPrompt
            );


            window.removeEventListener(
                "appinstalled",
                handleAppInstalled
            );
        };

    }, []);


    async function install():
        Promise<boolean> {

        if (!deferredPrompt) {
            return false;
        }


        const result =
            await deferredPrompt.prompt();


        setDeferredPrompt(
            null
        );


        return result.outcome
            === "accepted";
    }


    /*
     * Do NOT inspect browser information
     * until hydration is complete.
     */
    const detectedInstalled =
        isClient
            ? detectInstalled()
            : false;


    const isIOS =
        isClient
            ? detectIOS()
            : false;


    const isInstalled =
        installedByEvent ||
        detectedInstalled;


    return {

        canInstall:
            isClient &&
            deferredPrompt !== null,

        isInstalled,

        isIOS,

        install
    };
}