"use client";
import MobilePageBack from "@/components/customer/MobilePageBack";
import {T} from "@/lib/language";


import {useEffect, useState} from "react";
import {useParams} from "next/navigation";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import BranchDiscovery from "@/components/branch/BranchDiscovery";
import BranchDetails from "@/components/branch/BranchDetails";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {checkOperationalBranch} from "@/lib/branchOperationalCache";
import type {Branch} from "@/types/branch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function BranchHomePage() {
    const {branchId} = useParams<{branchId: string}>();
    const id = Number(branchId);
    const {branch: selected} = useSelectedBranch();
    const occasionEnquiries = useStorefrontFeatures()?.occasionEnquiries === true;
    const [branch, setBranch] = useState<Branch | null>(null);
    const [tab, setTab] = useState<"home" | "details">("home");
    const [error, setError] = useState("");
    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1) return;
        const controller = new AbortController();
        checkOperationalBranch(id).then(data => {if (!controller.signal.aborted) setBranch(data);})
            .catch(() => {if (!controller.signal.aborted) setError("This branch is unavailable right now.");});
        return () => controller.abort();
    }, [id]);
    return <AppShell editorial showSocialPopup={false}>
        <article className="branch-home mx-auto max-w-6xl px-4 py-8 text-[#173a37] sm:px-6">

            {!branch && !error && <p role="status" className="mt-10">Loading branch…</p>}
            {(error || !Number.isSafeInteger(id) || id < 1) && <p role="alert" className="mt-10">{error || "Branch not found."}</p>}
            {branch && <>
                <header className="branch-home-hero">
                    <div className="branch-home-heading"><MobilePageBack href="/branches" label="All branches" className="branch-home-back"/><div><p className="gokul-overline">Your Gokul branch</p><h1>{branch.name}</h1></div></div>
                    <p>{branch.description || "Fresh sweets, snacks and meals from your neighbourhood Gokul branch."}</p>
                    <div className="branch-home-actions">
                        {selected?.id === branch.id ? <Link href="/menu"><T text="Browse menu"/> →</Link>
                            : <BranchSelector cardBranch={branch} destination="menu" />}
                    </div>
                </header>
                <nav className="gokul-branch-tabs" aria-label="Branch pages">
                    <button className="branch-home-tab" type="button" aria-current={tab === "home" ? "page" : undefined} onClick={() => setTab("home")}><T text="Home" /></button>
                    <span className="branch-home-menu-tab">{selected?.id === branch.id ? <Link href="/menu"><T text="Menu" /></Link> : <BranchSelector cardBranch={branch} destination="menu" />}</span>
                    {occasionEnquiries && (selected?.id === branch.id ? <Link href="/occasions"><span className="desktop-celebration-label"><T text="Occasions & gifting" /></span><span className="mobile-celebration-label"><T text="Bulk order" /></span></Link> : <BranchSelector cardBranch={branch} destination="occasions" />)}
                    {occasionEnquiries && <Link className="branch-request-desktop" href="/occasions/requests">My requests & quotes</Link>}
                    <button type="button" aria-current={tab === "details" ? "page" : undefined} onClick={() => setTab("details")}><T text="Branch details" /></button>
                </nav>
                {tab === "details" ? <BranchDetails branch={branch} /> : <><BranchDiscovery branch={branch} selected={selected?.id === branch.id} /><section className="branch-home-welcome">
                    <h2>Made for your next visit.</h2><p>{branch.pickupAvailable ? "Browse what is available here, place an order and choose your pickup time at checkout." : "Explore this branch and contact us for your visit."}</p>
                    <button className="branch-welcome-desktop" type="button" onClick={() => setTab("details")}>See branch details →</button><div className="branch-welcome-mobile">{selected?.id === branch.id ? <Link href="/menu"><T text="Browse menu" /></Link> : <BranchSelector cardBranch={branch} destination="menu" />}</div>
                    {occasionEnquiries && selected?.id === branch.id && <Link className="branch-welcome-desktop ml-4 inline-flex min-h-11 items-center font-semibold underline" href="/occasions">Plan occasion food →</Link>}
                </section></>}
            </>}
        </article>
    </AppShell>;
}
