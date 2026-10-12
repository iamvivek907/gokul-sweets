import type {CSSProperties} from "react";

export default function NotificationIcon({kind = "bell", className = "h-5 w-5", style}: {kind?: string; className?: string; style?: CSSProperties}) {
    const ready = kind.includes("READY") && !kind.includes("CHANGED");
    const done = kind === "PICKED_UP" || kind === "DELIVERED" || kind.includes("PAID") || kind === "CONFIRMED";
    const delay = kind.includes("CHANGED") || kind.includes("REVIEW") || kind === "CANCELLED";
    return <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" strokeLinejoin="round" className={className} style={style} aria-hidden="true">
        {ready ? <><path d="M4 16h16M6 16v-3a6 6 0 0 1 12 0v3M12 5v2M3 20h18" /></>
            : done ? <><circle cx="12" cy="12" r="9"/><path d="m8 12 3 3 5-6"/></>
            : delay ? <><circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/></>
            : <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"/><path d="M12 2v1"/></>}
    </svg>;
}
