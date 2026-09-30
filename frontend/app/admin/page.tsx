"use client";

import OperationsToday from "@/components/admin/OperationsToday";
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


    return <main className="mx-auto max-w-7xl space-y-6 px-4 py-6 text-[#173a37] sm:px-6">
        <header><p className="text-xs font-bold uppercase tracking-widest text-[#526762]">Gokul operations</p><h1 className="mt-2 text-3xl font-bold">Welcome, {profile?.fullName || profile?.username || "team"}</h1><p className="mt-2 text-sm text-[#526762]">See today’s work, then open the tools you need.</p></header>
        <OperationsToday />
        <section aria-label="Quick access"><h2 className="mb-4 text-xl font-bold">Quick access</h2><div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{visibleActions.map(action=><Link key={action.href} href={action.href} className="rounded-2xl border border-[#d9e5df] bg-white p-5 transition hover:border-[#143936] hover:shadow-sm"><h3 className="font-bold">{action.title} →</h3><p className="mt-2 text-sm text-[#526762]">{action.description}</p></Link>)}</div></section>
    </main>;
}
