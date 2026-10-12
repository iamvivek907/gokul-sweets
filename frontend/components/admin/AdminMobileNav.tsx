"use client";
import {T} from "@/lib/language";

import {useEffect, useRef, useState} from "react";
import {createPortal} from "react-dom";
import AdminNavigation from "./AdminNavigation";

export default function AdminMobileNav() {
    const [open, setOpen] = useState(false);
    const trigger = useRef<HTMLButtonElement>(null);
    const panel = useRef<HTMLDivElement>(null);
    useEffect(() => {
        if (!open) return;
        const previous = document.body.style.overflow;
        const returnFocus = trigger.current;
        document.body.style.overflow = "hidden";
        panel.current?.querySelector<HTMLButtonElement>("button")?.focus();
        return () => {document.body.style.overflow = previous; returnFocus?.focus();};
    }, [open]);
    return <div className="lg:hidden">
        <button ref={trigger} type="button" aria-label="Admin menu" aria-expanded={open} aria-haspopup="dialog" onClick={() => setOpen(true)}
            className="flex min-h-11 min-w-11 items-center justify-center gap-2 rounded-xl border border-[#eadfd6] px-2 font-semibold sm:px-4"><svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M4 6h16M4 12h16M4 18h16" stroke="currentColor" strokeWidth="2" strokeLinecap="round"/></svg><span className="hidden sm:inline"><T text="Admin menu" /></span></button>
        {open && createPortal(<div className="fixed inset-0 z-50 bg-black/40" onClick={() => setOpen(false)}>
            <div ref={panel} role="dialog" aria-modal="true" aria-label="Admin navigation"
                className="h-full w-[min(90vw,340px)] overflow-y-auto bg-white p-4" onClick={event => event.stopPropagation()}
                onKeyDown={event => {
                    if (event.key === "Escape") setOpen(false);
                    if (event.key !== "Tab") return;
                    const targets = panel.current?.querySelectorAll<HTMLElement>('a[href], button:not([disabled])');
                    if (!targets?.length) return;
                    const first = targets[0], last = targets[targets.length - 1];
                    if (event.shiftKey && document.activeElement === first) {event.preventDefault(); last.focus();}
                    else if (!event.shiftKey && document.activeElement === last) {event.preventDefault(); first.focus();}
                }}>
                <div className="mb-4 flex items-center justify-between"><strong><T text="Admin tools" /></strong>
                    <button className="min-h-11 px-3" onClick={() => setOpen(false)}><T text="Close" /></button></div>
                <AdminNavigation onNavigate={() => setOpen(false)} />
            </div>
        </div>, document.body)}
    </div>;
}
