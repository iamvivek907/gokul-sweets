"use client";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import ReportNavigation
    from "@/components/admin/ReportNavigation";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getExecutiveDashboardOptions
} from "@/services/adminReportsApi";

import {
    getSalesIntelligence
} from "@/services/adminSalesReportsApi";

import type {
    ExecutiveDashboardOptions
} from "@/types/adminReports";

import type {
    SalesIntelligenceResponse
} from "@/types/adminSalesReports";


export default function AdminSalesReportsPage() {

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


    const initialRange =
        useMemo(
            () => {

                const today =
                    businessDate();

                return {
                    fromDate:
                        shiftDate(
                            today,
                            -29
                        ),
                    toDate:
                        today
                };
            },
            []
        );


    const [
        fromDate,
        setFromDate
    ] =
        useState(
            initialRange.fromDate
        );


    const [
        toDate,
        setToDate
    ] =
        useState(
            initialRange.toDate
        );


    const [
        branchId,
        setBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        options,
        setOptions
    ] =
        useState<ExecutiveDashboardOptions | null>(
            null
        );


    const [
        report,
        setReport
    ] =
        useState<SalesIntelligenceResponse | null>(
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


            Promise.all([
                getExecutiveDashboardOptions(
                    authorization,
                    controller.signal
                ),
                getSalesIntelligence(
                    authorization,
                    {
                        fromDate,
                        toDate,
                        branchId
                    },
                    controller.signal
                )
            ])
                .then(
                    ([
                        optionsResult,
                        reportResult
                    ]) => {

                        setOptions(
                            optionsResult
                        );

                        setReport(
                            reportResult
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
                                : "Unable to load sales intelligence."
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
            fromDate,
            toDate,
            branchId
        ]
    );


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
                    Sales Intelligence
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    Sales Patterns & Contribution
                </h1>


                <p className="mt-3 max-w-3xl text-sm leading-6 text-[#756763]">
                    Understand when customers buy, which categories contribute revenue, how branches compare, and where pickup demand is concentrated.
                </p>


                <div className="mt-6 grid gap-3 rounded-2xl border border-[#eadfd6] bg-white p-4 md:grid-cols-3">

                    <input
                        type="date"
                        value={
                            fromDate
                        }
                        onChange={
                            event =>
                                setFromDate(
                                    event.target.value
                                )
                        }
                        className={INPUT_CLASS}
                    />


                    <input
                        type="date"
                        value={
                            toDate
                        }
                        onChange={
                            event =>
                                setToDate(
                                    event.target.value
                                )
                        }
                        className={INPUT_CLASS}
                    />


                    <select
                        value={
                            branchId
                            ?? ""
                        }
                        onChange={
                            event =>
                                setBranchId(
                                    event.target.value
                                        ? Number(
                                            event.target.value
                                        )
                                        : null
                                )
                        }
                        className={INPUT_CLASS}
                    >
                        <option value="">
                            All branches
                        </option>

                        {
                            options?.branches.map(
                                branch => (
                                    <option
                                        key={
                                            branch.id
                                        }
                                        value={
                                            branch.id
                                        }
                                    >
                                        {branch.name}
                                    </option>
                                )
                            )
                        }
                    </select>

                </div>


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

                            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">

                                <Metric
                                    label="Revenue"
                                    value={
                                        money(
                                            report.summary.revenue
                                        )
                                    }
                                    change={
                                        report.summary.revenueChangePercent
                                    }
                                />

                                <Metric
                                    label="Completed Orders"
                                    value={
                                        number(
                                            report.summary.completedOrders
                                        )
                                    }
                                    change={
                                        report.summary.ordersChangePercent
                                    }
                                />

                                <Metric
                                    label="Units Sold"
                                    value={
                                        number(
                                            report.summary.unitsSold
                                        )
                                    }
                                />

                                <Metric
                                    label="Unique Customers"
                                    value={
                                        number(
                                            report.summary.uniqueCustomers
                                        )
                                    }
                                />

                                <Metric
                                    label="Average Order Value"
                                    value={
                                        money(
                                            report.summary.averageOrderValue
                                        )
                                    }
                                />

                                <Metric
                                    label="Discount Given"
                                    value={
                                        money(
                                            report.summary.discountAmount
                                        )
                                    }
                                />

                            </div>


                            <p className="mt-3 text-xs text-[#756763]">
                                Compared with {report.comparisonFromDate} to {report.comparisonToDate}
                            </p>


                            <div className="mt-6 grid gap-6 xl:grid-cols-2">

                                <Card
                                    title="Weekday Performance"
                                    subtitle="Average revenue per active weekday"
                                >
                                    <Bars
                                        rows={
                                            report.weekdayPerformance.map(
                                                item => ({
                                                    label:
                                                        item.weekday,
                                                    value:
                                                        item.averageRevenuePerActiveDay,
                                                    suffix:
                                                        money(
                                                            item.averageRevenuePerActiveDay
                                                        )
                                                })
                                            )
                                        }
                                    />
                                </Card>


                                <Card
                                    title="Pickup Hour Demand"
                                    subtitle="Completed orders by pickup hour"
                                >
                                    <Bars
                                        rows={
                                            report.pickupHours.map(
                                                item => ({
                                                    label:
                                                        hour(
                                                            item.hour
                                                        ),
                                                    value:
                                                        item.completedOrders,
                                                    suffix:
                                                        `${number(item.completedOrders)} orders`
                                                })
                                            )
                                        }
                                    />
                                </Card>

                            </div>


                            <div className="mt-6 grid gap-6 xl:grid-cols-2">

                                <Card
                                    title="Category Contribution"
                                    subtitle="Gross item revenue by category"
                                >
                                    <ContributionTable
                                        rows={
                                            report.categoryContribution.map(
                                                item => ({
                                                    key:
                                                        String(
                                                            item.categoryId
                                                        ),
                                                    label:
                                                        item.categoryName,
                                                    secondary:
                                                        `${number(item.quantitySold)} units`,
                                                    value:
                                                        money(
                                                            item.grossItemRevenue
                                                        ),
                                                    share:
                                                        item.revenueSharePercent
                                                })
                                            )
                                        }
                                    />
                                </Card>


                                <Card
                                    title="Branch Revenue Mix"
                                    subtitle="Share of selected-period revenue"
                                >
                                    <ContributionTable
                                        rows={
                                            report.branchMix.map(
                                                item => ({
                                                    key:
                                                        String(
                                                            item.branchId
                                                        ),
                                                    label:
                                                        item.branchName,
                                                    secondary:
                                                        `${number(item.completedOrders)} orders · ${number(item.unitsSold)} units`,
                                                    value:
                                                        money(
                                                            item.revenue
                                                        ),
                                                    share:
                                                        item.revenueSharePercent
                                                })
                                            )
                                        }
                                    />
                                </Card>

                            </div>


                            <div className="mt-6">

                                <Card
                                    title="Daily Sales"
                                    subtitle="Revenue, completed orders and units by pickup date"
                                >

                                    <div className="overflow-x-auto">

                                        <table className="min-w-full text-sm">

                                            <thead>
                                                <tr className="border-b border-[#eadfd6] text-left text-xs uppercase tracking-[0.08em] text-[#756763]">
                                                    <th className="px-3 py-3">Date</th>
                                                    <th className="px-3 py-3 text-right">Revenue</th>
                                                    <th className="px-3 py-3 text-right">Orders</th>
                                                    <th className="px-3 py-3 text-right">Units</th>
                                                    <th className="px-3 py-3 text-right">Customers</th>
                                                </tr>
                                            </thead>

                                            <tbody className="divide-y divide-[#eadfd6]">
                                                {
                                                    report.dailyTrend.map(
                                                        day => (
                                                            <tr
                                                                key={
                                                                    day.date
                                                                }
                                                            >
                                                                <td className="px-3 py-3 font-semibold text-[#241715]">
                                                                    {day.date}
                                                                </td>
                                                                <td className="px-3 py-3 text-right font-semibold text-[#7a1625]">
                                                                    {money(day.revenue)}
                                                                </td>
                                                                <td className="px-3 py-3 text-right text-[#756763]">
                                                                    {number(day.orders)}
                                                                </td>
                                                                <td className="px-3 py-3 text-right text-[#756763]">
                                                                    {number(day.unitsSold)}
                                                                </td>
                                                                <td className="px-3 py-3 text-right text-[#756763]">
                                                                    {number(day.uniqueCustomers)}
                                                                </td>
                                                            </tr>
                                                        )
                                                    )
                                                }
                                            </tbody>

                                        </table>

                                    </div>

                                </Card>

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
    value,
    change
}: {
    label: string;
    value: string;
    change?: number;
}) {

    return (
        <div className="rounded-2xl border border-[#eadfd6] bg-white p-5">
            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                {label}
            </p>

            <p className="mt-2 text-3xl font-bold text-[#241715]">
                {value}
            </p>

            {
                change !== undefined
                ? (
                    <p className={`mt-2 text-xs font-semibold ${
                        change > 0
                            ? "text-green-700"
                            : change < 0
                                ? "text-red-700"
                                : "text-[#756763]"
                    }`}>
                        {change > 0 ? "↑ " : change < 0 ? "↓ " : ""}
                        {Math.abs(change).toFixed(1)}% vs previous period
                    </p>
                )
                : null
            }
        </div>
    );
}


function Card({
    title,
    subtitle,
    children
}: {
    title: string;
    subtitle: string;
    children: React.ReactNode;
}) {

    return (
        <section className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6">
            <h2 className="text-lg font-bold text-[#241715]">
                {title}
            </h2>
            <p className="mt-1 text-xs text-[#756763]">
                {subtitle}
            </p>
            <div className="mt-5">
                {children}
            </div>
        </section>
    );
}


function Bars({
    rows
}: {
    rows: {
        label: string;
        value: number;
        suffix: string;
    }[];
}) {

    const max =
        Math.max(
            ...rows.map(
                row =>
                    row.value
            ),
            0
        );


    if (
        rows.length === 0
    ) {

        return (
            <p className="text-sm text-[#756763]">
                No completed sales in this period.
            </p>
        );
    }


    return (
        <div className="space-y-3">
            {
                rows.map(
                    row => (
                        <div
                            key={
                                row.label
                            }
                        >
                            <div className="mb-1 flex justify-between gap-4 text-xs">
                                <span className="font-semibold text-[#241715]">
                                    {row.label}
                                </span>
                                <span className="text-[#756763]">
                                    {row.suffix}
                                </span>
                            </div>

                            <div className="h-2 overflow-hidden rounded-full bg-[#f3e8df]">
                                <div
                                    className="h-full rounded-full bg-[#7a1625]"
                                    style={{
                                        width:
                                            `${
                                                max <= 0
                                                    ? 0
                                                    : Math.max(
                                                        2,
                                                        (
                                                            row.value
                                                            / max
                                                        )
                                                        * 100
                                                    )
                                            }%`
                                    }}
                                />
                            </div>
                        </div>
                    )
                )
            }
        </div>
    );
}


function ContributionTable({
    rows
}: {
    rows: {
        key: string;
        label: string;
        secondary: string;
        value: string;
        share: number;
    }[];
}) {

    if (
        rows.length === 0
    ) {

        return (
            <p className="text-sm text-[#756763]">
                No completed sales in this period.
            </p>
        );
    }


    return (
        <div className="space-y-3">
            {
                rows.map(
                    row => (
                        <div
                            key={
                                row.key
                            }
                            className="rounded-xl border border-[#eadfd6] bg-[#fffaf3] p-4"
                        >
                            <div className="flex items-start justify-between gap-4">
                                <div>
                                    <p className="font-bold text-[#241715]">
                                        {row.label}
                                    </p>
                                    <p className="mt-1 text-xs text-[#756763]">
                                        {row.secondary}
                                    </p>
                                </div>

                                <div className="text-right">
                                    <p className="font-bold text-[#7a1625]">
                                        {row.value}
                                    </p>
                                    <p className="mt-1 text-xs text-[#756763]">
                                        {row.share.toFixed(1)}%
                                    </p>
                                </div>
                            </div>
                        </div>
                    )
                )
            }
        </div>
    );
}


function businessDate() {

    return new Intl.DateTimeFormat(
        "en-CA",
        {
            timeZone:
                "Asia/Kolkata",
            year:
                "numeric",
            month:
                "2-digit",
            day:
                "2-digit"
        }
    ).format(
        new Date()
    );
}


function shiftDate(
    date: string,
    days: number
) {

    const value =
        new Date(
            `${date}T12:00:00Z`
        );


    value.setUTCDate(
        value.getUTCDate()
        + days
    );


    return value
        .toISOString()
        .slice(
            0,
            10
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


function hour(
    value: number
) {

    const suffix =
        value >= 12
            ? "PM"
            : "AM";

    const display =
        value % 12
        || 12;

    return `${display} ${suffix}`;
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
