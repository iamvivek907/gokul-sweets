"use client";

import Link from "next/link";
import {usePathname, useRouter} from "next/navigation";
import {useSyncExternalStore, type ReactNode} from "react";

function subscribeLocation(callback: () => void) {
    window.addEventListener("popstate", callback);
    window.addEventListener("hashchange", callback);
    return () => {window.removeEventListener("popstate", callback); window.removeEventListener("hashchange", callback);};
}
export function useCustomerLocation() {
    const pathname = usePathname();
    return useSyncExternalStore(subscribeLocation,
        () => window.location.pathname + window.location.search + window.location.hash, () => pathname);
}

export default function CustomerNotificationLink({children, className, label}: {
    children: ReactNode; className?: string; label?: string;
}) {
    const origin = useCustomerLocation();
    const router = useRouter();
    return <Link href={`/notifications?from=${encodeURIComponent(origin)}`} className={className} aria-label={label}
        onClick={event => {
            if (event.defaultPrevented || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey || event.button !== 0) return;
            event.preventDefault();
            router.push(`/notifications?from=${encodeURIComponent(window.location.pathname + window.location.search + window.location.hash)}`);
        }}>{children}</Link>;
}
