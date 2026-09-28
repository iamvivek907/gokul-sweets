"use client";

import {useState} from "react";
import CustomerIdentityPanel, {type CustomerSession} from "@/components/customer/CustomerIdentityPanel";
import CustomerAccountHub from "@/components/customer/CustomerAccountHub";
import InstallAppBanner from "@/components/pwa/InstallAppBanner";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";

export default function ProfileAccountExperience() {
    const [session, setSession] = useState<CustomerSession | null>(null);
    const {features, error, retry} = useStorefrontConfiguration();
    const enabled = features?.customerAccountHub === true;

    if (!features) return <div className="mx-auto max-w-xl py-8 text-sm text-[#756763]" role="status">
        {error ?? "Loading your profile…"}
        {error && <button type="button" onClick={retry} className="ml-3 font-semibold text-[#7a1625] underline">Try again</button>}
    </div>;

    if (!enabled) return <div className="mx-auto max-w-xl"><p className="text-xs font-semibold uppercase tracking-wide text-[#c88a20]">Your account</p>
        <h1 className="mt-1 text-2xl font-bold">Profile</h1><InstallAppBanner /><CustomerIdentityPanel />
        <section className="mt-6 rounded-3xl border border-[#e8d7c9] bg-white p-5 shadow-sm sm:p-6" aria-label="Rewards">
            <h2 className="text-xl font-semibold text-[#241715]">Rewards</h2><p className="mt-2 text-sm leading-6 text-[#756763]">Earned points are not available yet. A balance will appear here when the rewards programme is launched.</p></section></div>;

    return <div className="mx-auto max-w-6xl px-4 pb-28 pt-5 sm:px-6 sm:pt-8">
        {session?.authenticated ? <CustomerAccountHub session={session} /> :
            <header className="account-profile-heading rounded-3xl border border-[#eadfd6] bg-white p-7 sm:p-10">
                <p className="text-xs font-bold uppercase tracking-[0.16em] text-[#c88a20]">Your Gokul profile</p>
                <h1 className="mt-3 text-3xl font-bold text-[#241715] sm:text-4xl">All your Gokul moments, together.</h1>
                <p className="mt-3 max-w-xl text-sm text-[#756763]">Sign in to see your earned badges, orders and saved choices.</p>
            </header>}
        <section id="account-details" className="account-details-section mt-7 max-w-3xl scroll-mt-24">
            <CustomerIdentityPanel onSessionChange={setSession} />
        </section>
        <div className="mt-8 max-w-3xl"><InstallAppBanner /></div>
    </div>;
}
