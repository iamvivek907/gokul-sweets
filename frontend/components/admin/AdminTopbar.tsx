"use client";

import {
    useRouter
} from "next/navigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import AdminMobileNav
    from "@/components/admin/AdminMobileNav";


export default function AdminTopbar() {

    const router =
        useRouter();


    const {
        profile,
        logout
    } =
        useAdminAuth();


    function handleLogout() {

        logout();


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
                    gap-3
                    px-4

                    sm:px-6
                "
            >

                <div
                    className="
                        flex
                        min-w-0
                        items-center
                        gap-3
                    "
                >

                    <AdminMobileNav />


                    <div
                        className="
                            min-w-0
                        "
                    >

                        <p
                            className="
                                text-xs
                                font-semibold
                                uppercase
                                tracking-wide
                                text-[#c88a20]

                                lg:hidden
                            "
                        >
                            Gokul Sweets Admin
                        </p>


                        <p
                            className="
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


                <button
                    type="button"
                    onClick={
                        handleLogout
                    }
                    className="
                        min-h-10
                        shrink-0
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
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
                    Logout
                </button>

            </div>

        </header>
    );
}