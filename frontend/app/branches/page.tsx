"use client";

import AppShell from "@/components/layout/AppShell";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";

export default function BranchesPage() {
    const features = useStorefrontFeatures();
    return <AppShell editorial showSocialPopup={false}>
        <EditorialArrival branchesOnly campaignsEnabled={features?.homepageCampaigns === true}
            accessible={features?.accessibleOrderingV2 === true} />
    </AppShell>;
}
