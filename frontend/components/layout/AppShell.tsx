"use client";

import type {
    ReactNode
} from "react";

import Header
    from "./Header";

import BottomNavigation
    from "./BottomNavigation";

import SocialFollowPopup
    from "./SocialFollowPopup";
import CustomerFooter
    from "./CustomerFooter";
import PickupJourneyContext from "./PickupJourneyContext";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import "./futuristic-storefront.css";
import "./editorial-storefront.css";


interface AppShellProps {

    children: ReactNode;
    showSocialPopup?: boolean;
    editorial?: boolean;
}


export default function AppShell({
    children,
    showSocialPopup = true,
    editorial = false
}: AppShellProps) {
    const features = useStorefrontFeatures();
    const futuristic = features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true;

    return (
        <div
            className={`
                app-container
                ${futuristic ? "future-storefront" : ""}
                ${futuristic && editorial ? "editorial-storefront" : ""}
                flex
                flex-col
                min-h-dvh
                w-full
                min-w-0
                max-w-full
                overflow-x-clip
                bg-[#fffaf3]
                text-[#241715]
            `}
        >

            <Header />

            <PickupJourneyContext />


            <main
                className="
                    page-content
                    flex-1
                    w-full
                    min-w-0
                    max-w-full
                    overflow-x-clip
                "
            >
                {children}
            </main>

            <CustomerFooter />

            <BottomNavigation />


            {showSocialPopup && <SocialFollowPopup />}

        </div>
    );
}
