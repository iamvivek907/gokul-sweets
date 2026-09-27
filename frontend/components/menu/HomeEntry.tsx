"use client";

import type {ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {shouldShowIntentGateway} from "@/lib/entryIntent";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";

/** Keep the entrance as Home while the gateway is enabled; branch cards open ordering directly. */
export default function HomeEntry({children}: {children: ReactNode}) {
    const {features, error} = useStorefrontConfiguration();

    if (!features && !error) {
        return <AppShell showSocialPopup={false}><div role="status" className="px-5 py-16">Preparing your Gokul visit…</div></AppShell>;
    }
    if (!features || error || !shouldShowIntentGateway(features.preHomeIntentGateway, false)) return <>{children}</>;

    return <AppShell editorial showSocialPopup={false}>
        <EditorialArrival campaignsEnabled={features.homepageCampaigns}
            accessible={features.accessibleOrderingV2} />
    </AppShell>;
}
