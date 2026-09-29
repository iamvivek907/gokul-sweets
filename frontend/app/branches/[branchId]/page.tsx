"use client";

import {useEffect, useState} from "react";
import {useParams} from "next/navigation";
import Link from "next/link";
import AppShell from "@/components/layout/AppShell";
import BranchSelector from "@/components/branch/BranchSelector";
import {getBranch} from "@/services/branchApi";
import type {Branch} from "@/types/branch";

export default function BranchDetailPage() {
    const {branchId} = useParams<{branchId: string}>();
    const id = Number(branchId);
    const [branch, setBranch] = useState<Branch | null>(null);
    const [error, setError] = useState("");
    const [failedImage, setFailedImage] = useState(false);
    useEffect(() => {
        if (!Number.isSafeInteger(id) || id < 1) return;
        const controller = new AbortController();
        getBranch(id, controller.signal).then(data => {if (!controller.signal.aborted) setBranch(data);})
            .catch(() => {if (!controller.signal.aborted) setError("This branch is unavailable right now.");});
        return () => controller.abort();
    }, [id]);
    return <AppShell editorial showSocialPopup={false}>
        <article className="mx-auto max-w-6xl px-4 py-8 text-[#173a37] sm:px-6 sm:py-12">
            <Link href="/branches" className="text-sm font-semibold underline">← All branches</Link>
            {!branch && !error && <p role="status" className="mt-10">Loading branch details…</p>}
            {(error || !Number.isSafeInteger(id) || id < 1) && <p role="alert" className="mt-10">{error || "Branch not found."}</p>}
            {branch && <>
                <div className="mt-6 overflow-hidden rounded-3xl bg-[#143936] text-white">
                    {!failedImage && branch.coverImageUrl && <picture>
                        {branch.mobileCoverImageUrl && <source media="(max-width: 700px)" srcSet={branch.mobileCoverImageUrl} />}
                        <img src={branch.coverImageUrl} alt={branch.coverAltText || branch.name}
                            onError={() => setFailedImage(true)} className="max-h-[32rem] w-full object-cover" />
                    </picture>}
                    <div className="p-6 sm:p-10"><p className="text-xs font-bold uppercase tracking-widest text-[#f8bea7]">Gokul branch</p>
                        <h1 className="mt-2 font-serif text-4xl sm:text-6xl">{branch.name}</h1>
                        <p className="mt-4 max-w-2xl leading-7">{branch.description || "Fresh sweets, snacks and meals from your neighbourhood Gokul branch."}</p></div>
                </div>
                <div className="mt-8 grid gap-6 md:grid-cols-2">
                    <section className="rounded-2xl border border-[#dbe6df] bg-white p-6"><h2 className="text-2xl font-semibold">Visit this branch</h2>
                        <p className="mt-3">{[branch.address, branch.city, branch.state, branch.pincode].filter(Boolean).join(", ") || "Address being updated"}</p>
                        {branch.openingTime && branch.closingTime && <p className="mt-2">Opening hours: {branch.openingTime.slice(0, 5)}–{branch.closingTime.slice(0, 5)} IST</p>}
                        {branch.phone && <a className="mt-3 inline-block font-semibold underline" href={`tel:${branch.phone}`}>Call branch</a>}</section>
                    <section className="rounded-2xl border border-[#dbe6df] bg-white p-6"><h2 className="text-2xl font-semibold">What you can do here</h2>
                        {branch.pickupAvailable ? <><p className="mt-3">Order food for pickup. You will choose an available date and time during checkout.</p>
                            <div className="branch-detail-action mt-5"><BranchSelector cardBranch={branch} /></div></>
                            : <p className="mt-3">Online pickup is currently unavailable at this branch.</p>}
                        <p className="mt-5 text-sm text-[#536b66]">Table, occasion and banquet bookings are not available online at this branch yet.</p>
                    </section>
                </div>
            </>}
        </article>
    </AppShell>;
}
