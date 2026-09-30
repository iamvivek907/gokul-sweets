"use client";

import {useEffect, useRef, useState} from "react";
import {apiClient} from "@/services/apiClient";
import {ApiError} from "@/services/apiClient";
import {proofFromWidget} from "@/lib/msg91Proof";
import {MSG91_WIDGET_ID, MSG91_WIDGET_TOKEN} from "@/lib/constants";
import Link from "next/link";

const widgetId = MSG91_WIDGET_ID;
const widgetToken = MSG91_WIDGET_TOKEN;
const scriptUrl = "https://verify.msg91.com/otp-provider.js";

type Msg91Window = Window & {initSendOTP?: (configuration: {
    widgetId: string;
    tokenAuth: string;
    success: (result: unknown) => void;
    failure: () => void;
}) => void};

export type CustomerSession = {authenticated: boolean; phone?: string; name?: string};

async function loadWidget(): Promise<Msg91Window> {
    const sdk = window as Msg91Window;
    if (sdk.initSendOTP) return sdk;
    await new Promise<void>((resolve, reject) => {
        const existing = document.querySelector<HTMLScriptElement>(`script[src="${scriptUrl}"]`);
        const script = existing ?? document.createElement("script");
        const timeout = window.setTimeout(() => reject(new Error("Widget unavailable")), 10000);
        script.addEventListener("load", () => {window.clearTimeout(timeout); resolve();}, {once: true});
        script.addEventListener("error", () => {window.clearTimeout(timeout); reject(new Error("Widget unavailable"));}, {once: true});
        if (!existing) {
            script.src = scriptUrl;
            script.async = true;
            document.head.appendChild(script);
        }
    });
    if (!sdk.initSendOTP) throw new Error("Widget unavailable");
    return sdk;
}

export default function CustomerIdentityPanel({mode = "profile", onSessionChange}: {
    mode?: "profile" | "checkout" | "occasion";
    onSessionChange?: (session: CustomerSession) => void;
}) {
    const [availability, setAvailability] = useState<"loading" | "ready" | "disabled" | "error">(
        "loading");
    const [session, setSession] = useState<CustomerSession>({authenticated: false});
    const [busy, setBusy] = useState(false);
    const [promptDismissed, setPromptDismissed] = useState(false);
    const promptButton = useRef<HTMLButtonElement>(null);
    const [error, setError] = useState<string | null>(null);
    const [nameDraft, setNameDraft] = useState("");
    const [nameError, setNameError] = useState<string | null>(null);
    const alive = useRef(true);
    const exchanging = useRef(false);

    useEffect(() => {
        alive.current = true;
        void (async () => {
            try {
                const config = await apiClient<{enabled: boolean}>("/api/storefront/customer-identity");
                if (!alive.current) return;
                if (!config.enabled) {setAvailability("disabled"); onSessionChange?.({authenticated: false}); return;}
                const session = await apiClient<CustomerSession>(
                    "/api/customer/identity/me", {credentials: "include"});
                if (alive.current) {setSession(session); setNameDraft(session.name ?? ""); onSessionChange?.(session); setAvailability("ready");}
            } catch {
                if (alive.current) {setAvailability("error"); onSessionChange?.({authenticated: false});}
            }
        })();
        return () => {alive.current = false;};
    }, [onSessionChange]);

    useEffect(() => {
        if (mode !== "checkout" || availability !== "ready" || session.authenticated || promptDismissed) return;
        promptButton.current?.focus();
        function onEscape(event: KeyboardEvent) {
            if (event.key === "Escape") setPromptDismissed(true);
        }
        window.addEventListener("keydown", onEscape);
        return () => window.removeEventListener("keydown", onEscape);
    }, [mode, availability, session.authenticated, promptDismissed]);

    if (availability === "loading") return <p className="mt-6 text-sm text-[#756763]" role="status">Checking phone verification…</p>;
    if (availability !== "ready" || !session.authenticated && (!widgetId || !widgetToken)) return mode === "checkout" ? null : <section
        className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6"
        aria-label="Phone verification">
        <h2 className="text-xl font-semibold text-[#241715]">Phone verification is unavailable</h2>
        <p className="mt-2 text-sm leading-6 text-[#756763]">
            {availability === "error"
                ? "We could not check verification right now. Please try again later. You can still place a pickup order as a guest."
                : "SMS sign-in is not enabled for this storefront yet. You can still place a pickup order as a guest."}
        </p>
    </section>;

    async function start() {
        if (busy || !widgetId || !widgetToken) return;
        setBusy(true);
        setError(null);
        try {
            await apiClient<void>("/api/customer/identity/start", {
                method: "POST", credentials: "include"
            });
            const sdk = await loadWidget();
            sdk.initSendOTP?.({
                widgetId,
                tokenAuth: widgetToken,
                success: result => {
                    if (exchanging.current) return;
                    const accessToken = proofFromWidget(result);
                    if (!accessToken) {
                        if (alive.current) {setError("Verification could not be completed. Please try again."); setBusy(false);}
                        return;
                    }
                    exchanging.current = true;
                    void (async () => {
                        try {
                            await apiClient<CustomerSession>("/api/customer/identity/exchange", {
                                method: "POST", credentials: "include", body: JSON.stringify({accessToken})
                            });
                            const signedIn = await apiClient<CustomerSession>(
                                "/api/customer/identity/me", {credentials: "include"});
                            if (!signedIn.authenticated || !signedIn.phone) {
                                throw new Error("Customer session was not established");
                            }
                            if (alive.current) {
                                setSession(signedIn);
                                onSessionChange?.(signedIn);
                                setNameDraft(signedIn.name ?? "");
                                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
                            }
                        } catch (failure) {
                            if (alive.current) setError(failure instanceof ApiError && failure.status === 429
                                ? "Too many sign-in attempts. Please wait up to an hour before trying a new OTP."
                                : "The OTP was accepted, but sign-in could not be completed. Please try again.");
                        } finally {
                            exchanging.current = false;
                            if (alive.current) setBusy(false);
                        }
                    })();
                },
                failure: () => {if (alive.current && !exchanging.current) {setError("Verification was cancelled or failed."); setBusy(false);}}
            });
        } catch (failure) {
            setError(failure instanceof ApiError && failure.status === 429
                ? "Too many verification attempts. Please wait up to an hour before trying again."
                : "Verification is unavailable right now. You can continue as a guest.");
            setBusy(false);
        }
    }

    async function logout() {
        setBusy(true);
        setError(null);
        try {
            await apiClient<void>("/api/customer/identity/logout", {method: "POST", credentials: "include"});
            if (alive.current) {
                setSession({authenticated: false});
                onSessionChange?.({authenticated: false});
                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
            }
        } catch {
            if (alive.current) setError("Could not sign out. Please try again.");
        } finally {
            if (alive.current) setBusy(false);
        }
    }

    async function saveName() {
        const name = nameDraft.trim();
        if (!name || name.length < 2 || name.length > 80) {
            setNameError("Enter your name (2 to 80 characters).");
            return;
        }
        setBusy(true);
        setNameError(null);
        try {
            await apiClient<void>("/api/customer/identity/me/name", {
                method: "PUT", credentials: "include", body: JSON.stringify({name})
            });
            const updated = await apiClient<CustomerSession>("/api/customer/identity/me", {credentials: "include"});
            if (!updated.authenticated) throw new Error("Session expired");
            if (alive.current) {setSession(updated); onSessionChange?.(updated); window.dispatchEvent(new Event("gokul-customer-identity-changed"));}
        } catch {
            if (alive.current) setNameError("Could not save your name. Please check it and try again.");
        } finally {
            if (alive.current) setBusy(false);
        }
    }

    if (mode === "occasion" && session.authenticated) return <div className="flex flex-wrap items-center justify-between gap-3" aria-label="Verified occasion contact"><div><p className="font-semibold text-[#245b38]">✓ Phone verified</p><p className="mt-1 text-sm">{session.name || "Your Gokul account"} · {session.phone}</p></div><Link href="/profile" className="text-sm underline">Manage account</Link></div>;

    if (mode === "checkout") return <>
        {session.authenticated ? <div className="mb-5 flex flex-wrap items-center justify-between gap-3 rounded-2xl bg-[#f4faf4] p-4" aria-label="Verified pickup contact">
            <div><p className="text-sm font-semibold text-[#245b38]">Signed in · phone verified</p>
                <p className="mt-1 text-xs text-[#465a4a]">Your account details are filled in below.</p></div>
            <Link href="/profile" className="text-sm font-semibold text-[#7a1625] underline">Account</Link>
        </div> : <div className="mb-5 rounded-2xl border border-[#eadfd6] p-4">
            <p className="text-sm font-semibold text-[#241715]">Checking out as a guest?</p>
            <p className="mt-1 text-xs text-[#756763]">Sign in to fill your verified number automatically.</p>
            <button type="button" onClick={() => setPromptDismissed(false)} className="mt-2 text-sm font-semibold text-[#7a1625] underline">Sign in with SMS</button>
        </div>}
        {!session.authenticated && !promptDismissed && <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/55 p-4" onMouseDown={event => {if (event.target === event.currentTarget) setPromptDismissed(true);}}>
            <div role="dialog" aria-modal="true" aria-labelledby="checkout-signin-title" className="w-full max-w-md rounded-3xl bg-white p-6 shadow-xl">
                <h2 id="checkout-signin-title" className="text-2xl font-bold text-[#241715]">Sign in for a faster checkout</h2>
                <p className="mt-2 text-sm leading-6 text-[#756763]">Verify your number to fill your pickup details. You can also continue as a guest.</p>
                <button ref={promptButton} type="button" disabled={busy} onClick={() => {void start();}}
                    className="mt-6 min-h-12 w-full rounded-xl bg-[#7a1625] px-5 font-semibold text-white disabled:opacity-50">{busy ? "Please wait…" : "Verify with SMS"}</button>
                <button type="button" onClick={() => setPromptDismissed(true)} className="mt-3 min-h-11 w-full rounded-xl border border-[#eadfd6] font-semibold text-[#241715]">Continue as guest</button>
                {error && <p role="alert" className="mt-3 text-sm text-[#9e2732]">{error}</p>}
            </div>
        </div>}
    </>;

    return <><section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Phone verification">
        <p className="text-xs font-bold uppercase tracking-[0.15em] text-[#a56e2e]">Your account</p>
        <h2 className="mt-2 text-xl font-semibold text-[#241715]">{session.authenticated ? "Account details" : "Verify your phone"}</h2>
        <p className="mt-2 text-sm leading-6 text-[#756763]">
            {session.authenticated ? "Update the name shown on your account or manage your sign-in."
                : "Optional verification helps secure your account. You can still place a pickup order as a guest."}
        </p>
        {session.authenticated && session.phone && <p className="mt-2 text-sm font-semibold text-[#241715]">
            Verified phone: <span className="select-text">{session.phone}</span>
        </p>}
        {session.authenticated && <div className="mt-4">
            <label htmlFor="customer-display-name" className="block text-sm font-semibold text-[#241715]">Your name</label>
            <div className="mt-2 flex flex-wrap items-center gap-2">
                <input id="customer-display-name" type="text" autoComplete="name" maxLength={80} value={nameDraft}
                    onChange={event => setNameDraft(event.target.value)}
                    className="min-h-11 min-w-0 flex-1 rounded-xl border border-[#d8c6ba] bg-white px-3 text-base text-[#241715]"
                    placeholder="Enter your name" />
                <button type="button" disabled={busy || nameDraft.trim() === (session.name ?? "")}
                    onClick={() => {void saveName();}}
                    className="min-h-11 rounded-full border border-[#d8c6ba] px-4 text-sm font-semibold disabled:opacity-50">Save name</button>
            </div>
            {nameError && <p role="alert" className="mt-2 text-sm text-[#9e2732]">{nameError}</p>}
        </div>}
        <button type="button" disabled={busy} onClick={() => {void (session.authenticated ? logout() : start());}}
            className="mt-4 min-h-11 rounded-full bg-[#7a1625] px-6 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {busy ? "Please wait…" : session.authenticated ? "Sign out" : "Verify with SMS"}
        </button>
        {error && <p role="alert" className="mt-3 text-sm text-[#9e2732]">{error}</p>}
    </section>{session.authenticated && <Link href="/profile/privacy" className="mt-4 flex min-h-12 items-center justify-between rounded-2xl border border-[#e8d7c9] bg-white px-5 text-sm font-semibold text-[#7a1625]">Privacy and data choices <span aria-hidden="true">→</span></Link>}</>;
}
