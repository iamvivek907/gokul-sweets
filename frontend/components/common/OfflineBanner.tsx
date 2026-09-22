"use client";

import {
    useOnlineStatus
} from "@/hooks/useOnlineStatus";


export default function OfflineBanner() {

    const online =
        useOnlineStatus();


    if (online) {
        return null;
    }


    return (
        <div
            className="
                fixed
                left-1/2
                top-3
                z-200
                -translate-x-1/2
                whitespace-nowrap
                rounded-full
                bg-[#241715]
                px-4
                py-2
                text-xs
                font-semibold
                text-white
                shadow-lg
            "
        >
            You&apos;re offline
        </div>
    );
}