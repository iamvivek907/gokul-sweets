"use client";

import Link
    from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";


interface DashboardAction {

    title: string;

    description: string;

    href: string;

    permissions?: string[];

    ownerOnly?: boolean;
}


const dashboardActions:
    DashboardAction[] = [

    {
        title:
            "Live Orders",

        description:
            "View incoming paid orders and manage preparation and pickup status.",

        href:
            "/admin/orders",

        permissions: [
            "ORDER_VIEW",
            "ORDER_START_PREPARATION",
            "ORDER_MARK_READY",
            "ORDER_MARK_PICKED_UP",
            "ORDER_CANCEL"
        ]
    },

    {
        title:
            "Menu",

        description:
            "Manage menu availability, branch pricing and Excel menu imports.",

        href:
            "/admin/menu",

        permissions: [
            "MENU_MANAGE"
        ]
    },

    {
        title:
            "Branches",

        description:
            "Create and manage Gokul Sweets branches.",

        href:
            "/admin/branches",

        permissions: [
            "BRANCH_MANAGE"
        ]
    },

    {
        title:
            "Staff",

        description:
            "Add staff, assign roles and control branch access.",

        href:
            "/admin/staff",

        permissions: [
            "STAFF_MANAGE"
        ]
    },

    {
        title:
            "Reports",

        description:
            "Review sales, orders, payments and operational reports.",

        href:
            "/admin/reports",

        permissions: [
            "REPORT_VIEW"
        ]
    },

    {
        title:
            "Customers",

        description:
            "View customer details and order history.",

        href:
            "/admin/customers",

        ownerOnly:
            true
    },

    {
        title:
            "Offers & Notifications",

        description:
            "Create customer offers and notification campaigns.",

        href:
            "/admin/notifications",

        ownerOnly:
            true
    }
];


export default function AdminDashboardPage() {

    const {
        profile,
        hasAnyPermission
    } =
        useAdminAuth();


    function canSee(
        action: DashboardAction
    ) {

        if (!profile) {

            return false;
        }


        if (
            profile.roleName
            === "OWNER_ADMIN"
        ) {

            return true;
        }


        if (
            action.ownerOnly
        ) {

            return false;
        }


        if (
            !action.permissions
            ||
            action.permissions.length
                === 0
        ) {

            return true;
        }


        return hasAnyPermission(
            action.permissions
        );
    }


    const visibleActions =
        dashboardActions
            .filter(
                canSee
            );


    return (
        <div
            className="
                px-4
                py-6

                sm:px-6
                sm:py-8

                lg:px-8
            "
        >

            <div
                className="
                    mx-auto
                    max-w-7xl
                "
            >

                <section
                    className="
                        rounded-3xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-5
                        shadow-sm

                        sm:p-7
                    "
                >

                    <div
                        className="
                            flex
                            flex-col
                            gap-4

                            lg:flex-row
                            lg:items-end
                            lg:justify-between
                        "
                    >

                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-[0.16em]
                                    text-[#c88a20]
                                "
                            >
                                Gokul Sweets Admin
                            </p>


                            <h1
                                className="
                                    mt-2
                                    text-3xl
                                    font-bold
                                    tracking-tight
                                    text-[#241715]

                                    sm:text-4xl
                                "
                            >
                                Dashboard
                            </h1>


                            <p
                                className="
                                    mt-3
                                    max-w-2xl
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                Welcome, {profile?.fullName}. Manage daily operations and access the tools available to your role.
                            </p>

                        </div>


                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                px-4
                                py-3
                            "
                        >

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#756763]
                                "
                            >
                                Signed in as
                            </p>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    font-bold
                                    text-[#7a1625]
                                "
                            >
                                {
                                    profile?.roleName
                                }
                            </p>

                        </div>

                    </div>

                </section>


                <section
                    className="
                        mt-6
                    "
                >

                    <div
                        className="
                            flex
                            items-end
                            justify-between
                            gap-4
                        "
                    >

                        <div>

                            <h2
                                className="
                                    text-xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                Quick access
                            </h2>


                            <p
                                className="
                                    mt-1
                                    text-sm
                                    text-[#756763]
                                "
                            >
                                Open the areas available to your role.
                            </p>

                        </div>

                    </div>


                    <div
                        className="
                            mt-4
                            grid
                            gap-4

                            sm:grid-cols-2
                            xl:grid-cols-3
                        "
                    >

                        {
                            visibleActions
                                .map(
                                    action => (

                                        <Link
                                            key={
                                                action.href
                                            }
                                            href={
                                                action.href
                                            }
                                            className="
                                                group
                                                flex
                                                min-h-40
                                                flex-col
                                                justify-between
                                                rounded-2xl
                                                border
                                                border-[#eadfd6]
                                                bg-white
                                                p-5
                                                transition

                                                hover:-translate-y-0.5
                                                hover:border-[#d9c5b8]
                                                hover:shadow-md

                                                focus-visible:outline-none
                                                focus-visible:ring-4
                                                focus-visible:ring-[#c88a20]/20
                                            "
                                        >

                                            <div>

                                                <h3
                                                    className="
                                                        text-lg
                                                        font-bold
                                                        text-[#241715]
                                                    "
                                                >
                                                    {
                                                        action.title
                                                    }
                                                </h3>


                                                <p
                                                    className="
                                                        mt-2
                                                        text-sm
                                                        leading-6
                                                        text-[#756763]
                                                    "
                                                >
                                                    {
                                                        action.description
                                                    }
                                                </p>

                                            </div>


                                            <div
                                                className="
                                                    mt-5
                                                    flex
                                                    items-center
                                                    justify-between
                                                "
                                            >

                                                <span
                                                    className="
                                                        text-sm
                                                        font-semibold
                                                        text-[#7a1625]
                                                    "
                                                >
                                                    Open
                                                </span>


                                                <span
                                                    aria-hidden="true"
                                                    className="
                                                        text-xl
                                                        text-[#c88a20]
                                                        transition

                                                        group-hover:translate-x-1
                                                    "
                                                >
                                                    →
                                                </span>

                                            </div>

                                        </Link>

                                    )
                                )
                        }

                    </div>

                </section>


                <section
                    className="
                        mt-6
                    "
                >

                    <div
                        className="
                            mb-4
                        "
                    >

                        <h2
                            className="
                                text-xl
                                font-bold
                                text-[#241715]
                            "
                        >
                            Today at a glance
                        </h2>


                        <p
                            className="
                                mt-1
                                text-sm
                                text-[#756763]
                            "
                        >
                            Live operational numbers will appear here once the dashboard APIs are connected.
                        </p>

                    </div>


                    <div
                        className="
                            grid
                            gap-4

                            sm:grid-cols-2
                            xl:grid-cols-4
                        "
                    >

                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-medium
                                    text-[#756763]
                                "
                            >
                                Orders today
                            </p>


                            <p
                                className="
                                    mt-3
                                    text-2xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                —
                            </p>

                        </div>


                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-medium
                                    text-[#756763]
                                "
                            >
                                Preparing
                            </p>


                            <p
                                className="
                                    mt-3
                                    text-2xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                —
                            </p>

                        </div>


                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-medium
                                    text-[#756763]
                                "
                            >
                                Ready for pickup
                            </p>


                            <p
                                className="
                                    mt-3
                                    text-2xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                —
                            </p>

                        </div>


                        <div
                            className="
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                            "
                        >

                            <p
                                className="
                                    text-sm
                                    font-medium
                                    text-[#756763]
                                "
                            >
                                Sales today
                            </p>


                            <p
                                className="
                                    mt-3
                                    text-2xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                —
                            </p>

                        </div>

                    </div>

                </section>

            </div>

        </div>
    );
}