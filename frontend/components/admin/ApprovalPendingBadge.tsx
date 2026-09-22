"use client";

import {
    useEffect,
    useState
} from "react";

import {
    usePathname
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminApprovalCounts
} from "@/services/adminApprovalsApi";


export default function ApprovalPendingBadge() {

    const pathname =
        usePathname();


    const {
        authorization
    } =
        useAdminAuth();


    const [
        count,
        setCount
    ] =
        useState(
            0
        );


    useEffect(
        () => {

            if (
                authorization === null
            ) {

                return;
            }


            let cancelled =
                false;


            const load =
                async () => {

                    try {

                        const result =
                            await getAdminApprovalCounts(
                                authorization,
                                {}
                            );


                        if (!cancelled) {

                            setCount(
                                result.pending
                            );
                        }

                    } catch {

                        /*
                         * Navigation should still work if the badge
                         * cannot be refreshed.
                         */
                    }
                };


            void load();


            const onApprovalsPage =
                pathname === "/admin/approvals"
                ||
                pathname.startsWith(
                    "/admin/approvals/"
                );


            const intervalId =
                window.setInterval(
                    () => {

                        void load();
                    },
                    onApprovalsPage
                        ? 2000
                        : 30000
                );


            const handleFocus =
                () => {

                    void load();
                };


            const handleVisibilityChange =
                () => {

                    if (
                        document.visibilityState
                        === "visible"
                    ) {

                        void load();
                    }
                };


            window.addEventListener(
                "focus",
                handleFocus
            );


            document.addEventListener(
                "visibilitychange",
                handleVisibilityChange
            );


            return () => {

                cancelled =
                    true;


                window.clearInterval(
                    intervalId
                );


                window.removeEventListener(
                    "focus",
                    handleFocus
                );


                document.removeEventListener(
                    "visibilitychange",
                    handleVisibilityChange
                );
            };

        },
        [
            authorization,
            pathname
        ]
    );


    if (
        count <= 0
    ) {

        return null;
    }


    return (
        <span
            className="
                ml-auto
                inline-flex
                min-w-6
                items-center
                justify-center
                rounded-full
                bg-[#7a1625]
                px-2
                py-0.5
                text-[11px]
                font-bold
                leading-5
                text-white
            "
            aria-label={`${count} pending approval request${count === 1 ? "" : "s"}`}
        >
            {
                count > 99
                    ? "99+"
                    : count
            }
        </span>
    );
}
