"use client";

import {useEffect} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function AccessibleOrderingRuntime() {
    const enabled = useStorefrontFeatures()?.accessibleOrderingV2 === true;
    useEffect(() => {
        const preventCustomerPinch = (event: Event) => {
            const target = event.target;
            if (target instanceof Element && target.closest(".app-container")) event.preventDefault();
        };
        const preventMultiTouch = (event: TouchEvent) => {
            if (event.touches.length > 1) preventCustomerPinch(event);
        };
        document.addEventListener("gesturestart", preventCustomerPinch, {passive: false});
        document.addEventListener("gesturechange", preventCustomerPinch, {passive: false});
        document.addEventListener("touchmove", preventMultiTouch, {passive: false});
        return () => {
            document.removeEventListener("gesturestart", preventCustomerPinch);
            document.removeEventListener("gesturechange", preventCustomerPinch);
            document.removeEventListener("touchmove", preventMultiTouch);
        };
    }, []);
    useEffect(() => {
        if (!enabled) return;
        const root = document.documentElement;
        root.dataset.accessibleOrdering = "true";
        return () => {
            delete root.dataset.accessibleOrdering;
        };
    }, [enabled]);
    return null;
}
