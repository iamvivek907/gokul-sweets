"use client";

import Link from "next/link";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

interface InventoryTool {
    href: string;
    eyebrow: string;
    title: string;
    description: string;
    action: string;
}

const inventoryTools: InventoryTool[] = [
    {
        href: "/admin/inventory/setup",
        eyebrow: "Planning & availability",
        title: "Inventory Setup",
        description:
            "Configure product control rules, approve quantities by date, and manage online availability for each branch.",
        action: "Open inventory setup"
    },
    {
        href: "/admin/inventory/production",
        eyebrow: "Daily operations",
        title: "Production Board",
        description:
            "See confirmed demand, temporary holds, physical stock, preparation requirements, wastage, and corrections.",
        action: "Open production board"
    },
    {
        href: "/admin/inventory/automation",
        eyebrow: "Planning intelligence",
        title: "Daily Automation",
        description:
            "Create safe daily allocation drafts, protect guaranteed stock, schedule festival products, and generate forecast suggestions.",
        action: "Open daily automation"
    }
];

export default function AdminInventoryHomePage() {
    const {
        profile,
        hasAnyPermission
    } = useAdminAuth();

    if (!profile) {
        return null;
    }

    const canAccessInventory =
        profile.roleName === "OWNER_ADMIN"
        || hasAnyPermission([
            "INVENTORY_VIEW",
            "INVENTORY_MANAGE"
        ]);

    if (!canAccessInventory) {
        return (
            <main className="min-w-0 flex-1 bg-[#fffaf3] px-4 py-8 sm:px-6 lg:px-8">
                <div className="mx-auto max-w-3xl rounded-3xl border border-red-200 bg-white p-8 text-center shadow-sm">
                    <h1 className="text-xl font-bold text-[#241715]">
                        Inventory access unavailable
                    </h1>
                    <p className="mt-2 text-sm leading-6 text-[#756763]">
                        Your role does not have permission to view inventory operations.
                    </p>
                </div>
            </main>
        );
    }

    return (
        <main className="min-w-0 flex-1 bg-[#fffaf3] px-4 pb-8 pt-12 sm:px-6 lg:px-8">
            <div className="mx-auto max-w-[1500px]">
                <section className="rounded-3xl border border-[#eadfd6] bg-white p-6 shadow-sm lg:p-10">
                    <p className="text-xs font-bold uppercase tracking-[0.18em] text-[#c88a20]">
                        Inventory management
                    </p>

                    <div className="mt-2 flex flex-col gap-5 xl:flex-row xl:items-end xl:justify-between">
                        <div>
                            <h1 className="text-3xl font-bold text-[#241715] sm:text-4xl">
                                Inventory
                            </h1>
                            <p className="mt-3 max-w-3xl text-sm leading-6 text-[#756763] sm:text-base">
                                Plan what can be sold online, automate safe daily allocations,
                                and manage what must be prepared or reconciled at each branch.
                            </p>
                        </div>

                        <div className="w-fit rounded-2xl border border-[#eadfd6] bg-[#fffaf3] px-5 py-3">
                            <p className="text-[10px] font-bold uppercase tracking-[0.14em] text-[#756763]">
                                Signed in as
                            </p>
                            <p className="mt-1 text-sm font-bold text-[#7a1625]">
                                {profile.roleName}
                            </p>
                        </div>
                    </div>
                </section>

                <section className="mt-8">
                    <h2 className="text-2xl font-bold text-[#241715]">
                        Inventory tools
                    </h2>
                    <p className="mt-1 text-sm text-[#756763]">
                        Choose the task you want to perform.
                    </p>

                    <div className="mt-5 grid gap-5 lg:grid-cols-3">
                        {inventoryTools.map(tool => (
                            <Link
                                key={tool.href}
                                href={tool.href}
                                className="group flex min-h-64 flex-col rounded-3xl border border-[#eadfd6] bg-white p-6 shadow-sm transition hover:-translate-y-0.5 hover:border-[#d9b897] hover:shadow-md sm:p-8"
                            >
                                <span className="w-fit rounded-full bg-[#fff1e9] px-3 py-1.5 text-[10px] font-bold uppercase tracking-[0.14em] text-[#7a1625]">
                                    {tool.eyebrow}
                                </span>

                                <h3 className="mt-5 text-2xl font-bold text-[#241715]">
                                    {tool.title}
                                </h3>

                                <p className="mt-3 text-sm leading-7 text-[#756763]">
                                    {tool.description}
                                </p>

                                <div className="mt-auto flex items-center justify-between pt-8 text-sm font-bold text-[#7a1625]">
                                    <span>{tool.action}</span>
                                    <span
                                        aria-hidden="true"
                                        className="text-xl text-[#c88a20] transition group-hover:translate-x-1"
                                    >
                                        →
                                    </span>
                                </div>
                            </Link>
                        ))}
                    </div>
                </section>

                <section className="mt-8 rounded-3xl border border-[#eadfd6] bg-[#fffaf3] p-6 sm:p-8">
                    <h2 className="text-lg font-bold text-[#241715]">
                        Recommended daily flow
                    </h2>

                    <div className="mt-5 grid gap-4 md:grid-cols-4">
                        <FlowStep
                            number="1"
                            title="Generate"
                            description="Create safe drafts and forecasts in Daily Automation."
                        />
                        <FlowStep
                            number="2"
                            title="Approve"
                            description="Review branch and date quantities in Inventory Setup."
                        />
                        <FlowStep
                            number="3"
                            title="Prepare"
                            description="Work from confirmed demand on the Production Board."
                        />
                        <FlowStep
                            number="4"
                            title="Reconcile"
                            description="Record produced stock, wastage, and verified corrections."
                        />
                    </div>
                </section>
            </div>
        </main>
    );
}

function FlowStep({
    number,
    title,
    description
}: {
    number: string;
    title: string;
    description: string;
}) {
    return (
        <div className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <div className="flex h-9 w-9 items-center justify-center rounded-full bg-[#7a1625] text-sm font-bold text-white">
                {number}
            </div>
            <h3 className="mt-4 font-bold text-[#241715]">
                {title}
            </h3>
            <p className="mt-2 text-sm leading-6 text-[#756763]">
                {description}
            </p>
        </div>
    );
}
