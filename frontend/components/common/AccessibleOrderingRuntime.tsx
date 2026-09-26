"use client";

import {useEffect} from "react";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function AccessibleOrderingRuntime() {
    const enabled = useStorefrontFeatures()?.accessibleOrderingV2 === true;
    useEffect(() => {
        if (!enabled) return;
        const root = document.documentElement;
        root.dataset.accessibleOrdering = "true";
        // Next owns the viewport tag; change its content only while the feature is enabled.
        // iOS browsers otherwise refuse pinch zoom because the legacy layout caps scale at 1.
        const viewport = document.querySelector<HTMLMetaElement>('meta[name="viewport"]');
        const previous = viewport?.content;
        if (viewport) viewport.content = (previous ?? "width=device-width, initial-scale=1")
            .replace(/,?\s*maximum-scale\s*=\s*[^,]+/gi, "")
            .replace(/,?\s*user-scalable\s*=\s*no/gi, "");
        return () => {
            delete root.dataset.accessibleOrdering;
            if (viewport && previous !== undefined) viewport.content = previous;
        };
    }, [enabled]);
    return null;
}
