"use client";

import Link from "next/link";
import {useEffect, useState} from "react";
import {T} from "@/lib/language";
import {useSelectedBranch} from "@/hooks/useSelectedBranch";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import {usePhoneViewport} from "@/hooks/usePhoneViewport";
import {adminFetch} from "@/services/adminApi";

/** Mobile profile and menu share one route; private sections remain owned by the account hub. */
export default function MobileAccountNavigation() {
    const features = useStorefrontFeatures();
    const phone = usePhoneViewport();
    const themed = features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true;
    const [staffAccess, setStaffAccess] = useState(false);
    useEffect(() => {
        if (!phone || !themed) return;
        const controller = new AbortController();
        void adminFetch("/api/admin/auth/me", "staff-session", {signal: controller.signal})
            .then(response => {if (!controller.signal.aborted) setStaffAccess(response.ok);})
            .catch(() => {if (!controller.signal.aborted) setStaffAccess(false);});
        return () => controller.abort();
    }, [phone, themed]);
    return <div className="mobile-account-navigation">
        <nav className="mobile-account-links" aria-label="Explore Gokul">
            <Link href="/menu"><T text="Menu" /><small><T text="Browse sweets, snacks and food" /></small></Link>
            <Link href="/cart"><T text="Cart" /><small><T text="View your cart" /></small></Link>
            {features?.occasionEnquiries && <Link href="/occasions"><T text="Celebrations & Gifts" /><small><T text="Plan bulk sweets and celebration gift boxes" /></small></Link>}
            <Link href="/about"><T text="About Gokul Sweets" /></Link>
            <Link href="/profile/privacy"><T text="Privacy and data" /></Link>
            <a href="https://www.instagram.com/_gokulsweets" target="_blank" rel="noopener noreferrer">Instagram</a>
            <a href="https://www.facebook.com/visitgokulsweets" target="_blank" rel="noopener noreferrer">Facebook</a>
            {staffAccess && <Link href="/admin"><T text="Staff dashboard" /></Link>}
        </nav>
    </div>;
}

export function MobileAccountBack() {
    const {branch} = useSelectedBranch();
    const features = useStorefrontFeatures();
    const home = features?.branchExperience ? branch ? `/branches/${branch.id}` : "/branches" : "/";
    const homeLabel = features?.branchExperience ? branch ? "Branch home" : "All branches" : "Home";
    return <nav className="mobile-account-back" aria-label="Account navigation">
        <Link href="/menu"><span aria-hidden="true">←</span> <T text="Back to menu" /></Link>
        <Link href={home}><T text={homeLabel} /></Link>
    </nav>;
}
