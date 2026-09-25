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


interface AppShellProps {

    children: ReactNode;
    showSocialPopup?: boolean;
}


export default function AppShell({
    children,
    showSocialPopup = true
}: AppShellProps) {

    return (
        <div
            className="
                app-container
                flex
                flex-col
                min-h-dvh
                w-full
                min-w-0
                max-w-full
                overflow-x-clip
                bg-[#fffaf3]
                text-[#241715]
            "
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
