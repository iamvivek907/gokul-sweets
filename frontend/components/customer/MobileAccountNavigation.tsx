"use client";

import SocialFollowLinks from "@/components/customer/SocialFollowLinks";
import Link from "next/link";
import MobilePageBack from "./MobilePageBack";
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
            <Link href="/about"><T text="About Gokul Sweets" /></Link>
            <Link href="/profile/privacy"><T text="Privacy and data" /></Link>

            {staffAccess && <Link href="/admin"><T text="Staff dashboard" /></Link>}
        </nav>
        <SocialFollowLinks/>
    </div>;
}

export function MobileAccountBack() {
    const {branch} = useSelectedBranch();
    const features = useStorefrontFeatures();
    const home = features?.branchExperience ? branch ? `/branches/${branch.id}` : "/branches" : "/";
    const homeLabel = features?.branchExperience ? branch ? "Branch home" : "All branches" : "Home";
    return <nav className="mobile-account-back" aria-label="Account navigation">
        <MobilePageBack href="/menu" label="Back to menu"/>
        <Link href={home}><T text={homeLabel} /></Link>
    </nav>;
}
