"use client";

import HomeQuickActions from "@/components/customer/HomeQuickActions";
import type {ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import StorefrontWelcome from "@/components/common/StorefrontWelcome";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {shouldShowIntentGateway} from "@/lib/entryIntent";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";

/** Keep the entrance as Home while the gateway is enabled; branch cards open ordering directly. */
export default function HomeEntry({children}: {children: ReactNode}) {
    const {features} = useStorefrontConfiguration();

    if (!features) return <AppShell showSocialPopup={false} showConnectionNotice={false}><StorefrontWelcome /></AppShell>;
    if (!shouldShowIntentGateway(features.preHomeIntentGateway, false)) return <>{children}</>;

    return <AppShell editorial showSocialPopup={false}>
        <HomeQuickActions/>
        <EditorialArrival campaignsEnabled={features.homepageCampaigns} branchExperience={features.branchExperience}
            occasionEnquiries={features.occasionEnquiries}
            accessible={features.accessibleOrderingV2} />
    </AppShell>;
}
