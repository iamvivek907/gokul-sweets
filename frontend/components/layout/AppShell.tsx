import type {
    ReactNode
} from "react";

import Header
    from "./Header";

import BottomNavigation
    from "./BottomNavigation";

import SocialFollowPopup
    from "./SocialFollowPopup";


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


            <main
                className="
                    page-content
                    w-full
                    min-w-0
                    max-w-full
                    overflow-x-clip
                "
            >
                {children}
            </main>


            <BottomNavigation />


            {showSocialPopup && <SocialFollowPopup />}

        </div>
    );
}
