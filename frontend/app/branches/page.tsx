"use client";

import {useEffect} from "react";
import AppShell from "@/components/layout/AppShell";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function BranchesPage() {
    const features = useStorefrontFeatures();
    useEffect(() => {
        // Reveal the selected branch shortcuts before the branch gallery.
        document.getElementById("gokul-discovery")?.scrollIntoView({behavior: "instant"});
    }, []);
    return <AppShell editorial showSocialPopup={false}>
        <EditorialArrival campaignsEnabled={features?.homepageCampaigns === true}
            branchExperience={features?.branchExperience === true}
            occasionEnquiries={features?.occasionEnquiries === true}
            accessible={features?.accessibleOrderingV2 === true} />
    </AppShell>;
}
