"use client";

import Link
    from "next/link";

import {
    useEffect,
    useState
} from "react";

import ReportNavigation
    from "@/components/admin/ReportNavigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getCustomerIntelligence
} from "@/services/adminCustomerReportsApi";

import type {
    CustomerIntelligenceResponse,
    CustomerLifecycleState,
    CustomerValueSegment
} from "@/types/adminCustomerReports";


export default function AdminCustomerReportsPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const canView =
        hasPermission(
            "REPORT_VIEW"
        );


    const [
        searchInput,
        setSearchInput
    ] =
        useState("");


    const [
        search,
        setSearch
    ] =
        useState("");


    const [
        lifecycle,
        setLifecycle
    ] =
        useState<CustomerLifecycleState | null>(
            null
        );


    const [
        valueSegment,
        setValueSegment
    ] =
        useState<CustomerValueSegment | null>(
            null
        );


    const [
        page,
        setPage
    ] =
        useState(0);


    const [
        report,
        setReport
    ] =
        useState<CustomerIntelligenceResponse | null>(
            null
        );


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !canView
            ) {

                return;
            }


            const controller =
                new AbortController();


            getCustomerIntelligence(
                authorization,
                {
                    search,
                    lifecycle,
                    valueSegment,
                    page,
                    size:
                        25
                },
                controller.signal
            )
                .then(
                    result => {

                        setReport(
                            result
                        );

                        setError(
                            null
                        );
                    }
                )
                .catch(
                    exception => {

                        if (
                            exception instanceof DOMException
                            &&
                            exception.name === "AbortError"
                        ) {

                            return;
                        }


                        setError(
                            exception instanceof Error
                                ? exception.message
                                : "Unable to load customer intelligence."
                        );
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            canView,
            search,
            lifecycle,
            valueSegment,
            page
        ]
    );


    function submitSearch() {

        setPage(
            0
        );

        setSearch(
            searchInput.trim()
        );
    }


    if (
        profile
        &&
        !canView
    ) {

        return (
            <div className="px-4 py-6 sm:px-6 lg:px-8">
                <div className="mx-auto max-w-7xl rounded-2xl border border-red-200 bg-red-50 p-6 text-red-800">
                    You do not have permission to view reports.
                </div>
            </div>
        );
    }


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

                <ReportNavigation />


                <p className="mt-6 text-xs font-semibold uppercase tracking-[0.16em] text-[#c88a20]">
                    Customer Intelligence
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    Customer Value & Reorder Health
                </h1>


                <p className="mt-3 max-w-4xl text-sm leading-6 text-[#756763]">
                    Understand repeat behavior, customer value, and whether customers are returning within their own normal purchase rhythm. Guest phone groups remain unverified identities until explicitly verified.
                </p>


                {
                    error
                    && (
                        <div className="mt-5 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm font-medium text-red-700">
                            {error}
                        </div>
                    )
                }


                {
                    report
                    && (
                        <>

                            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">

                                <Metric
                                    label="Customers With Purchases"
                                    value={
                                        number(
                                            report.summary.customersWithPurchases
                                        )
                                    }
                                />

                                <Metric
                                    label="Repeat Customers"
                                    value={
                                        `${number(report.summary.repeatCustomers)} · ${report.summary.repeatCustomerPercent.toFixed(1)}%`
                                    }
                                />

                                <Metric
                                    label="Active Customers"
                                    value={
                                        number(
                                            report.summary.activeCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="At Risk"
                                    value={
                                        number(
                                            report.summary.atRiskCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="Lapsed"
                                    value={
                                        number(
                                            report.summary.lapsedCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="Reactivated"
                                    value={
                                        number(
                                            report.summary.reactivatedCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="High Value + VIP"
                                    value={
                                        number(
                                            report.summary.highValueCustomers
                                            +
                                            report.summary.vipCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="Avg Lifetime Spend"
                                    value={
                                        money(
                                            report.summary.averageLifetimeSpend
                                        )
                                    }
                                />

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4">

                                <div className="grid gap-3 xl:grid-cols-[1fr_190px_190px_auto]">

                                    <input
                                        value={
                                            searchInput
                                        }
                                        onChange={
                                            event =>
                                                setSearchInput(
                                                    event.target.value
                                                )
                                        }
                                        onKeyDown={
                                            event => {

                                                if (
                                                    event.key === "Enter"
                                                ) {

                                                    submitSearch();
                                                }
                                            }
                                        }
                                        placeholder="Search name or phone"
                                        className={INPUT_CLASS}
                                    />


                                    <select
                                        value={
                                            lifecycle
                                            ?? ""
                                        }
                                        onChange={
                                            event => {

                                                setPage(
                                                    0
                                                );

                                                setLifecycle(
                                                    event.target.value
                                                        ? event.target.value as CustomerLifecycleState
                                                        : null
                                                );
                                            }
                                        }
                                        className={INPUT_CLASS}
                                    >
                                        <option value="">All lifecycle states</option>
                                        <option value="NEW">New</option>
                                        <option value="ACTIVE">Active</option>
                                        <option value="WATCH">Watch</option>
                                        <option value="AT_RISK">At risk</option>
                                        <option value="LAPSED">Lapsed</option>
                                        <option value="REACTIVATED">Reactivated</option>
                                    </select>


                                    <select
                                        value={
                                            valueSegment
                                            ?? ""
                                        }
                                        onChange={
                                            event => {

                                                setPage(
                                                    0
                                                );

                                                setValueSegment(
                                                    event.target.value
                                                        ? event.target.value as CustomerValueSegment
                                                        : null
                                                );
                                            }
                                        }
                                        className={INPUT_CLASS}
                                    >
                                        <option value="">All value segments</option>
                                        <option value="NEW">New</option>
                                        <option value="OCCASIONAL">Occasional</option>
                                        <option value="REGULAR">Regular</option>
                                        <option value="HIGH_VALUE">High value</option>
                                        <option value="VIP">VIP</option>
                                    </select>


                                    <button
                                        type="button"
                                        onClick={
                                            submitSearch
                                        }
                                        className="
                                            min-h-11
                                            rounded-xl
                                            bg-[#7a1625]
                                            px-5
                                            text-sm
                                            font-semibold
                                            !text-white

                                            hover:bg-[#5d0f1b]
                                        "
                                    >
                                        Search
                                    </button>

                                </div>

                            </div>


                            <div className="mt-6 space-y-4">

                                {
                                    report.customers.content.map(
                                        customer => (
                                            <article
                                                key={
                                                    customer.customerId
                                                }
                                                className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6"
                                            >

                                                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">

                                                    <div>

                                                        <div className="flex flex-wrap items-center gap-2">

                                                            <Link
                                                                href={
                                                                    `/admin/customers/${customer.customerId}`
                                                                }
                                                                className="text-lg font-bold text-[#241715] hover:text-[#7a1625]"
                                                            >
                                                                {
                                                                    customer.latestName
                                                                    ?? "Guest customer"
                                                                }
                                                            </Link>


                                                            <LifecycleBadge
                                                                value={
                                                                    customer.lifecycleState
                                                                }
                                                            />


                                                            <ValueBadge
                                                                value={
                                                                    customer.valueSegment
                                                                }
                                                            />

                                                        </div>


                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            {customer.normalizedPhone} · {customer.verificationStatus === "VERIFIED" ? "Verified identity" : "Guest / unverified grouping"}
                                                        </p>

                                                    </div>


                                                    <div className="text-left xl:text-right">

                                                        <p className="text-2xl font-bold text-[#7a1625]">
                                                            {money(customer.lifetimeSpend)}
                                                        </p>

                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            {number(customer.lifetimeOrders)} completed orders
                                                        </p>

                                                    </div>

                                                </div>


                                                <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-6">

                                                    <SmallMetric
                                                        label="AOV"
                                                        value={
                                                            money(
                                                                customer.averageOrderValue
                                                            )
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Last Purchase"
                                                        value={
                                                            customer.daysSinceLastPurchase === null
                                                                ? "—"
                                                                : `${customer.daysSinceLastPurchase}d ago`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Expected Gap"
                                                        value={
                                                            customer.expectedPurchaseGapDays === null
                                                                ? "Not established"
                                                                : `${customer.expectedPurchaseGapDays.toFixed(1)}d`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Gap Ratio"
                                                        value={
                                                            customer.currentGapRatio === null
                                                                ? "—"
                                                                : `${customer.currentGapRatio.toFixed(2)}×`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Orders 90d"
                                                        value={
                                                            number(
                                                                customer.orders90d
                                                            )
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Spend 90d"
                                                        value={
                                                            money(
                                                                customer.spend90d
                                                            )
                                                        }
                                                    />

                                                </div>


                                                <p className="mt-4 rounded-xl bg-[#fffaf3] px-4 py-3 text-sm leading-6 text-[#756763]">
                                                    {customer.lifecycleReason}
                                                </p>

                                            </article>
                                        )
                                    )
                                }


                                {
                                    report.customers.content.length === 0
                                    && (
                                        <div className="rounded-2xl border border-[#eadfd6] bg-white p-8 text-sm text-[#756763]">
                                            No customers match the selected filters.
                                        </div>
                                    )
                                }

                            </div>


                            {
                                report.customers.totalPages > 1
                                && (
                                    <div className="mt-5 flex items-center justify-between gap-3">

                                        <p className="text-sm text-[#756763]">
                                            Page {report.customers.page + 1} of {report.customers.totalPages} · {number(report.customers.totalElements)} customers
                                        </p>


                                        <div className="flex gap-2">

                                            <button
                                                type="button"
                                                disabled={
                                                    report.customers.page === 0
                                                }
                                                onClick={
                                                    () =>
                                                        setPage(
                                                            current =>
                                                                Math.max(
                                                                    current - 1,
                                                                    0
                                                                )
                                                        )
                                                }
                                                className={PAGE_BUTTON}
                                            >
                                                Previous
                                            </button>


                                            <button
                                                type="button"
                                                disabled={
                                                    report.customers.page + 1
                                                    >= report.customers.totalPages
                                                }
                                                onClick={
                                                    () =>
                                                        setPage(
                                                            current =>
                                                                current + 1
                                                        )
                                                }
                                                className={PAGE_BUTTON}
                                            >
                                                Next
                                            </button>

                                        </div>

                                    </div>
                                )
                            }


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">

                                <h2 className="font-bold text-[#241715]">
                                    Lifecycle interpretation
                                </h2>

                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    For customers with enough repeat history, the system compares the current time since last purchase with that customer’s own expected purchase gap. Under 1.5× is Active, 1.5–2.5× is Watch, 2.5–4× is At Risk, and above 4× is Lapsed. Reactivated means the customer returned after an unusually long prior gap and is currently back inside their normal rhythm.
                                </p>


                                <h2 className="mt-4 font-bold text-[#241715]">
                                    Value interpretation
                                </h2>

                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    Value segments describe observed purchasing behavior only; they do not estimate a customer’s income or financial capacity. VIP and High Value are based on completed-order spend relative to other purchasing customers, while Regular and Occasional also consider repeat frequency.
                                </p>

                            </div>

                        </>
                    )
                }

            </div>

        </div>
    );
}


function Metric({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div className="rounded-2xl border border-[#eadfd6] bg-white p-5">

            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                {label}
            </p>

            <p className="mt-2 text-3xl font-bold text-[#241715]">
                {value}
            </p>

        </div>
    );
}


function SmallMetric({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div className="rounded-xl border border-[#eadfd6] bg-[#fffaf3] p-3">

            <p className="text-[11px] font-semibold uppercase tracking-[0.06em] text-[#756763]">
                {label}
            </p>

            <p className="mt-1 font-bold text-[#241715]">
                {value}
            </p>

        </div>
    );
}


function LifecycleBadge({
    value
}: {
    value: CustomerLifecycleState;
}) {

    const classes =
        value === "ACTIVE"
        ||
        value === "REACTIVATED"
            ? "bg-green-50 text-green-800"
            : value === "WATCH"
                ? "bg-amber-50 text-amber-800"
                : value === "AT_RISK"
                    ? "bg-orange-50 text-orange-800"
                    : value === "LAPSED"
                        ? "bg-red-50 text-red-700"
                        : "bg-blue-50 text-blue-800";


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {value.replaceAll("_", " ")}
        </span>
    );
}


function ValueBadge({
    value
}: {
    value: CustomerValueSegment;
}) {

    const classes =
        value === "VIP"
            ? "bg-[#f6dfad] text-[#5d0f1b]"
            : value === "HIGH_VALUE"
                ? "bg-purple-50 text-purple-800"
                : "bg-gray-100 text-gray-700";


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {value.replaceAll("_", " ")}
        </span>
    );
}


function money(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            maximumFractionDigits:
                2
        }
    ).format(
        value
    );
}


function number(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN"
    ).format(
        value
    );
}


const INPUT_CLASS =
    `
        min-h-11
        w-full
        rounded-xl
        border
        border-[#eadfd6]
        bg-white
        px-4
        text-sm
        text-[#241715]
        outline-none

        focus:border-[#c88a20]
        focus:ring-4
        focus:ring-[#f6dfad]/40
    `;


const PAGE_BUTTON =
    `
        min-h-11
        rounded-xl
        border
        border-[#eadfd6]
        bg-white
        px-4
        text-sm
        font-semibold
        text-[#7a1625]

        hover:bg-[#fffaf3]

        disabled:cursor-not-allowed
        disabled:opacity-50
    `;
