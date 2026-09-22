"use client";

import Link
    from "next/link";

import {
    usePathname
} from "next/navigation";

import {
    useEffect,
    useState
} from "react";

import {
    createPortal
} from "react-dom";

import ApprovalPendingBadge
    from "@/components/admin/ApprovalPendingBadge";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";


interface NavItem {

    href: string;

    label: string;

    permissions?: string[];

    ownerOnly?: boolean;
}


const navItems:
    NavItem[] = [

    {
        href: "/admin",
        label: "Dashboard"
    },

    {
        href: "/admin/me",
        label: "My Details"
    },

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
        permissions: [
            "ORDER_VIEW"
        ]
    },

    {
        href: "/admin/menu",
        label: "Menu",
        permissions: [
            "MENU_MANAGE"
        ]
    },

    {
        href: "/admin/branches",
        label: "Branches",
        permissions: [
            "BRANCH_MANAGE"
        ]
    },

    {
        href: "/admin/customers",
        label: "Customers",
        permissions: [
            "CUSTOMER_VIEW"
        ]
    },

    {
        href: "/admin/notifications",
        label: "Offers & Notifications",
        ownerOnly: true
    },

    {
        href: "/admin/staff",
        label: "Staff",
        permissions: [
            "STAFF_MANAGE"
        ]
    },

    {
        href: "/admin/approvals",
        label: "Approvals",
        permissions: [
            "APPROVAL_VIEW"
        ]
    },

    {
        href: "/admin/payroll",
        label: "Payroll",
        permissions: [
            "PAYROLL_VIEW"
        ]
    },

    {
        href: "/admin/reports",
        label: "Reports",
        permissions: [
            "REPORT_VIEW"
        ]
    }
];


export default function AdminMobileNav() {

    const pathname =
        usePathname();


    const {
        profile,
        hasAnyPermission
    } =
        useAdminAuth();


    const [
        open,
        setOpen
    ] =
        useState(false);


    function canSee(
        item: NavItem
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
            item.ownerOnly
        ) {

            return false;
        }


        if (
            !item.permissions
            ||
            item.permissions.length
                === 0
        ) {

            return true;
        }


        return hasAnyPermission(
            item.permissions
        );
    }


    function isActive(
        href: string
    ) {

        if (
            href === "/admin"
        ) {

            return pathname
                === "/admin";
        }


        return pathname === href
            ||
            pathname.startsWith(
                `${href}/`
            );
    }


    /*
     * Prevent the page behind the drawer
     * from scrolling while menu is open.
     */
    useEffect(
        () => {

            if (!open) {

                return;
            }


            const previousOverflow =
                document.body.style.overflow;


            document.body.style.overflow =
                "hidden";


            return () => {

                document.body.style.overflow =
                    previousOverflow;
            };

        },
        [
            open
        ]
    );


    /*
     * Allow Escape key to close the drawer.
     */
    useEffect(
        () => {

            if (!open) {

                return;
            }


            function handleKeyDown(
                event: KeyboardEvent
            ) {

                if (
                    event.key
                    === "Escape"
                ) {

                    setOpen(
                        false
                    );
                }
            }


            window.addEventListener(
                "keydown",
                handleKeyDown
            );


            return () => {

                window.removeEventListener(
                    "keydown",
                    handleKeyDown
                );
            };

        },
        [
            open
        ]
    );


    const drawer =
        open
        &&
        typeof document
            !== "undefined"
            ? createPortal(

                <div
                    className="
                        fixed
                        inset-0
                        z-100
                        bg-black/40

                        lg:hidden
                    "
                    onClick={
                        () =>
                            setOpen(
                                false
                            )
                    }
                >

                    <aside
                        className="
                            absolute
                            inset-y-0
                            left-0
                            flex
                            w-[86%]
                            max-w-sm
                            flex-col
                            overflow-y-auto
                            border-r
                            border-[#eadfd6]
                            bg-white
                            shadow-2xl
                        "
                        onClick={
                            event =>
                                event
                                    .stopPropagation()
                        }
                    >

                        <div
                            className="
                                flex
                                items-start
                                justify-between
                                gap-4
                                border-b
                                border-[#eadfd6]
                                px-5
                                py-5
                            "
                        >

                            <div>

                                <p
                                    className="
                                        text-xs
                                        font-semibold
                                        uppercase
                                        tracking-[0.18em]
                                        text-[#c88a20]
                                    "
                                >
                                    Gokul Sweets
                                </p>


                                <h2
                                    className="
                                        mt-1
                                        text-xl
                                        font-bold
                                        text-[#7a1625]
                                    "
                                >
                                    Admin Portal
                                </h2>

                            </div>


                            <button
                                type="button"
                                onClick={
                                    () =>
                                        setOpen(
                                            false
                                        )
                                }
                                aria-label="Close admin navigation"
                                className="
                                    flex
                                    h-10
                                    w-10
                                    shrink-0
                                    items-center
                                    justify-center
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    text-xl
                                    text-[#756763]
                                    transition

                                    hover:bg-[#fffaf3]
                                    hover:text-[#241715]

                                    focus-visible:outline-none
                                    focus-visible:ring-4
                                    focus-visible:ring-[#c88a20]/30
                                "
                            >
                                ×
                            </button>

                        </div>


                        <nav
                            className="
                                flex-1
                                space-y-1
                                px-4
                                py-5
                            "
                        >

                            {
                                navItems
                                    .filter(
                                        canSee
                                    )
                                    .map(
                                        item => (

                                            <Link
                                                key={
                                                    item.href
                                                }
                                                href={
                                                    item.href
                                                }
                                                onClick={
                                                    () =>
                                                        setOpen(
                                                            false
                                                        )
                                                }
                                                className={[
                                                    "flex min-h-12 items-center rounded-xl px-4 text-sm font-semibold transition",

                                                    isActive(
                                                        item.href
                                                    )
                                                        ? "bg-[#fff1e9] text-[#7a1625]"
                                                        : "text-[#756763] hover:bg-[#fffaf3] hover:text-[#241715]"
                                                ].join(
                                                    " "
                                                )}
                                            >
                                                <span>
                                                    {
                                                        item.label
                                                    }
                                                </span>


                                                {
                                                    item.href
                                                    === "/admin/approvals"
                                                    && (

                                                        <ApprovalPendingBadge />

                                                    )
                                                }
                                            </Link>

                                        )
                                    )
                            }

                        </nav>


                        <div
                            className="
                                border-t
                                border-[#eadfd6]
                                p-4
                            "
                        >

                            <div
                                className="
                                    rounded-2xl
                                    border
                                    border-[#eadfd6]
                                    bg-[#fffaf3]
                                    p-4
                                "
                            >

                                <p
                                    className="
                                        truncate
                                        text-sm
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    {
                                        profile?.fullName
                                    }
                                </p>


                                <p
                                    className="
                                        mt-1
                                        text-xs
                                        font-medium
                                        text-[#756763]
                                    "
                                >
                                    {
                                        profile?.roleName
                                    }
                                </p>

                            </div>

                        </div>

                    </aside>

                </div>,

                document.body

            )
            : null;


    return (
        <div
            className="
                lg:hidden
            "
        >

            <button
                type="button"
                onClick={
                    () =>
                        setOpen(
                            true
                        )
                }
                aria-expanded={
                    open
                }
                aria-label="Open admin navigation"
                className="
                    flex
                    min-h-10
                    items-center
                    justify-center
                    rounded-xl
                    border
                    border-[#eadfd6]
                    bg-white
                    px-3
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
                Menu
            </button>


            {drawer}

        </div>
    );
}
