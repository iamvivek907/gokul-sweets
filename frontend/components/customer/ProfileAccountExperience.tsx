"use client";

import {useState} from "react";
import Link from "next/link";
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

    return <div className="mx-auto max-w-5xl px-4 pb-28 pt-5 sm:px-6 sm:pt-8">
        <header className="account-profile-heading relative overflow-hidden rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-10">
            <p className="text-xs font-semibold uppercase tracking-[0.17em] text-[#c88a20]">Gokul Sweets · Your space</p>
            <h1 className="mt-3 text-3xl font-bold tracking-tight text-[#241715] sm:text-5xl">{session?.authenticated ? `Welcome${session.name ? `, ${session.name}` : " back"}` : "Welcome to Gokul"}</h1>
            <p className="mt-4 max-w-xl text-sm leading-7 text-[#756763]">Your orders, favourite sweets and saved choices in one place.</p>
            {session?.authenticated && <span className="mt-5 inline-flex rounded-full border border-[#c88a20] px-4 py-2 text-xs font-semibold text-[#7a1625]">✓ Verified account</span>}
        </header>
        <CustomerAccountHub session={session} />
        <div className="mt-7 grid gap-6 md:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
            <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]">Account and privacy</h2>
                <p className="mt-2 text-sm text-[#756763]">Manage your verified number and name, then choose how your data is used.</p>
                <Link href="/profile/privacy" className="mt-5 inline-flex min-h-11 items-center font-semibold text-[#7a1625] underline">Privacy and data choices →</Link></section>
            <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 sm:p-8"><h2 className="text-xl font-bold text-[#241715]">Enjoy Gokul your way</h2>
                <p className="mt-2 text-sm text-[#756763]">Browse the current branch menu for today&apos;s selection.</p>
                <Link href="/menu" className="mt-5 inline-flex min-h-11 items-center font-semibold text-[#7a1625] underline">Explore the menu →</Link></section>
        </div>
        <div className="mx-auto mt-8 max-w-2xl"><CustomerIdentityPanel onSessionChange={setSession} /><InstallAppBanner /></div>
    </div>;
}
