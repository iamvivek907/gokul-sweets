"use client";

import {useEffect, useId, useRef, useState} from "react";
import {T} from "@/lib/language";

export default function LogoutConfirmation({open, onClose, onConfirm}: {
    open: boolean; onClose: () => void; onConfirm: () => Promise<boolean>;
}) {
    const titleId = useId();
    const dialog = useRef<HTMLDialogElement>(null);
    const submitting = useRef(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState(false);
    useEffect(() => {
        if (open) dialog.current?.showModal();
        else dialog.current?.close();
    }, [open]);
    function close() {setError(false); onClose();}
    async function confirm() {
        if (submitting.current) return;
        submitting.current = true; setBusy(true); setError(false);
        try {if (await onConfirm()) close(); else setError(true);}
        catch {setError(true);}
        finally {submitting.current = false; setBusy(false);}
    }
    return <dialog ref={dialog} aria-labelledby={titleId}
        className="logout-confirmation m-auto w-[calc(100%_-_2rem)] max-w-sm rounded-3xl border border-[#eadfd6] bg-white p-6 text-[#241715] shadow-xl backdrop:bg-black/45"
        onCancel={event => {event.preventDefault(); if (!submitting.current) close();}}
        onClick={event => {if (event.target === event.currentTarget && !submitting.current) {
            const bounds = event.currentTarget.getBoundingClientRect();
            if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) close();
        }}}>
        <h2 id={titleId} className="text-xl font-bold"><T text="Log out of Gokul?" /></h2>
        <p className="mt-3 text-sm leading-6 text-[#756763]"><T text="You will need to verify your phone again to sign in." /></p>
        {error && <p role="alert" className="mt-3 text-sm text-[#9e2732]"><T text="Could not sign out. Please try again." /></p>}
        <div className="mt-5 flex flex-col gap-2">
            <button type="button" autoFocus disabled={busy} onClick={close} className="min-h-11 rounded-full bg-[#143936] px-5 font-semibold text-white disabled:opacity-50"><T text="Stay signed in" /></button>
            <button type="button" disabled={busy} onClick={() => void confirm()} className="min-h-11 rounded-full border border-[#eadfd6] px-5 font-semibold text-[#7a1625] disabled:opacity-50"><T text={busy ? "Logging out…" : "Log out"} /></button>
        </div>
    </dialog>;
}
