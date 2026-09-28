"use client";

import {useEffect} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function AccessibleOrderingRuntime() {
    const enabled = useStorefrontFeatures()?.accessibleOrderingV2 === true;
    useEffect(() => {
        let lastTouch: {x: number; y: number} | null = null;
        const preventCustomerPinch = (event: Event) => {
            const target = event.target;
            if (target instanceof Element && target.closest(".app-container")) event.preventDefault();
        };
        const preventMultiTouch = (event: TouchEvent) => {
            if (event.touches.length > 1) preventCustomerPinch(event);
        };
        const rememberTouch = (event: TouchEvent) => {
            lastTouch = event.touches.length === 1
                ? {x: event.touches[0].clientX, y: event.touches[0].clientY} : null;
        };
        const preventEdgeBounce = (event: TouchEvent) => {
            if (event.touches.length !== 1 || !lastTouch) return;
            const target = event.target;
            if (!(target instanceof Element) || !target.closest(".app-container")) return;
            const x = event.touches[0].clientX;
            const y = event.touches[0].clientY;
            const dx = x - lastTouch.x;
            const dy = y - lastTouch.y;
            lastTouch = {x, y};
            if (Math.abs(dy) <= Math.abs(dx) || !dy) return;

            // Keep dialogs and other intentional vertical scrollers usable at the page edge.
            for (let node: Element | null = target; node && !node.classList.contains("app-container"); node = node.parentElement) {
                const style = getComputedStyle(node);
                if (!/(auto|scroll)/.test(style.overflowY) || node.scrollHeight <= node.clientHeight + 1) continue;
                if (dy > 0 && node.scrollTop > 0 || dy < 0 && node.scrollTop + node.clientHeight < node.scrollHeight - 1) return;
            }

            const maxScroll = Math.max(0, document.documentElement.scrollHeight - window.innerHeight);
            if (dy > 0 && window.scrollY <= 0 || dy < 0 && window.scrollY >= maxScroll - 1) event.preventDefault();
        };
        const preventCustomerCallout = (event: MouseEvent) => {
            const target = event.target;
            if (!(target instanceof Element) || !target.closest(".app-container")) return;
            if (target.closest("input,textarea,select,[contenteditable='true'],.selectable-text,[data-copyable]")) return;
            if (target.closest("a,img,video")) event.preventDefault();
        };
        document.addEventListener("gesturestart", preventCustomerPinch, {passive: false});
        document.addEventListener("gesturechange", preventCustomerPinch, {passive: false});
        document.addEventListener("touchmove", preventMultiTouch, {passive: false});
        document.addEventListener("touchstart", rememberTouch, {passive: true});
        document.addEventListener("touchmove", preventEdgeBounce, {passive: false});
        document.addEventListener("touchend", rememberTouch, {passive: true});
        document.addEventListener("contextmenu", preventCustomerCallout);
        return () => {
            document.removeEventListener("gesturestart", preventCustomerPinch);
            document.removeEventListener("gesturechange", preventCustomerPinch);
            document.removeEventListener("touchmove", preventMultiTouch);
            document.removeEventListener("touchstart", rememberTouch);
            document.removeEventListener("touchmove", preventEdgeBounce);
            document.removeEventListener("touchend", rememberTouch);
            document.removeEventListener("contextmenu", preventCustomerCallout);
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
