"use client";
import "@/lib/paymentNavigation";

import {usePathname} from "next/navigation";
import type {
    ReactNode
} from "react";

import BranchOperationalGuard from "./BranchOperationalGuard";
import InstallAppBanner from "@/components/pwa/InstallAppBanner";
import ConnectionNotice from "@/components/common/ConnectionNotice";
import Header
    from "./Header";

import BottomNavigation
    from "./BottomNavigation";

import SocialFollowPopup
    from "./SocialFollowPopup";
import CustomerFooter
    from "./CustomerFooter";
import PickupJourneyContext from "./PickupJourneyContext";
import CustomerBreadcrumbs from "./CustomerBreadcrumbs";
import {useStorefrontFeatures} from "@/hooks/useStorefrontFeatures";
import CustomerAlertRuntime from "@/components/customer/CustomerAlertRuntime";
import "./futuristic-storefront.css";
import "./editorial-storefront.css";
import "./customer-journey.css";
import "./compact-mobile.css";
import "./mobile-account-refinement.css";
import "./reference-storefront.css";
import "./customer-polish.css";
import MobileEdgeBack from "./MobileEdgeBack";


interface AppShellProps {

    children: ReactNode;
    showSocialPopup?: boolean;
    showConnectionNotice?: boolean;
    editorial?: boolean;
}


export default function AppShell({
    children,
    showSocialPopup = true,
    showConnectionNotice = true,
    editorial = false
}: AppShellProps) {
    const pathname = usePathname();
    const features = useStorefrontFeatures();
    const futuristic = features?.futuristicStorefrontV2 === true || features?.checkoutExperienceV2 === true;

    return (
        <div
            data-customer-route={pathname}
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

            {futuristic&&<MobileEdgeBack />}
            <Header />
            <CustomerAlertRuntime />

            {showConnectionNotice && <ConnectionNotice />}
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
                {futuristic && <CustomerBreadcrumbs />}
                {futuristic && <InstallAppBanner compact />}
                <BranchOperationalGuard>{children}</BranchOperationalGuard>
            </main>

            <CustomerFooter />

            <BottomNavigation />


            {showSocialPopup && <SocialFollowPopup />}

        </div>
    );
}
