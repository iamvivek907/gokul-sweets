"use client";

import {useState, type ReactNode} from "react";
import AppShell from "@/components/layout/AppShell";
import EditorialArrival from "@/components/menu/EditorialArrival";
import {shouldShowIntentGateway} from "@/lib/entryIntent";
import {useStorefrontConfiguration} from "@/hooks/useStorefrontFeatures";

/** Show the entrance on every app opening. A choice advances this visit without a stored-branch bypass. */
export default function HomeEntry({children}: {children: ReactNode}) {
    const {features, error} = useStorefrontConfiguration();
    const [entered, setEntered] = useState(false);

    if (!features && !error) {
        return <AppShell showSocialPopup={false}><div role="status" className="px-5 py-16">Preparing your Gokul visit…</div></AppShell>;
    }
    if (!features || error || !shouldShowIntentGateway(features.preHomeIntentGateway, entered)) return <>{children}</>;

    return <AppShell editorial showSocialPopup={false}>
        <EditorialArrival campaignsEnabled={features.homepageCampaigns}
            accessible={features.accessibleOrderingV2} onExplore={() => setEntered(true)} />
    </AppShell>;
}
