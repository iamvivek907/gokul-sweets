"use client";

import {LanguagePicker, T, useTranslation} from "@/lib/language";

import {
    useRouter
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import StaffNotificationBell from "@/components/admin/StaffNotificationBell";
import AdminMobileNav
    from "@/components/admin/AdminMobileNav";


export default function AdminTopbar() {
    const translate = useTranslation();

    const router =
        useRouter();


    const {
        profile,
        logout
    } =
        useAdminAuth();


    async function handleLogout() {

        await logout();


        router.replace(
            "/admin/login"
        );
    }


    return (
        <header
            className="
                sticky
                top-0
                z-40
                border-b
                border-[#eadfd6]
                bg-[#fffaf3]/95
                backdrop-blur
            "
        >

            <div
                className="
                    flex
                    min-h-16
                    items-center
                    justify-between
                    gap-2
                    px-3

                    sm:px-6
                "
            >

                <div
                    className="
                        flex
                        min-w-0
                        items-center
                        gap-2
                    "
                >

                    <AdminMobileNav />


                    <div
                        className="
                            min-w-0
                        "
                    >

                        <p className="text-sm font-bold sm:hidden">Gokul</p>
                        <p
                            className="
                                text-xs
                                font-semibold
                                uppercase
                                tracking-wide
                                text-[#c88a20]

                                hidden sm:block lg:hidden
                            "
                        >
                            Gokul Sweets Admin
                        </p>


                        <p
                            className="
                                hidden sm:block
                                truncate
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            {
                                profile?.fullName
                            }
                        </p>


                        <p
                            className="
                                hidden sm:block
                                mt-0.5
                                truncate
                                text-xs
                                text-[#756763]
                            "
                        >
                            {
                                profile?.roleName
                            }
                        </p>

                    </div>

                </div>


                <div className="flex shrink-0 items-center gap-1 sm:gap-3"><LanguagePicker /><StaffNotificationBell />
                <button
                    type="button"
                    aria-label={translate("Logout")}
                    onClick={
                        handleLogout
                    }
                    className="
                        flex items-center justify-center gap-2
                        min-h-11 min-w-11
                        shrink-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-2 sm:px-4
                        text-sm
                        font-semibold
                        text-[#7a1625]
                        transition

                        hover:bg-[#fff1e9]

                        focus-visible:outline-none
                        focus-visible:ring-4
                        focus-visible:ring-[#c88a20]/30
                    "
                >
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" aria-hidden="true"><path d="M9 4H5v16h4M13 8l4 4-4 4M9 12h11" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"/></svg><span className="hidden sm:inline"><T text="Logout" /></span>
                </button>
                </div>

            </div>


        </header>
    );
}