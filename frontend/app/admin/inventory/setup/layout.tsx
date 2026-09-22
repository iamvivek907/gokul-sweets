import Link from "next/link";

import type {
    ReactNode
} from "react";

export default function InventorySetupLayout({
    children
}: {
    children: ReactNode;
}) {
    return (
        <div className="min-w-0 flex-1 bg-[#fffaf3]">
            <div className="px-4 pt-10 sm:px-6 lg:px-8">
                <div className="mx-auto flex max-w-[1500px] items-center justify-between gap-4">
                    <Link
                        href="/admin/inventory"
                        className="inline-flex min-h-11 w-fit items-center justify-center rounded-xl border border-[#eadfd6] bg-white px-5 text-sm font-bold text-[#7a1625] shadow-sm transition hover:border-[#c88a20] hover:bg-[#fff4e5]"
                    >
                        ← Back to Inventory
                    </Link>

                    <Link
                        href="/admin/inventory/production"
                        className="hidden min-h-11 items-center justify-center rounded-xl px-4 text-sm font-bold text-[#7a1625] transition hover:bg-[#fff4e5] sm:inline-flex"
                    >
                        Production Board →
                    </Link>
                </div>
            </div>

            {children}
        </div>
    );
}
