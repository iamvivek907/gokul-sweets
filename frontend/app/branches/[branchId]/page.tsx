"use client";

import {useEffect, useState} from "react";
import {useParams} from "next/navigation";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import BranchDetails from "@/components/branch/BranchDetails";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {getBranch} from "@/services/branchApi";
import type {Branch} from "@/types/branch";

export default function BranchHomePage() {
    const {branchId} = useParams<{branchId: string}>();
    const id = Number(branchId);
    const {branch: selected} = useSelectedBranch();
    const [branch, setBranch] = useState<Branch | null>(null);
    const [tab, setTab] = useState<"home" | "details">("home");
    const [error, setError] = useState("");
    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1) return;
        const controller = new AbortController();
        getBranch(id, controller.signal).then(data => {if (!controller.signal.aborted) setBranch(data);})
            .catch(() => {if (!controller.signal.aborted) setError("This branch is unavailable right now.");});
        return () => controller.abort();
    }, [id]);
    return <AppShell editorial showSocialPopup={false}>
        <article className="branch-home mx-auto max-w-6xl px-4 py-8 text-[#173a37] sm:px-6">
            <Link href="/branches" className="text-sm font-semibold underline">← All branches</Link>
            {!branch && !error && <p role="status" className="mt-10">Loading branch…</p>}
            {(error || !Number.isSafeInteger(id) || id < 1) && <p role="alert" className="mt-10">{error || "Branch not found."}</p>}
            {branch && <>
                <header className="branch-home-hero">
                    <p className="gokul-overline">Your Gokul branch</p><h1>{branch.name}</h1>
                    <p>{branch.description || "Fresh sweets, snacks and meals from your neighbourhood Gokul branch."}</p>
                    <div className="branch-home-actions">
                        {selected?.id === branch.id ? <Link href="/menu">Browse this branch’s menu →</Link>
                            : <BranchSelector cardBranch={branch} destination="menu" />}
                    </div>
                </header>
                <nav className="gokul-branch-tabs" aria-label="Branch pages">
                    <button type="button" aria-current={tab === "home" ? "page" : undefined} onClick={() => setTab("home")}>Home</button>
                    {selected?.id === branch.id ? <Link href="/menu">Menu</Link> : <BranchSelector cardBranch={branch} destination="menu" />}
                    <button type="button" aria-current={tab === "details" ? "page" : undefined} onClick={() => setTab("details")}>Branch details</button>
                </nav>
                {tab === "details" ? <BranchDetails branch={branch} /> : <section className="branch-home-welcome">
                    <h2>Made for your next visit.</h2><p>{branch.pickupAvailable ? "Browse what is available here, place an order and choose your pickup time at checkout." : "Explore this branch and contact us for your visit."}</p>
                    <button type="button" onClick={() => setTab("details")}>See branch details →</button>
                </section>}
            </>}
        </article>
    </AppShell>;
}
