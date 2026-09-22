"use client";

import {
    useEffect,
    type ReactNode
} from "react";

import {
    usePathname,
    useRouter
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import AdminSidebar
    from "@/components/admin/AdminSidebar";

import AdminTopbar
    from "@/components/admin/AdminTopbar";


export default function AdminShell({
    children
}: {
    children: ReactNode;
}) {

    const router =
        useRouter();

    const pathname =
        usePathname();

    const {
        ready,
        isAuthenticated
    } =
        useAdminAuth();


    const isLoginPage =
        pathname
        === "/admin/login";


    /*
     * ---------------------------------------------------------
     * ADMIN ROUTE GUARD
     * ---------------------------------------------------------
     *
     * Not logged in:
     *
     * /admin
     * /admin/menu
     * /admin/orders
     * etc.
     *
     *        ↓
     *
     * /admin/login
     *
     *
     * Already logged in:
     *
     * /admin/login
     *
     *        ↓
     *
     * /admin
     */
    useEffect(
        () => {

            if (!ready) {

                return;
            }


            if (
                isLoginPage
                &&
                isAuthenticated
            ) {

                router.replace(
                    "/admin"
                );

                return;
            }


            if (
                !isLoginPage
                &&
                !isAuthenticated
            ) {

                router.replace(
                    "/admin/login"
                );
            }

        },
        [
            ready,
            isAuthenticated,
            isLoginPage,
            router
        ]
    );


    /*
     * Wait until browser session state
     * has been checked.
     */
    if (!ready) {

        return (
            <div
                className="
                    flex
                    min-h-screen
                    items-center
                    justify-center
                    bg-[#fffaf3]
                    px-4
                "
            >
                <p
                    className="
                        text-sm
                        font-semibold
                        text-[#756763]
                    "
                >
                    Loading admin portal...
                </p>
            </div>
        );
    }


    /*
     * Login page must remain outside
     * the authenticated admin shell.
     */
    if (isLoginPage) {

        if (isAuthenticated) {

            return null;
        }


        return (
            <>
                {children}
            </>
        );
    }


    /*
     * Prevent protected admin content from
     * flashing before redirecting to login.
     */
    if (!isAuthenticated) {

        return null;
    }


    /*
     * ---------------------------------------------------------
     * AUTHENTICATED ADMIN LAYOUT
     * ---------------------------------------------------------
     *
     * Desktop:
     *
     * Sidebar | Topbar
     *         | Page Content
     *
     * Mobile:
     *
     * Topbar
     * Page Content
     *
     * Mobile navigation will be added later.
     */
    return (
        <div
            className="
                min-h-screen
                bg-[#fffaf3]
                text-[#241715]
            "
        >

            <div
                className="
                    flex
                    min-h-screen
                "
            >

                <AdminSidebar />


                <div
                    className="
                        min-w-0
                        flex-1
                    "
                >

                    <AdminTopbar />


                    <div
                        className="
                            mx-auto
                            max-w-1500px
                        "
                    >

                        {children}

                    </div>

                </div>

            </div>

        </div>
    );
}