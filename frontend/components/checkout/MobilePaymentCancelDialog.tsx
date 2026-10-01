"use client";
import {useEffect, useRef} from "react";
import {T, useTranslation} from "@/lib/language";
import "./MobilePaymentCancelDialog.css";

export default function MobilePaymentCancelDialog({active, busy, error, onKeep, onCancel}: {
    active: boolean; busy: boolean; error: string | null; onKeep: () => void; onCancel: () => Promise<void>;
}) {
    const dialog = useRef<HTMLDialogElement>(null);
    const translate = useTranslation();
    useEffect(() => {
        const surface = dialog.current;
        if (!surface) return;
        const media = window.matchMedia("(max-width: 640px)");
        const sync = () => {
            if (active && media.matches && !surface.open) surface.showModal();
            else if (!active || !media.matches) surface.close();
        };
        sync(); media.addEventListener("change", sync);
        return () => {surface.close(); media.removeEventListener("change", sync);};
    }, [active]);
    return <dialog ref={dialog} className="gokul-payment-cancel-dialog" aria-labelledby="mobile-cancel-title"
        onCancel={event => {event.preventDefault(); if (!busy) onKeep();}}>
        <h2 id="mobile-cancel-title"><T text="Cancel this payment?" /></h2>
        <p><T text="We’ll check for a confirmed payment first. If it hasn’t completed, we’ll release the reservation and keep your cart ready to try again." /></p>
        {error && <p role="alert">{translate(error)}</p>}
        <div><button type="button" disabled={busy} onClick={onKeep}><T text="Keep payment" /></button>
            <button type="button" disabled={busy} onClick={() => void onCancel()}>{busy ? translate("Checking payment…") : translate("Confirm cancellation")}</button></div>
    </dialog>;
}
