"use client";
import "@/lib/paymentNavigation";

import {usePathname} from "next/navigation";
import {useState, type ReactNode} from "react";

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
import "./profile-history-polish.css";
import "./menu-premium.css";
import "./customer-design.css";
import "./menu-pickup.css";
import MobileEdgeBack from "./MobileEdgeBack";
import OrderingTour from "@/components/customer/OrderingTour";
import {OPEN_ORDERING_TOUR} from "@/lib/orderingTour";
import "./ordering-tour.css";


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
    const tourAllowed = futuristic && !pathname.startsWith("/checkout") && !/^\/orders\/[^/]+/.test(pathname);
    const [orderingGuideActive, setOrderingGuideActive] = useState(false);

    return (
        <div
            data-customer-route={pathname}
            data-customer-design={futuristic ? "reference" : undefined}
            data-premium-menu={pathname === "/menu" && futuristic && features?.contextualStorefrontV2 === true ? "true" : undefined}
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

            <BranchOperationalGuard>
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
                {tourAllowed && <OrderingTour key={pathname} pathname={pathname} onActivityChange={setOrderingGuideActive} />}
                {children}
            </main>

            <CustomerFooter onHowToOrder={tourAllowed ? () => window.dispatchEvent(new Event(OPEN_ORDERING_TOUR)) : undefined} />

            {showSocialPopup && !orderingGuideActive && <SocialFollowPopup />}
            </BranchOperationalGuard>

            <BottomNavigation />


        </div>
    );
}
