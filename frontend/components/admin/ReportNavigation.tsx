"use client";

import Link
    from "next/link";

import {
    usePathname
} from "next/navigation";


const ITEMS = [
    {
        href:
            "/admin/reports",
        label:
            "Executive"
    },
    {
        href:
            "/admin/reports/sales",
        label:
            "Sales"
    },
    {
        href:
            "/admin/reports/products",
        label:
            "Products"
    },
    {
        href:
            "/admin/reports/customers",
        label:
            "Customers"
    },
    {
        href:
            "/admin/reports/forecast",
        label:
            "Forecast"
    },
    {
        href:
            "/admin/reports/basket",
        label:
            "Basket"
    },
    {
        href:
            "/admin/reports/insights",
        label:
            "Insights"
    }
] as const;


export default function ReportNavigation() {

    const pathname =
        usePathname();


    return (
        <nav
            aria-label="Reporting sections"
            className="
                flex
                max-w-full
                gap-1
                overflow-x-auto
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                p-1.5
            "
        >

            {
                ITEMS.map(
                    item => {

                        const active =
                            pathname === item.href;


                        return (
                            <Link
                                key={
                                    item.href
                                }
                                href={
                                    item.href
                                }
                                aria-current={
                                    active
                                        ? "page"
                                        : undefined
                                }
                                className={`
                                    inline-flex
                                    min-h-10
                                    shrink-0
                                    items-center
                                    justify-center
                                    rounded-xl
                                    px-4
                                    text-sm
                                    font-semibold
                                    transition-colors

                                    ${
                                        active
                                            ? "bg-[#7a1625] text-white!"
                                            : "text-[#756763] hover:bg-[#fff1e9] hover:text-[#7a1625]"
                                    }

                                    focus-visible:outline-none
                                    focus-visible:ring-4
                                    focus-visible:ring-[#c88a20]/30
                                `}
                            >
                                {item.label}
                            </Link>
                        );
                    }
                )
            }

        </nav>
    );
}
