"use client";

import {useEffect, useRef, useState} from "react";
import {apiClient} from "@/services/apiClient";
import {proofFromWidget} from "@/lib/msg91Proof";
import {MSG91_WIDGET_ID, MSG91_WIDGET_TOKEN} from "@/lib/constants";

const widgetId = MSG91_WIDGET_ID;
const widgetToken = MSG91_WIDGET_TOKEN;
const scriptUrl = "https://verify.msg91.com/otp-provider.js";

type Msg91Window = Window & {initSendOTP?: (configuration: {
    widgetId: string;
    tokenAuth: string;
    success: (result: unknown) => void;
    failure: () => void;
}) => void};

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

export default function CustomerIdentityPanel() {
    const [available, setAvailable] = useState(false);
    const [verified, setVerified] = useState(false);
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState<string | null>(null);
    const alive = useRef(true);
    const exchanging = useRef(false);

    useEffect(() => {
        alive.current = true;
        if (!widgetId || !widgetToken) return;
        void (async () => {
            try {
                const config = await apiClient<{enabled: boolean}>("/api/storefront/customer-identity");
                if (!config.enabled || !alive.current) return;
                setAvailable(true);
                const session = await apiClient<{authenticated: boolean}>(
                    "/api/customer/identity/me", {credentials: "include"});
                if (alive.current) setVerified(session.authenticated);
            } catch {
                if (alive.current) setAvailable(false);
            }
        })();
        return () => {alive.current = false;};
    }, []);

    if (!available || !widgetId || !widgetToken) return null;

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
                            await apiClient<{authenticated: boolean}>("/api/customer/identity/exchange", {
                                method: "POST", credentials: "include", body: JSON.stringify({accessToken})
                            });
                            if (alive.current) {
                                setVerified(true);
                                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
                            }
                        } catch {
                            if (alive.current) setError("Verification could not be completed. Please try again.");
                        } finally {
                            exchanging.current = false;
                            if (alive.current) setBusy(false);
                        }
                    })();
                },
                failure: () => {if (alive.current) {setError("Verification was cancelled or failed."); setBusy(false);}}
            });
        } catch {
            setError("Verification is unavailable right now. You can continue as a guest.");
            setBusy(false);
        }
    }

    async function logout() {
        setBusy(true);
        setError(null);
        try {
            await apiClient<void>("/api/customer/identity/logout", {method: "POST", credentials: "include"});
            if (alive.current) {
                setVerified(false);
                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
            }
        } catch {
            if (alive.current) setError("Could not sign out. Please try again.");
        } finally {
            if (alive.current) setBusy(false);
        }
    }

    return <section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Phone verification">
        <p className="text-xs font-bold uppercase tracking-[0.15em] text-[#a56e2e]">Your account</p>
        <h2 className="mt-2 text-xl font-semibold text-[#241715]">{verified ? "Phone verified" : "Verify your phone"}</h2>
        <p className="mt-2 text-sm leading-6 text-[#756763]">
            {verified ? "Your phone is verified for this session."
                : "Optional verification helps secure your account. You can still place a pickup order as a guest."}
        </p>
        <button type="button" disabled={busy} onClick={() => {void (verified ? logout() : start());}}
            className="mt-4 min-h-11 rounded-full bg-[#7a1625] px-6 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {busy ? "Please wait…" : verified ? "Sign out" : "Verify with SMS"}
        </button>
        {error && <p role="alert" className="mt-3 text-sm text-[#9e2732]">{error}</p>}
    </section>;
}
