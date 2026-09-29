"use client";

import {useEffect} from "react";
import AppShell from "@/components/layout/AppShell";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function BranchesPage() {
    const features = useStorefrontFeatures();
    useEffect(() => {
        // Enter at the branch cards while keeping the welcome hero directly above them.
        document.getElementById("gokul-branches")?.scrollIntoView({behavior: "instant"});
    }, []);
    return <AppShell editorial showSocialPopup={false}>
        <EditorialArrival campaignsEnabled={features?.homepageCampaigns === true}
            branchExperience={features?.branchExperience === true}
            accessible={features?.accessibleOrderingV2 === true} />
    </AppShell>;
}
