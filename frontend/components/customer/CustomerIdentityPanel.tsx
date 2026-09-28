"use client";

import {useEffect, useRef, useState} from "react";
import {apiClient} from "@/services/apiClient";
import {proofFromWidget} from "@/lib/msg91Proof";
import {MSG91_WIDGET_ID, MSG91_WIDGET_TOKEN} from "@/lib/constants";
import ConsentPreferences from "@/components/customer/ConsentPreferences";

const widgetId = MSG91_WIDGET_ID;
const widgetToken = MSG91_WIDGET_TOKEN;
const scriptUrl = "https://verify.msg91.com/otp-provider.js";

type Msg91Window = Window & {initSendOTP?: (configuration: {
    widgetId: string;
    tokenAuth: string;
    success: (result: unknown) => void;
    failure: () => void;
}) => void};

type CustomerSession = {authenticated: boolean; phone?: string};

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
    const [availability, setAvailability] = useState<"loading" | "ready" | "disabled" | "error">(
        widgetId && widgetToken ? "loading" : "disabled");
    const [session, setSession] = useState<CustomerSession>({authenticated: false});
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
                if (!alive.current) return;
                if (!config.enabled) {setAvailability("disabled"); return;}
                setAvailability("ready");
                const session = await apiClient<CustomerSession>(
                    "/api/customer/identity/me", {credentials: "include"});
                if (alive.current) setSession(session);
            } catch {
                if (alive.current) setAvailability("error");
            }
        })();
        return () => {alive.current = false;};
    }, []);

    if (availability === "loading") return <p className="mt-6 text-sm text-[#756763]" role="status">Checking phone verification…</p>;
    if (availability !== "ready" || !widgetId || !widgetToken) return <section
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
                                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
                            }
                        } catch {
                            if (alive.current) setError("The OTP was accepted, but sign-in could not be completed. Please try again.");
                        } finally {
                            exchanging.current = false;
                            if (alive.current) setBusy(false);
                        }
                    })();
                },
                failure: () => {if (alive.current && !exchanging.current) {setError("Verification was cancelled or failed."); setBusy(false);}}
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
                setSession({authenticated: false});
                window.dispatchEvent(new Event("gokul-customer-identity-changed"));
            }
        } catch {
            if (alive.current) setError("Could not sign out. Please try again.");
        } finally {
            if (alive.current) setBusy(false);
        }
    }

    return <><section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Phone verification">
        <p className="text-xs font-bold uppercase tracking-[0.15em] text-[#a56e2e]">Your account</p>
        <h2 className="mt-2 text-xl font-semibold text-[#241715]">{session.authenticated ? "Signed in" : "Verify your phone"}</h2>
        <p className="mt-2 text-sm leading-6 text-[#756763]">
            {session.authenticated ? "Your phone is verified. This device stays signed in for up to 30 days unless you sign out or verify again on another device."
                : "Optional verification helps secure your account. You can still place a pickup order as a guest."}
        </p>
        {session.authenticated && session.phone && <p className="mt-2 text-sm font-semibold text-[#241715]">
            Verified phone: <span className="select-text">{session.phone}</span>
        </p>}
        <button type="button" disabled={busy} onClick={() => {void (session.authenticated ? logout() : start());}}
            className="mt-4 min-h-11 rounded-full bg-[#7a1625] px-6 py-2 text-sm font-semibold text-white disabled:opacity-50">
            {busy ? "Please wait…" : session.authenticated ? "Sign out" : "Verify with SMS"}
        </button>
        {error && <p role="alert" className="mt-3 text-sm text-[#9e2732]">{error}</p>}
    </section>{session.authenticated && <ConsentPreferences />}</>;
}
