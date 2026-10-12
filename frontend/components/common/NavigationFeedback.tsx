"use client";
import {useEffect, useState} from "react";
import {usePathname} from "next/navigation";
import {useTranslation} from "@/lib/language";

export default function NavigationFeedback() {
    const pathname = usePathname(), translate = useTranslation();
    const [pending, setPending] = useState(false);
    useEffect(() => {window.dispatchEvent(new Event("gokul-navigation-reset"));}, [pathname]);
    useEffect(() => {
        const sources = new Set<string>();
        let destination: string | null = null;
        let showing = false;
        let delay: ReturnType<typeof setTimeout> | undefined;
        let deadline: ReturnType<typeof setTimeout> | undefined;
        const stop = () => {
            clearTimeout(delay); clearTimeout(deadline);
            delay = undefined; deadline = undefined;
            sources.clear(); destination = null; showing = false; setPending(false);
        };
        const start = (event?: Event) => {
            const detail = (event as CustomEvent<{token?: string; destination?: string}> | undefined)?.detail;
            const token = detail?.token;
            if (token) {sources.delete("fallback"); sources.add(token); destination = detail?.destination ?? null;}
            else sources.add("fallback");
            if (!delay && !showing) delay = setTimeout(() => {
                delay = undefined; showing = true; setPending(true);
            }, 350);
            clearTimeout(deadline);
            deadline = setTimeout(stop, 15000);
        };
        const end = (event: Event) => {
            const token = (event as CustomEvent<{token?: string}>).detail?.token;
            if (token) sources.delete(token); else sources.delete("fallback");
            if (!sources.size) stop();
        };
        const targetFor = (event: MouseEvent) => {
            if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
            const anchor = (event.target as Element)?.closest?.("a[href]") as HTMLAnchorElement | null;
            if (!anchor || anchor.target && anchor.target !== "_self" || anchor.hasAttribute("download")) return;
            const target = new URL(anchor.href, location.href);
            if (target.origin !== location.origin) return;
            return {anchor, target};
        };
        const guard = (event: MouseEvent) => {
            const value = targetFor(event); if (!value) return;
            const {anchor, target} = value;
            const primary = anchor.hasAttribute("data-nav-icon") || anchor.hasAttribute("data-navigation-link");
            if (primary && (target.href === location.href || sources.size > 0 && target.href === destination)) {
                event.preventDefault();
            }
        };
        const click = (event: MouseEvent) => {
            // React/Next and payment guards get to cancel before fallback feedback starts.
            const value = targetFor(event); if (!value) return;
            const {target} = value;
            if (target.pathname !== location.pathname) {
                destination = target.href;
                start();
            }
        };
        document.addEventListener("click", guard, true);
        document.addEventListener("click", click);
        window.addEventListener("gokul-navigation-start", start);
        window.addEventListener("gokul-navigation-end", end);
        window.addEventListener("gokul-navigation-reset", stop);
        return () => {
            clearTimeout(delay); clearTimeout(deadline);
            document.removeEventListener("click", guard, true);
            document.removeEventListener("click", click);
            window.removeEventListener("gokul-navigation-start", start);
            window.removeEventListener("gokul-navigation-end", end);
            window.removeEventListener("gokul-navigation-reset", stop);
        };
    }, []);
    return pending ? <div role="status" aria-live="polite" className="navigation-feedback">
        <span className="sr-only">{translate("Opening…")}</span><span className="navigation-progress" aria-hidden="true" />
    </div> : null;
}
