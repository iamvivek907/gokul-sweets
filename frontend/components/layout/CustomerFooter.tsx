"use client";

import Link from "next/link";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function CustomerFooter() {
    const features = useStorefrontFeatures();
    if (!features?.futuristicStorefrontV2 && !features?.checkoutExperienceV2) return <LegacyFooter />;
    return <footer aria-label="Customer footer" className="customer-site-footer border-t border-[#d8dedb] bg-[#f5f8f6] px-5 pb-[calc(90px+env(safe-area-inset-bottom))] pt-12">
        <div className="mx-auto grid w-full max-w-[1280px] gap-10 md:grid-cols-[1.6fr_1fr_1fr]">
            <div><p className="font-serif text-3xl font-bold text-[#173a37]">Gokul Sweets</p>
                <p className="mt-4 max-w-sm text-base leading-7 text-[#435d58]">Fresh sweets, snacks and meals for the moments you share. Choose a branch and a pickup time that works for you.</p></div>
            <nav aria-label="Explore Gokul"><h2 className="mb-4 text-sm font-bold uppercase tracking-widest text-[#173a37]">Explore</h2>
                <div className="flex flex-col gap-3 text-sm"><Link href="/menu">Menu</Link><Link href="/about#our-branches">Our branches</Link><Link href="/about">Our story</Link></div></nav>
            <nav aria-label="Customer help"><h2 className="mb-4 text-sm font-bold uppercase tracking-widest text-[#173a37]">Your visit</h2>
                <div className="flex flex-col gap-3 text-sm"><Link href="/orders">Your orders</Link><Link href="/profile">Your account</Link><Link href="/about">Contact a branch</Link></div></nav>
        </div>
        <div className="mx-auto mt-12 flex w-full max-w-[1280px] flex-wrap justify-between gap-3 border-t border-[#cbd6d1] pt-5 text-xs text-[#435d58]">
            <span>© {new Date().getFullYear()} Gokul Sweets & Restaurants</span><span>Pickup availability and prices are confirmed before payment.</span>
        </div>
    </footer>;
}

function LegacyFooter() {
    return (
        <footer
            aria-label="Customer footer"
            className="customer-site-footer
                border-t
                border-[#eadfd6]
                bg-white
                px-4
                pb-[calc(90px+env(safe-area-inset-bottom))]
                pt-6
            "
        >
            <div
                className="
                    mx-auto
                    flex
                    w-full
                    max-w-[1180px]
                    flex-col
                    gap-2
                    text-center
                "
            >
                <p className="text-sm font-semibold text-[#241715]">
                    Gokul Sweets
                </p>
                <p className="text-xs leading-5 text-[#756763]">
                    Fresh sweets for every celebration. We’ll confirm your pickup time and payment
                    before preparing your order.
                </p>
            </div>
        </footer>
    );
}
