"use client";
import {T} from "@/lib/language";


import {useEffect, useState} from "react";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import ConsentPreferences from "@/components/customer/ConsentPreferences";
import {apiClient} from "@/services/apiClient";
import type {CustomerSession} from "@/components/customer/CustomerIdentityPanel";

export default function PrivacyPage() {
    const [status, setStatus] = useState<"loading" | "signed-in" | "guest" | "error">("loading");

    useEffect(() => {
        let active = true;
        void apiClient<CustomerSession>("/api/customer/identity/me", {credentials: "include"})
            .then(session => {if (active) setStatus(session.authenticated ? "signed-in" : "guest");})
            .catch(() => {if (active) setStatus("error");});
        return () => {active = false;};
    }, []);

    return <AppShell>
        <main className="mx-auto max-w-3xl px-4 pb-24 sm:px-6">
            <Link href="/profile" className="text-sm font-semibold text-[#7a1625]">← Back to profile</Link>
            <p className="mt-8 text-xs font-bold uppercase tracking-[0.16em] text-[#a56e2e]"><T text="Your account" /></p>
            <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">Privacy and your data</h1>
            <p className="mt-3 max-w-2xl text-base leading-7 text-[#756763]">
                Decide which optional messages and insights you allow. Your pickup orders and essential order updates work without these choices.
            </p>
            {status === "signed-in" ? <ConsentPreferences /> :
                <section className="mt-8 rounded-3xl border border-[#e8d7c9] bg-white p-6" role="status">
                    <h2 className="text-lg font-semibold text-[#241715]">
                        {status === "loading" ? "Loading your privacy choices…" :
                            status === "error" ? "Privacy choices are temporarily unavailable" : "Sign in to manage privacy"}
                    </h2>
                    <p className="mt-2 text-sm text-[#756763]">
                        {status === "guest" ? "Verify your phone from your profile to view your account choices." :
                            status === "error" ? "Please refresh this page or try again shortly." : "Checking your account."}
                    </p>
                    {status === "guest" && <Link href="/profile" className="mt-4 inline-flex min-h-11 items-center rounded-xl bg-[#7a1625] px-5 text-sm font-semibold text-white">Go to profile</Link>}
                </section>}
        </main>
    </AppShell>;
}
