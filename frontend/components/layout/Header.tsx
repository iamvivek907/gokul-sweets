"use client";
import CustomerIcon from "@/components/customer/CustomerIcon";
import {T} from "@/lib/language";
import LinkFeedback from "@/components/common/LinkFeedback";

import {LanguagePicker} from "@/lib/language";

import Link from "next/link";
import {usePathname} from "next/navigation";
import MobileMenu
    from "@/components/layout/MobileMenu";
import BranchSelector from "@/components/branch/BranchSelector";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useCart} from "@/hooks/useCart";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import CustomerNotificationBell from "@/components/customer/CustomerNotificationBell";
import CustomerAccountLink from "@/components/customer/CustomerAccountLink";
import ReferenceWordmark from "./ReferenceWordmark";


export default function Header() {
    const features = useStorefrontFeatures();
    const futuristic = features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true;
    const {branch} = useSelectedBranch();
    const cart = useCart();
    const pathname = usePathname();

    return (
        <header
            className="customer-site-header

                sticky
                top-0
                z-40
                w-full
                max-w-full
                overflow-x-clip
                border-b
                border-[#eadfd6]
                bg-[#fffaf3]/95
                backdrop-blur-xl
            "
        >

            <div
                className="
                    mx-auto
                    flex
                    w-full
                    max-w-[1180px]
                    min-w-0
                    items-center
                    justify-between
                    gap-3
                    px-4
                    py-3
                "
            >

                {futuristic ? <Link href="/" data-navigation-link className="future-brand" aria-label="Gokul Sweets · Home">
                    <ReferenceWordmark />
                    <span className="future-brand-mark" aria-hidden="true">G</span>
                    <span><T text="Gokul Sweets" /><small><T text="FRESH FOR YOUR MOMENTS" /></small></span>
                <LinkFeedback href="/" /></Link> : <Link href="/" data-navigation-link aria-label="Gokul Sweets · Home"
                    className="
                        min-w-0
                    "
                >

                    <p
                        className="
                            truncate
                            text-[11px]
                            font-medium
                            text-[#756763]
                            sm:text-xs
                        "
                    >
                        <T text="Fresh sweets. Happier moments." /></p>


                    <h1
                        className="
                            truncate
                            text-lg
                            font-extrabold
                            tracking-tight
                            text-[#7a1625]
                            sm:text-xl
                        "
                    >
                        <T text="Gokul Sweets" /></h1>

                <LinkFeedback href="/" /></Link>}


                {futuristic && pathname !== "/branches" && !pathname.startsWith("/checkout/") && <div className="future-branch-control">
                    {features?.cartSwitchPreview || cart.isEmpty
                        ? <BranchSelector locationControl />
                        : <Link href="/cart" className="gokul-location-control future-branch-review" aria-label="Review cart before changing branch">
                            <span className="gokul-location-pin" aria-hidden="true"><CustomerIcon kind="pin"/></span>
                            <span className="gokul-location-name"><small><T text="PICKUP BRANCH" /></small><strong>{branch?.name ?? "Choose branch"}</strong></span>
                            <span className="gokul-location-chevron" aria-hidden="true">⌄</span>
                        <LinkFeedback /></Link>}
                </div>}

                {futuristic && pathname !== "/branches" && !pathname.startsWith("/checkout/") && <p className="mobile-header-branch-name">{branch?.name ?? "Choose pickup branch"}</p>}

                <div className="customer-header-actions ml-auto flex shrink-0 flex-row-reverse items-center gap-3 sm:flex-row">
                    <MobileMenu />
                    <LanguagePicker />
                    <CustomerNotificationBell />
                            <CustomerAccountLink />
                </div>

            </div>


        </header>
    );
}
