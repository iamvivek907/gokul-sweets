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
    getProductIntelligence
} from "@/services/adminProductReportsApi";

import type {
    ExecutiveDashboardOptions
} from "@/types/adminReports";

import type {
    ProductIntelligenceItem,
    ProductIntelligenceResponse,
    ProductPerformanceState
} from "@/types/adminProductReports";


type StateFilter =
    | "ALL"
    | ProductPerformanceState;


export default function AdminProductReportsPage() {

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
        stateFilter,
        setStateFilter
    ] =
        useState<StateFilter>(
            "ALL"
        );


    const [
        search,
        setSearch
    ] =
        useState("");


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
        useState<ProductIntelligenceResponse | null>(
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
                getProductIntelligence(
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
                                : "Unable to load product intelligence."
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


    const visibleProducts =
        useMemo(
            () => {

                if (
                    !report
                ) {

                    return [];
                }


                const normalizedSearch =
                    search
                        .trim()
                        .toLowerCase();


                return report.products.filter(
                    product => {

                        if (
                            stateFilter !== "ALL"
                            &&
                            product.state !== stateFilter
                        ) {

                            return false;
                        }


                        if (
                            !normalizedSearch
                        ) {

                            return true;
                        }


                        return (
                            product.productName
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                            ||
                            product.productCode
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                            ||
                            product.categoryName
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                        );
                    }
                );
            },
            [
                report,
                search,
                stateFilter
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
                    Product Intelligence
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    Product Performance
                </h1>


                <p className="mt-3 max-w-4xl text-sm leading-6 text-[#756763]">
                    Compare product revenue, quantity, order penetration, growth and selling-day consistency using explainable performance states.
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
                                    label="Products With Sales"
                                    value={
                                        number(
                                            report.summary.productsWithSales
                                        )
                                    }
                                />

                                <Metric
                                    label="Units Sold"
                                    value={
                                        number(
                                            report.summary.totalQuantitySold
                                        )
                                    }
                                />

                                <Metric
                                    label="Gross Item Revenue"
                                    value={
                                        money(
                                            report.summary.grossItemRevenue
                                        )
                                    }
                                />

                                <Metric
                                    label="Star Products"
                                    value={
                                        number(
                                            report.summary.starProducts
                                        )
                                    }
                                />

                                <Metric
                                    label="Growing Products"
                                    value={
                                        number(
                                            report.summary.growingProducts
                                        )
                                    }
                                />

                                <Metric
                                    label="Declining Products"
                                    value={
                                        number(
                                            report.summary.decliningProducts
                                        )
                                    }
                                />

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4">

                                <div className="grid gap-3 lg:grid-cols-[1fr_260px]">

                                    <input
                                        value={
                                            search
                                        }
                                        onChange={
                                            event =>
                                                setSearch(
                                                    event.target.value
                                                )
                                        }
                                        placeholder="Search product, code or category"
                                        className={INPUT_CLASS}
                                    />

                                    <select
                                        value={
                                            stateFilter
                                        }
                                        onChange={
                                            event =>
                                                setStateFilter(
                                                    event.target.value as StateFilter
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    >
                                        <option value="ALL">All states</option>
                                        <option value="STAR">Star</option>
                                        <option value="GROWING">Growing</option>
                                        <option value="CORE">Core</option>
                                        <option value="NICHE">Niche</option>
                                        <option value="DECLINING">Declining</option>
                                        <option value="NEW_INSUFFICIENT_DATA">New / insufficient data</option>
                                    </select>

                                </div>


                                <p className="mt-3 text-xs text-[#756763]">
                                    Comparison period: {report.comparisonFromDate} to {report.comparisonToDate}
                                </p>

                            </div>


                            <div className="mt-6 space-y-4">

                                {
                                    visibleProducts.map(
                                        product => (
                                            <ProductCard
                                                key={
                                                    product.productId
                                                }
                                                product={
                                                    product
                                                }
                                            />
                                        )
                                    )
                                }


                                {
                                    visibleProducts.length === 0
                                    && (
                                        <div className="rounded-2xl border border-[#eadfd6] bg-white p-8 text-sm text-[#756763]">
                                            No products match the selected filters.
                                        </div>
                                    )
                                }

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">

                                <h2 className="font-bold text-[#241715]">
                                    How the current classification works
                                </h2>

                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    STAR requires at least 20% order penetration, sales on at least 50% of selected days, and non-negative growth. GROWING means revenue is at least 20% above the previous comparable period. CORE requires at least 10% order penetration and 40% selling-day consistency. DECLINING means revenue is at least 20% below the previous period. Products without enough completed-order history are marked NEW / INSUFFICIENT DATA; the remainder are NICHE.
                                </p>

                            </div>

                        </>
                    )
                }

            </div>

        </div>
    );
}


function ProductCard({
    product
}: {
    product: ProductIntelligenceItem;
}) {

    return (
        <article className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6">

            <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">

                <div>
                    <div className="flex flex-wrap items-center gap-2">

                        <h2 className="text-lg font-bold text-[#241715]">
                            {product.productName}
                        </h2>

                        <StateBadge
                            state={
                                product.state
                            }
                        />

                    </div>

                    <p className="mt-1 text-xs text-[#756763]">
                        {product.productCode} · {product.categoryName}
                    </p>
                </div>


                <div className="text-left xl:text-right">
                    <p className="text-2xl font-bold text-[#7a1625]">
                        {money(product.grossItemRevenue)}
                    </p>
                    <p className="mt-1 text-xs text-[#756763]">
                        {number(product.quantitySold)} units
                    </p>
                </div>

            </div>


            <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">

                <SmallMetric
                    label="Orders"
                    value={
                        number(
                            product.orderCount
                        )
                    }
                />

                <SmallMetric
                    label="Order Penetration"
                    value={
                        `${product.orderPenetrationPercent.toFixed(1)}%`
                    }
                />

                <SmallMetric
                    label="Selling-Day Consistency"
                    value={
                        `${product.activeDayConsistencyPercent.toFixed(1)}%`
                    }
                />

                <SmallMetric
                    label="Revenue Growth"
                    value={
                        `${product.revenueGrowthPercent > 0 ? "+" : ""}${product.revenueGrowthPercent.toFixed(1)}%`
                    }
                />

                <SmallMetric
                    label="Unique Customers"
                    value={
                        number(
                            product.uniqueCustomers
                        )
                    }
                />

            </div>


            <p className="mt-4 rounded-xl bg-[#fffaf3] px-4 py-3 text-sm leading-6 text-[#756763]">
                {product.stateReason}
            </p>

        </article>
    );
}


function StateBadge({
    state
}: {
    state: ProductPerformanceState;
}) {

    const label =
        state === "NEW_INSUFFICIENT_DATA"
            ? "NEW / INSUFFICIENT DATA"
            : state.replaceAll(
                "_",
                " "
            );


    const classes =
        state === "STAR"
            ? "bg-[#f6dfad] text-[#5d0f1b]"
            : state === "GROWING"
                ? "bg-green-50 text-green-800"
                : state === "DECLINING"
                    ? "bg-red-50 text-red-700"
                    : state === "CORE"
                        ? "bg-blue-50 text-blue-800"
                        : "bg-gray-100 text-gray-700";


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {label}
        </span>
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
