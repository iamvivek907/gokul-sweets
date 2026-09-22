"use client";

import {
    useSyncExternalStore
} from "react";


function subscribe(
    callback: () => void
): () => void {

    window.addEventListener(
        "online",
        callback
    );

    window.addEventListener(
        "offline",
        callback
    );


    return () => {

        window.removeEventListener(
            "online",
            callback
        );

        window.removeEventListener(
            "offline",
            callback
        );
    };
}


function getSnapshot(): boolean {

    return navigator.onLine;
}


/*
 * IMPORTANT:
 *
 * Server always assumes online.
 *
 * React uses this same value during hydration,
 * preventing server/client HTML mismatch.
 */
function getServerSnapshot(): boolean {

    return true;
}


export function useOnlineStatus(): boolean {

    return useSyncExternalStore(
        subscribe,
        getSnapshot,
        getServerSnapshot
    );
}