"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";

import ApprovalPendingBadge from "@/components/admin/ApprovalPendingBadge";
import {useAdminAuth} from "@/contexts/AdminAuthContext";

interface NavItem {
    href: string;
    label: string;
    permissions?: string[];
    ownerOnly?: boolean;
    exact?: boolean;
}

const navItems: NavItem[] = [
    {href: "/admin/homepage-campaigns", label: "Homepage Campaigns", permissions: ["MENU_MANAGE"]},
    {href: "/admin/tax-categories", label: "Tax Categories", permissions: ["MENU_MANAGE"]},
    {href: "/admin/pickup-scheduling", label: "Pickup Scheduling", permissions: ["BRANCH_MANAGE"]},
    {href: "/admin", label: "Dashboard", exact: true},
    {href: "/admin/me", label: "My Details"},
    {
        href: "/admin/orders",
        label: "Live Orders",
        permissions: [
            "ORDER_VIEW",
            "ORDER_START_PREPARATION",
            "ORDER_MARK_READY",
            "ORDER_MARK_PICKED_UP",
            "ORDER_CANCEL"
        ]
    },
    {
        href: "/admin/printing",
        label: "Printer Queue",
        permissions: ["ORDER_VIEW"]
    },
    {
        href: "/admin/menu",
        label: "Menu",
        permissions: ["MENU_MANAGE"]
    },
    {
        href: "/admin/inventory",
        label: "Inventory",
        permissions: ["INVENTORY_VIEW", "INVENTORY_MANAGE"]
    },
    {
        href: "/admin/branches",
        label: "Branches",
        permissions: ["BRANCH_MANAGE"]
    },
    {
        href: "/admin/customers",
        label: "Customers",
        permissions: ["CUSTOMER_VIEW"]
    },
    {
        href: "/admin/notifications",
        label: "Offers & Notifications",
        ownerOnly: true
    },
    {
        href: "/admin/staff",
        label: "Staff",
        permissions: ["STAFF_MANAGE"]
    },
    {
        href: "/admin/approvals",
        label: "Approvals",
        permissions: ["APPROVAL_VIEW"]
    },
    {
        href: "/admin/payroll",
        label: "Payroll",
        permissions: ["PAYROLL_VIEW"]
    },
    {
        href: "/admin/reports",
        label: "Reports",
        permissions: ["REPORT_VIEW"]
    }
];

export default function AdminSidebar() {
    const pathname = usePathname();
    const {profile, hasAnyPermission} = useAdminAuth();

    function canSee(item: NavItem) {
        if (!profile) return false;
        if (profile.roleName === "OWNER_ADMIN") return true;
        if (item.ownerOnly) return false;
        if (!item.permissions || item.permissions.length === 0) return true;
        return hasAnyPermission(item.permissions);
    }

    function isActive(item: NavItem): boolean {
        if (item.exact) return pathname === item.href;
        return pathname === item.href || pathname.startsWith(`${item.href}/`);
    }

    return (
        <aside className="hidden min-h-screen w-64 shrink-0 border-r border-[#eadfd6] bg-white lg:block">
            <div className="sticky top-0 flex min-h-screen flex-col px-4 py-6">
                <div className="px-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.18em] text-[#c88a20]">
                        Gokul Sweets
                    </p>
                    <h1 className="mt-1 text-xl font-bold text-[#7a1625]">
                        Admin Portal
                    </h1>
                </div>

                <nav className="mt-8 space-y-1">
                    {navItems.filter(canSee).map(item => {
                        const active = isActive(item);
                        return (
                            <Link
                                key={item.href}
                                href={item.href}
                                className={[
                                    "flex min-h-11 items-center rounded-xl px-3 text-sm font-semibold transition",
                                    active
                                        ? "bg-[#fff1e9] text-[#7a1625]"
                                        : "text-[#756763] hover:bg-[#fffaf3] hover:text-[#241715]"
                                ].join(" ")}
                            >
                                <span>{item.label}</span>
                                {item.href === "/admin/approvals" && (
                                    <ApprovalPendingBadge />
                                )}
                            </Link>
                        );
                    })}
                </nav>

                <div className="mt-auto rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4">
                    <p className="truncate text-sm font-bold text-[#241715]">
                        {profile?.fullName}
                    </p>
                    <p className="mt-1 text-xs font-medium text-[#756763]">
                        {profile?.roleName}
                    </p>
                </div>
            </div>
        </aside>
    );
}
