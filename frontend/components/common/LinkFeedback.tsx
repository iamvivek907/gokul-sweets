"use client";
import {useLinkStatus} from "next/link";
import {useEffect, useId} from "react";
/** Next owns completion; the shared progress bar gives feedback without changing link size. */
export default function LinkFeedback({href}: {href?: string} = {}) {
    const {pending} = useLinkStatus();
    const token = useId();
    useEffect(() => {
        if (!pending) return;
        window.dispatchEvent(new CustomEvent("gokul-navigation-start", {detail: {token, destination: href ? new URL(href, location.href).href : undefined}}));
        return () => {window.dispatchEvent(new CustomEvent("gokul-navigation-end", {detail: {token}}));};
    }, [pending, token, href]);
    return null;
}
