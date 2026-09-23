"use client";

import Link from "next/link";
import {usePathname} from "next/navigation";
import {useState} from "react";
import {useAdminAuth} from "@/contexts/AdminAuthContext";
import ApprovalPendingBadge from "@/components/admin/ApprovalPendingBadge";

interface Item {href: string; label: string; permissions?: string[]; ownerOnly?: boolean; exact?: boolean}
export const adminGroups: {label: string; items: Item[]}[] = [
    {label: "Orders & customers", items: [
        {href: "/admin/orders", label: "Live orders", permissions: ["ORDER_VIEW", "ORDER_START_PREPARATION", "ORDER_MARK_READY", "ORDER_MARK_PICKED_UP", "ORDER_CANCEL"]},
        {href: "/admin/printing", label: "Printer queue", permissions: ["ORDER_VIEW"]},
        {href: "/admin/customers", label: "Customers", permissions: ["CUSTOMER_VIEW"]}
    ]},
    {label: "Menu", items: [
        {href: "/admin/menu", label: "Menu management", permissions: ["MENU_MANAGE"], exact: true},
        {href: "/admin/menu/live", label: "Products & categories", permissions: ["MENU_MANAGE"]},
        {href: "/admin/menu/images", label: "Product images", permissions: ["MENU_MANAGE"]},
        {href: "/admin/menu/import", label: "Import menu", permissions: ["MENU_MANAGE"]},
        {href: "/admin/tax-categories", label: "Tax categories", permissions: ["MENU_MANAGE"]}
    ]},
    {label: "Storefront", items: [
        {href: "/admin/homepage-campaigns", label: "Homepage campaigns", permissions: ["MENU_MANAGE"]},
        {href: "/admin/notifications", label: "Offers", permissions: ["REBATE_VIEW", "REBATE_MANAGE"]}
    ]},
    {label: "Branch operations", items: [
        {href: "/admin/branches", label: "Branches & settings", permissions: ["BRANCH_MANAGE"]},
        {href: "/admin/pickup-scheduling", label: "Pickup scheduling", permissions: ["BRANCH_MANAGE"]}
    ]},
    {label: "Inventory", items: [
        {href: "/admin/inventory", label: "Daily availability", permissions: ["INVENTORY_VIEW", "INVENTORY_MANAGE"], exact: true},
        {href: "/admin/inventory/setup", label: "Product policies", permissions: ["INVENTORY_VIEW", "INVENTORY_MANAGE"]},
        {href: "/admin/inventory/automation", label: "Future production", permissions: ["INVENTORY_VIEW", "INVENTORY_MANAGE"]},
        {href: "/admin/inventory/production", label: "Production & ready stock", permissions: ["INVENTORY_VIEW", "INVENTORY_MANAGE"]}
    ]},
    {label: "Team", items: [
        {href: "/admin/staff", label: "Staff", permissions: ["STAFF_MANAGE"]},
        {href: "/admin/approvals", label: "Approvals", permissions: ["APPROVAL_VIEW"]},
        {href: "/admin/payroll", label: "Payroll", permissions: ["PAYROLL_VIEW"]}
    ]}
];

export function matchesAdminRoute(pathname: string, item: Item) {
    return pathname === item.href || (!item.exact && pathname.startsWith(`${item.href}/`));
}

export default function AdminNavigation({onNavigate}: {onNavigate?: () => void}) {
    const pathname = usePathname();
    const {profile, hasAnyPermission} = useAdminAuth();
    const [expanded, setExpanded] = useState<Record<string, boolean>>({});
    const canSee = (item: Item) => !!profile && (profile.roleName === "OWNER_ADMIN"
        || (!item.ownerOnly && (!item.permissions || hasAnyPermission(item.permissions))));
    const link = (item: Item) => <Link key={item.href} href={item.href} onClick={onNavigate}
        aria-current={matchesAdminRoute(pathname, item) ? "page" : undefined}
        className={`flex min-h-11 items-center rounded-xl px-3 text-sm ${matchesAdminRoute(pathname, item) ? "bg-[#fff1e9] font-bold text-[#7a1625]" : "text-[#756763] hover:bg-[#fffaf3]"}`}>
        {item.label}{item.href === "/admin/approvals" && <ApprovalPendingBadge />}
    </Link>;
    return <nav aria-label="Admin tools" className="space-y-1">
        {link({href: "/admin", label: "Dashboard", exact: true})}
        {adminGroups.map(group => {
            const items = group.items.filter(canSee);
            if (!items.length) return null;
            const active = items.some(item => matchesAdminRoute(pathname, item));
            const open = expanded[`${pathname}:${group.label}`] ?? active;
            const id = `admin-group-${group.label.replaceAll(" ", "-").replaceAll("&", "and")}`;
            return <div key={group.label}>
                <button type="button" aria-expanded={open} aria-controls={id}
                    onClick={() => setExpanded(current => ({...current, [`${pathname}:${group.label}`]: !open}))}
                    className={`flex min-h-11 w-full items-center justify-between rounded-xl px-3 text-left text-sm font-semibold ${active ? "text-[#7a1625]" : "text-[#241715]"}`}>
                    {group.label}<span aria-hidden="true">{open ? "-" : "+"}</span>
                </button>
                {open && <div id={id} className="ml-3 border-l border-[#eadfd6] pl-2">{items.map(link)}</div>}
            </div>;
        })}
        {canSee({href: "/admin/reports", label: "Reports", permissions: ["REPORT_VIEW"]})
            && link({href: "/admin/reports", label: "Reports"})}
        <div className="mt-4 border-t border-[#eadfd6] pt-3">{link({href: "/admin/me", label: "My account"})}</div>
    </nav>;
}
