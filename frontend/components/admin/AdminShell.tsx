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
import StaffReauthenticationDialog from "@/components/admin/StaffReauthenticationDialog";
import {LanguagePicker} from "@/lib/language";


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
        profile,
        verificationFailed,
        refresh,
        isAuthenticated
    } =
        useAdminAuth();


    const isLoginPage =
        pathname
        === "/admin/login";


    const isAccountAccessPage = pathname === "/admin/setup" || pathname === "/admin/recover";

    useEffect(() => {
        if (ready && !verificationFailed && isLoginPage && isAuthenticated) {
            router.replace("/admin");
        } else if (ready && !verificationFailed && !isLoginPage && !isAccountAccessPage && !isAuthenticated) {
            router.replace("/admin/login");
        }
    }, [ready, verificationFailed, isAuthenticated, isLoginPage, isAccountAccessPage, router]);

    /*
     * Wait until browser session state
     * has been checked.
     */
    if ((!ready || (verificationFailed && !isAuthenticated)) && !isAccountAccessPage && !isLoginPage) {

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
                    {verificationFailed ? "Could not verify your admin session. Check your connection." : "Loading admin portal..."}
                </p>
                {verificationFailed && (
                    <button type="button" onClick={() => void refresh()}
                        className="ml-3 min-h-11 rounded-lg border px-3 text-sm font-semibold">Retry</button>
                )}
            </div>
        );
    }


    /*
     * Login page must remain outside
     * the authenticated admin shell.
     */
    if (isLoginPage || isAccountAccessPage) {

        if (isAuthenticated && !isAccountAccessPage) {

            return null;
        }


        return (
            <>
                <div className="absolute right-4 top-4 z-20"><LanguagePicker /></div>
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
            key={profile?.staffId}
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
                    <StaffReauthenticationDialog />

                    {verificationFailed && (
                        <p role="status" className="bg-amber-50 px-4 py-2 text-sm text-amber-950">
                            The server could not verify your session. Your current page is still open.
                            <button type="button" onClick={() => void refresh()} className="ml-2 underline">Retry connection</button>
                        </p>
                    )}


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
