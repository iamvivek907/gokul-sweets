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
    getExecutiveDashboard,
    getExecutiveDashboardOptions
} from "@/services/adminReportsApi";

import type {
    ExecutiveDashboardOptions,
    ExecutiveDashboardResponse
} from "@/types/adminReports";


type QuickRange =
    | "TODAY"
    | "YESTERDAY"
    | "LAST_7"
    | "LAST_30"
    | "THIS_MONTH"
    | "CUSTOM";


export default function AdminReportsPage() {

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
            () =>
                getRange(
                    "LAST_7"
                ),
            []
        );


    const [
        quickRange,
        setQuickRange
    ] =
        useState<QuickRange>(
            "LAST_7"
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
        dashboard,
        setDashboard
    ] =
        useState<ExecutiveDashboardResponse | null>(
            null
        );


    const [
        loading,
        setLoading
    ] =
        useState(true);


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
                getExecutiveDashboard(
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
                        dashboardResult
                    ]) => {

                        setOptions(
                            optionsResult
                        );


                        setDashboard(
                            dashboardResult
                        );


                        setError(
                            null
                        );


                        setLoading(
                            false
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
                                : "Unable to load reports."
                        );


                        setLoading(
                            false
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


    function applyQuickRange(
        value: QuickRange
    ) {

        setQuickRange(
            value
        );


        if (
            value === "CUSTOM"
        ) {

            return;
        }


        const range =
            getRange(
                value
            );


        setFromDate(
            range.fromDate
        );


        setToDate(
            range.toDate
        );
    }


    if (
        profile
        &&
        !canView
    ) {

        return (
            <div className="px-4 py-6 sm:px-6 lg:px-8">

                <div
                    className="
                        mx-auto
                        max-w-7xl
                        rounded-2xl
                        border
                        border-red-200
                        bg-red-50
                        p-6
                        text-red-800
                    "
                >
                    You do not have permission to view reports.
                </div>

            </div>
        );
    }


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

                <div>

                    <p className="text-xs font-semibold uppercase tracking-[0.16em] text-[#c88a20]">
                        Business Intelligence
                    </p>


                    <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                        Executive Dashboard
                    </h1>


                    <p className="mt-3 max-w-3xl text-sm leading-6 text-[#756763]">
                        Understand revenue, demand, branch performance and important business patterns from completed pickup orders.
                    </p>

                </div>


                <div className="mt-6">
                    <ReportNavigation />
                </div>


                <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4 sm:p-5">

                    <div className="flex flex-wrap gap-2">

                        {
                            (
                                [
                                    ["TODAY", "Today"],
                                    ["YESTERDAY", "Yesterday"],
                                    ["LAST_7", "Last 7 days"],
                                    ["LAST_30", "Last 30 days"],
                                    ["THIS_MONTH", "This month"],
                                    ["CUSTOM", "Custom"]
                                ] as const
                            ).map(
                                ([
                                    value,
                                    label
                                ]) => (

                                    <button
                                        key={
                                            value
                                        }
                                        type="button"
                                        onClick={
                                            () =>
                                                applyQuickRange(
                                                    value
                                                )
                                        }
                                        className={`
                                            min-h-10
                                            rounded-xl
                                            border
                                            px-3
                                            text-sm
                                            font-semibold

                                            ${
                                                quickRange === value
                                                    ? "border-[#7a1625] bg-[#fff1e9] text-[#7a1625]"
                                                    : "border-[#eadfd6] bg-white text-[#756763]"
                                            }
                                        `}
                                    >
                                        {label}
                                    </button>

                                )
                            )
                        }

                    </div>


                    <div className="mt-4 grid gap-3 md:grid-cols-3">

                        <input
                            type="date"
                            value={
                                fromDate
                            }
                            onChange={
                                event => {

                                    setQuickRange(
                                        "CUSTOM"
                                    );


                                    setFromDate(
                                        event.target.value
                                    );
                                }
                            }
                            className={INPUT_CLASS}
                        />


                        <input
                            type="date"
                            value={
                                toDate
                            }
                            onChange={
                                event => {

                                    setQuickRange(
                                        "CUSTOM"
                                    );


                                    setToDate(
                                        event.target.value
                                    );
                                }
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
                    loading
                    &&
                    !dashboard
                        ? (

                            <div className="mt-6 text-sm text-[#756763]">
                                Loading executive dashboard...
                            </div>

                        )
                        : dashboard
                            ? (

                                <>

                                    <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-3">

                                        <Kpi
                                            label="Revenue"
                                            value={
                                                formatMoney(
                                                    dashboard.kpis.revenue
                                                )
                                            }
                                            change={
                                                dashboard.kpis.revenueChangePercent
                                            }
                                        />

                                        <Kpi
                                            label="Completed Orders"
                                            value={
                                                formatNumber(
                                                    dashboard.kpis.completedOrders
                                                )
                                            }
                                            change={
                                                dashboard.kpis.ordersChangePercent
                                            }
                                        />

                                        <Kpi
                                            label="Unique Customers"
                                            value={
                                                formatNumber(
                                                    dashboard.kpis.uniqueCustomers
                                                )
                                            }
                                            change={
                                                dashboard.kpis.customersChangePercent
                                            }
                                        />

                                        <Kpi
                                            label="Units Sold"
                                            value={
                                                formatNumber(
                                                    dashboard.kpis.unitsSold
                                                )
                                            }
                                        />

                                        <Kpi
                                            label="Average Order Value"
                                            value={
                                                formatMoney(
                                                    dashboard.kpis.averageOrderValue
                                                )
                                            }
                                        />

                                        <Kpi
                                            label="Discount Given"
                                            value={
                                                formatMoney(
                                                    dashboard.kpis.discountAmount
                                                )
                                            }
                                        />

                                    </div>


                                    <p className="mt-3 text-xs text-[#756763]">
                                        Comparison: {dashboard.comparisonFromDate} to {dashboard.comparisonToDate}
                                    </p>


                                    <div className="mt-6 grid gap-6 xl:grid-cols-2">

                                        <ReportCard
                                            title="Revenue Trend"
                                            subtitle="Net revenue by pickup date"
                                        >

                                            <LineChart
                                                data={
                                                    dashboard.revenueTrend.map(
                                                        item => ({
                                                            label:
                                                                shortDate(
                                                                    item.date
                                                                ),
                                                            value:
                                                                item.revenue
                                                        })
                                                    )
                                                }
                                                valueFormatter={
                                                    formatMoneyCompact
                                                }
                                            />

                                        </ReportCard>


                                        <ReportCard
                                            title="Hourly Demand"
                                            subtitle="Completed orders by pickup hour"
                                        >

                                            <BarChart
                                                data={
                                                    dashboard.hourlyDemand.map(
                                                        item => ({
                                                            label:
                                                                formatHour(
                                                                    item.hour
                                                                ),
                                                            value:
                                                                item.orders
                                                        })
                                                    )
                                                }
                                                valueFormatter={
                                                    formatNumber
                                                }
                                            />

                                        </ReportCard>

                                    </div>


                                    <div className="mt-6 grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">

                                        <ReportCard
                                            title="Branch Performance"
                                            subtitle="Revenue in the selected period"
                                        >

                                            <BarChart
                                                data={
                                                    dashboard.branches.map(
                                                        item => ({
                                                            label:
                                                                item.branchName,
                                                            value:
                                                                item.revenue
                                                        })
                                                    )
                                                }
                                                valueFormatter={
                                                    formatMoneyCompact
                                                }
                                            />

                                        </ReportCard>


                                        <ReportCard
                                            title="Business Highlights"
                                            subtitle="Important signals from the selected period"
                                        >

                                            <div className="space-y-3">

                                                <Highlight
                                                    label="Strongest day"
                                                    value={
                                                        dashboard.highlights.strongestDay
                                                            ? `${dashboard.highlights.strongestDay} · ${formatMoney(dashboard.highlights.strongestDayRevenue)}`
                                                            : "No completed sales"
                                                    }
                                                />

                                                <Highlight
                                                    label="Strongest branch"
                                                    value={
                                                        dashboard.highlights.strongestBranchName
                                                            ? `${dashboard.highlights.strongestBranchName} · ${formatMoney(dashboard.highlights.strongestBranchRevenue)}`
                                                            : "No completed sales"
                                                    }
                                                />

                                                <Highlight
                                                    label="Peak pickup hour"
                                                    value={
                                                        dashboard.highlights.peakPickupHour !== null
                                                            ? `${formatHour(dashboard.highlights.peakPickupHour)} · ${formatNumber(dashboard.highlights.peakPickupOrders)} orders`
                                                            : "No completed sales"
                                                    }
                                                />

                                                <Highlight
                                                    label="Top product by quantity"
                                                    value={
                                                        dashboard.highlights.topProductName
                                                            ? `${dashboard.highlights.topProductName} · ${formatNumber(dashboard.highlights.topProductQuantity)} units · ${formatMoney(dashboard.highlights.topProductRevenue)}`
                                                            : "No completed sales"
                                                    }
                                                />

                                            </div>

                                        </ReportCard>

                                    </div>

                                </>

                            )
                            : null
                }

            </div>

        </div>
    );
}


function Kpi({
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

            <p className="text-xs font-semibold uppercase tracking-0.1em text-[#756763]">
                {label}
            </p>


            <p className="mt-2 text-3xl font-bold text-[#241715]">
                {value}
            </p>


            {
                change !== undefined
                ? (

                    <p
                        className={`
                            mt-2
                            text-xs
                            font-semibold

                            ${
                                change > 0
                                    ? "text-green-700"
                                    : change < 0
                                        ? "text-red-700"
                                        : "text-[#756763]"
                            }
                        `}
                    >
                        {change > 0 ? "↑ " : change < 0 ? "↓ " : ""}
                        {Math.abs(change).toFixed(1)}% vs previous period
                    </p>

                )
                : null
            }

        </div>
    );
}


function ReportCard({
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


function Highlight({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div className="rounded-xl border border-[#eadfd6] bg-[#fffaf3] p-4">

            <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                {label}
            </p>

            <p className="mt-2 text-sm font-bold text-[#241715]">
                {value}
            </p>

        </div>
    );
}


function BarChart({
    data,
    valueFormatter
}: {
    data: {
        label: string;
        value: number;
    }[];
    valueFormatter: (value: number) => string;
}) {

    const max =
        Math.max(
            ...data.map(
                item =>
                    item.value
            ),
            0
        );


    if (
        data.length === 0
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
                data.map(
                    item => {

                        const width =
                            max <= 0
                                ? 0
                                : Math.max(
                                    2,
                                    (
                                        item.value
                                        / max
                                    )
                                    * 100
                                );


                        return (
                            <div
                                key={
                                    item.label
                                }
                            >

                                <div className="mb-1 flex items-center justify-between gap-4 text-xs">

                                    <span className="truncate font-semibold text-[#241715]">
                                        {item.label}
                                    </span>

                                    <span className="shrink-0 text-[#756763]">
                                        {valueFormatter(item.value)}
                                    </span>

                                </div>


                                <div className="h-2 overflow-hidden rounded-full bg-[#f3e8df]">

                                    <div
                                        className="h-full rounded-full bg-[#7a1625]"
                                        style={{
                                            width:
                                                `${width}%`
                                        }}
                                    />

                                </div>

                            </div>
                        );
                    }
                )
            }

        </div>
    );
}


function LineChart({
    data,
    valueFormatter
}: {
    data: {
        label: string;
        value: number;
    }[];
    valueFormatter: (value: number) => string;
}) {

    if (
        data.length === 0
    ) {

        return (
            <p className="text-sm text-[#756763]">
                No completed sales in this period.
            </p>
        );
    }


    const width =
        720;

    const height =
        240;

    const padding =
        28;

    const max =
        Math.max(
            ...data.map(
                item =>
                    item.value
            ),
            1
        );


    const points =
        data.map(
            (item, index) => {

                const x =
                    data.length === 1
                        ? width / 2
                        : padding
                        + (
                            index
                            / (
                                data.length - 1
                            )
                        )
                        * (
                            width
                            - padding * 2
                        );

                const y =
                    height
                    - padding
                    - (
                        item.value
                        / max
                    )
                    * (
                        height
                        - padding * 2
                    );

                return {
                    ...item,
                    x,
                    y
                };
            }
        );


    return (
        <div>

            <div className="overflow-x-auto">

                <svg
                    viewBox={`0 0 ${width} ${height}`}
                    className="min-w-620px w-full"
                    role="img"
                    aria-label="Revenue trend"
                >

                    <line
                        x1={
                            padding
                        }
                        x2={
                            width - padding
                        }
                        y1={
                            height - padding
                        }
                        y2={
                            height - padding
                        }
                        stroke="currentColor"
                        className="text-[#eadfd6]"
                    />


                    <polyline
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="4"
                        strokeLinejoin="round"
                        strokeLinecap="round"
                        className="text-[#7a1625]"
                        points={
                            points
                                .map(
                                    point =>
                                        `${point.x},${point.y}`
                                )
                                .join(
                                    " "
                                )
                        }
                    />


                    {
                        points.map(
                            point => (

                                <circle
                                    key={
                                        `${point.label}-${point.x}`
                                    }
                                    cx={
                                        point.x
                                    }
                                    cy={
                                        point.y
                                    }
                                    r="4"
                                    fill="currentColor"
                                    className="text-[#c88a20]"
                                >
                                    <title>
                                        {point.label}: {valueFormatter(point.value)}
                                    </title>
                                </circle>

                            )
                        )
                    }

                </svg>

            </div>


            <div className="mt-2 flex justify-between text-xs text-[#756763]">

                <span>
                    {data[0]?.label}
                </span>

                <span>
                    {data[data.length - 1]?.label}
                </span>

            </div>

        </div>
    );
}


function getRange(
    range: Exclude<QuickRange, "CUSTOM">
) {

    const today =
        businessDate();


    if (
        range === "TODAY"
    ) {

        return {
            fromDate:
                today,
            toDate:
                today
        };
    }


    if (
        range === "YESTERDAY"
    ) {

        const yesterday =
            shiftDate(
                today,
                -1
            );

        return {
            fromDate:
                yesterday,
            toDate:
                yesterday
        };
    }


    if (
        range === "LAST_30"
    ) {

        return {
            fromDate:
                shiftDate(
                    today,
                    -29
                ),
            toDate:
                today
        };
    }


    if (
        range === "THIS_MONTH"
    ) {

        return {
            fromDate:
                `${today.slice(0, 8)}01`,
            toDate:
                today
        };
    }


    return {
        fromDate:
            shiftDate(
                today,
                -6
            ),
        toDate:
            today
    };
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


function shortDate(
    value: string
) {

    return new Intl.DateTimeFormat(
        "en-IN",
        {
            day:
                "numeric",
            month:
                "short"
        }
    ).format(
        new Date(
            `${value}T12:00:00Z`
        )
    );
}


function formatHour(
    hour: number
) {

    const suffix =
        hour >= 12
            ? "PM"
            : "AM";

    const display =
        hour % 12
        || 12;

    return `${display} ${suffix}`;
}


function formatMoney(
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


function formatMoneyCompact(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            notation:
                "compact",
            maximumFractionDigits:
                1
        }
    ).format(
        value
    );
}


function formatNumber(
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
