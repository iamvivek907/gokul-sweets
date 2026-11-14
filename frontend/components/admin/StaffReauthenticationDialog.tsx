"use client";

import {useEffect, useRef, useState, type FormEvent} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import {staffReauthenticationEvent} from "@/services/adminApi";

export default function StaffReauthenticationDialog() {
    const {login, profile} = useAdminAuth();
    const dialog = useRef<HTMLDialogElement>(null);
    const submitting = useRef(false);
    const [isOpen, setIsOpen] = useState(false);
    const [password, setPassword] = useState("");
    const [code, setCode] = useState("");
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
    const [confirmed, setConfirmed] = useState(false);

    useEffect(() => {
        function open() {
            const element = dialog.current;
            if (!element || element.open) return;
            setPassword("");
            setCode("");
            setError("");
            setConfirmed(false);
            setIsOpen(true);
        }
        window.addEventListener(staffReauthenticationEvent, open);
        return () => window.removeEventListener(staffReauthenticationEvent, open);
    }, []);

    useEffect(() => {
        const element = dialog.current;
        if (isOpen && element && !element.open) element.showModal();
    }, [isOpen]);

    function clearCredentials() {
        setPassword("");
        setCode("");
        setError("");
    }

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        if (!profile || submitting.current) return;
        submitting.current = true;
        setBusy(true);
        setError("");
        try {
            await login(profile.username, password, code || undefined);
            clearCredentials();
            dialog.current?.close();
            setConfirmed(true);
        } catch (cause) {
            setError(cause instanceof TypeError
                ? "Could not reach the server. Check your connection and try again."
                : cause instanceof Error ? cause.message : "Could not confirm your sign-in.");
        } finally {
            submitting.current = false;
            setBusy(false);
        }
    }

    return <>
        <dialog ref={dialog} aria-labelledby="staff-reauth-title"
            className="m-auto w-[calc(100%-2rem)] max-w-md rounded-xl border border-[#eadfd6] bg-white p-5 text-[#241715] backdrop:bg-black/40"
            onClose={() => {
                setIsOpen(false);
                clearCredentials();
            }}
            onCancel={event => {if (submitting.current) event.preventDefault();}}>
            {isOpen && <form onSubmit={submit} className="space-y-4">
                <h2 id="staff-reauth-title" className="text-xl font-bold">Confirm your sign-in</h2>
                <p className="text-sm">Sign in again as {profile?.username} to allow sensitive changes, then retry the original action.</p>
                <input type="text" name="username" autoComplete="username" value={profile?.username ?? ""} readOnly hidden />
                <label className="block text-sm font-semibold">Current password
                    <input required type="password" autoComplete="current-password" value={password}
                        onChange={event => setPassword(event.target.value)} disabled={busy}
                        className="mt-1 block min-h-11 w-full rounded-lg border p-2" />
                </label>
                <label className="block text-sm font-semibold">Authenticator or recovery code (if required)
                    <input autoComplete="one-time-code" value={code} onChange={event => setCode(event.target.value)}
                        disabled={busy} className="mt-1 block min-h-11 w-full rounded-lg border p-2" />
                </label>
                {error && <p role="alert" className="text-sm text-red-800">{error}</p>}
                <div className="flex flex-wrap justify-end gap-2">
                    <button type="button" disabled={busy} onClick={() => dialog.current?.close()}
                        className="min-h-11 rounded-lg border px-4">Cancel</button>
                    <button type="submit" disabled={busy} className="min-h-11 rounded-lg bg-[#7a1625] px-4 text-white disabled:opacity-50">
                        {busy ? "Confirming…" : "Confirm sign-in"}
                    </button>
                </div>
            </form>}
        </dialog>
        <div role="status" className="sr-only">{confirmed ? "Sign-in confirmed. Retry the original action." : ""}</div>
    </>;
}
