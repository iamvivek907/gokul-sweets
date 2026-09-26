"use client";

import Link from "next/link";
import {useState, useSyncExternalStore, type ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";
import {hasChosenEntryIntent, rememberEntryIntent, shouldShowIntentGateway} from "@/lib/entryIntent";

const subscribeToSessionChoice = () => () => {};

/** The existing homepage remains the entire OFF path; no extra branch or permission request. */
export default function HomeEntry({children}: {children: ReactNode}) {
    const {features, error} = useStorefrontConfiguration();
    const {branch} = useSelectedBranch();
    const remembered = useSyncExternalStore(subscribeToSessionChoice, hasChosenEntryIntent, () => null);
    const [justChosen, setJustChosen] = useState(false);
    const chosenInTab = justChosen || remembered;

    if (!features && !error) {
        return <AppShell showSocialPopup={false}><div role="status" className="mx-auto max-w-5xl px-5 py-16 text-[#756763]">Preparing your Gokul visit…</div></AppShell>;
    }
    // Configuration failures keep the existing safe homepage and its own Retry/Menu fallback.
    if (!features?.preHomeIntentGateway || error) return <>{children}</>;
    if (chosenInTab === null) {
        return <AppShell showSocialPopup={false}><div role="status" className="mx-auto max-w-5xl px-5 py-16 text-[#756763]">Preparing your Gokul visit…</div></AppShell>;
    }
    if (!shouldShowIntentGateway(true, Boolean(branch), chosenInTab)) return <>{children}</>;

    function choose() {
        rememberEntryIntent();
        setJustChosen(true);
    }

    return <AppShell showSocialPopup={false}>
        <section aria-labelledby="welcome-title" className="mx-auto grid min-h-[min(730px,85dvh)] max-w-6xl items-center gap-8 px-4 py-10 sm:px-7 md:grid-cols-[1.15fr_0.85fr] md:py-16">
            <div>
                <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#a46e1d]">A little something, just for you</p>
                <h1 id="welcome-title" className="mt-4 max-w-2xl font-serif text-4xl leading-tight font-bold tracking-tight text-[#5d0f1b] sm:text-5xl lg:text-6xl">How would you like to enjoy Gokul today?</h1>
                <p className="mt-5 max-w-xl text-base leading-7 text-[#65544f]">Find your favourites for pickup, talk to us about a celebration, or take a look around. You can change your mind anytime.</p>
                <div className="mt-8 grid gap-3 sm:grid-cols-2">
                    <Link onClick={choose} href="/menu" className="flex min-h-28 flex-col justify-center rounded-2xl bg-[#7a1625] px-5 py-4 text-white! shadow-lg shadow-[#7a1625]/15 focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-blue-700">
                        <strong className="text-lg">Order food</strong><span className="mt-1 text-sm text-white/80">Choose a shop, then your favourites →</span>
                    </Link>
                    <Link onClick={choose} href="/about#our-branches" className="flex min-h-28 flex-col justify-center rounded-2xl border border-[#e4cda7] bg-white px-5 py-4 text-[#5d0f1b]! focus-visible:outline-3 focus-visible:outline-offset-3 focus-visible:outline-blue-700">
                        <strong className="text-lg">Plan an occasion</strong><span className="mt-1 text-sm text-[#675850]">Speak with your nearest Gokul shop →</span>
                    </Link>
                </div>
                <button type="button" onClick={choose} className="mt-5 min-h-12 rounded-xl px-3 font-semibold text-[#7a1625] underline underline-offset-4 focus-visible:outline-3 focus-visible:outline-blue-700">Just explore the home page →</button>
                <p className="mt-5 text-xs text-[#756763]">No sign-in, video, or location permission needed to browse.</p>
            </div>
            <div aria-hidden="true" className="relative min-h-64 overflow-hidden rounded-[2rem] bg-linear-to-br from-[#f6dfad] via-[#d6a463] to-[#7a1625] p-8 shadow-[0_24px_65px_rgba(93,15,27,0.15)] md:min-h-[440px]">
                <div className="absolute -right-12 -top-12 h-56 w-56 rounded-full border-24 border-white/15" />
                <div className="absolute bottom-10 left-8 max-w-[240px] rounded-3xl border border-white/55 bg-[#fffaf3]/95 p-6 shadow-xl sm:left-12"><span className="font-serif text-3xl font-bold text-[#7a1625]">Gokul<br />Sweets</span><p className="mt-2 text-sm text-[#66534c]">Fresh sweets. Happier moments.</p></div>
            </div>
        </section>
    </AppShell>;
}
