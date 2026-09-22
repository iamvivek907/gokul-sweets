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
    getDemandForecast
} from "@/services/adminDemandForecastApi";

import type {
    ExecutiveDashboardOptions
} from "@/types/adminReports";

import type {
    DemandForecastConfidence,
    DemandForecastResponse
} from "@/types/adminDemandForecast";


export default function AdminDemandForecastPage() {

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


    const defaultDate =
        useMemo(
            () =>
                shiftDate(
                    businessDate(),
                    1
                ),
            []
        );


    const [
        targetDate,
        setTargetDate
    ] =
        useState(
            defaultDate
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
        forecast,
        setForecast
    ] =
        useState<DemandForecastResponse | null>(
            null
        );


    const [
        search,
        setSearch
    ] =
        useState("");


    const [
        confidence,
        setConfidence
    ] =
        useState<DemandForecastConfidence | "ALL">(
            "ALL"
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


            getExecutiveDashboardOptions(
                authorization,
                controller.signal
            )
                .then(
                    result => {

                        setOptions(
                            result
                        );


                        setBranchId(
                            current =>
                                current
                                ?? result.branches[0]?.id
                                ?? null
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
                                : "Unable to load branches."
                        );
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            canView
        ]
    );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !canView
                ||
                branchId === null
            ) {

                return;
            }


            const controller =
                new AbortController();


            getDemandForecast(
                authorization,
                {
                    targetDate,
                    branchId
                },
                controller.signal
            )
                .then(
                    result => {

                        setForecast(
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
                                : "Unable to calculate demand forecast."
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
            branchId,
            targetDate
        ]
    );


    const visibleProducts =
        useMemo(
            () => {

                if (
                    !forecast
                ) {

                    return [];
                }


                const normalized =
                    search
                        .trim()
                        .toLowerCase();


                return forecast.products.filter(
                    product => {

                        if (
                            confidence !== "ALL"
                            &&
                            product.confidence !== confidence
                        ) {

                            return false;
                        }


                        if (
                            !normalized
                        ) {

                            return true;
                        }


                        return (
                            product.productName
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            ||
                            product.productCode
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            ||
                            product.categoryName
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                        );
                    }
                );
            },
            [
                forecast,
                search,
                confidence
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
                    Demand Forecasting
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    Production Demand Forecast
                </h1>


                <p className="mt-3 max-w-4xl text-sm leading-6 text-[#756763]">
                    Estimate how many units may be needed for a selected pickup date and branch using comparable weekdays from recent history.
                </p>


                <div className="mt-6 grid gap-3 rounded-2xl border border-[#eadfd6] bg-white p-4 md:grid-cols-2">

                    <input
                        type="date"
                        min={
                            businessDate()
                        }
                        value={
                            targetDate
                        }
                        onChange={
                            event =>
                                setTargetDate(
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
                    forecast
                    && (
                        <>

                            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">

                                <Metric
                                    label="Products Forecasted"
                                    value={
                                        number(
                                            forecast.summary.productsForecasted
                                        )
                                    }
                                />

                                <Metric
                                    label="Recommended Units"
                                    value={
                                        number(
                                            forecast.summary.recommendedUnits
                                        )
                                    }
                                />

                                <Metric
                                    label="Likely Range"
                                    value={
                                        `${number(forecast.summary.lowerUnits)} – ${number(forecast.summary.upperUnits)}`
                                    }
                                />

                                <Metric
                                    label="High Confidence"
                                    value={
                                        number(
                                            forecast.summary.highConfidenceProducts
                                        )
                                    }
                                />

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4">

                                <div className="grid gap-3 lg:grid-cols-[1fr_220px]">

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
                                            confidence
                                        }
                                        onChange={
                                            event =>
                                                setConfidence(
                                                    event.target.value as DemandForecastConfidence | "ALL"
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    >
                                        <option value="ALL">All confidence levels</option>
                                        <option value="HIGH">High confidence</option>
                                        <option value="MEDIUM">Medium confidence</option>
                                        <option value="LOW">Low confidence</option>
                                    </select>

                                </div>


                                <p className="mt-3 text-xs text-[#756763]">
                                    Forecast for {forecast.targetDate} · {forecast.branchName} · based on up to {forecast.historyWeeks} comparable weekdays.
                                </p>

                            </div>


                            <div className="mt-6 space-y-4">

                                {
                                    visibleProducts.map(
                                        product => (
                                            <article
                                                key={
                                                    product.productId
                                                }
                                                className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6"
                                            >

                                                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">

                                                    <div>

                                                        <div className="flex flex-wrap items-center gap-2">

                                                            <h2 className="text-lg font-bold text-[#241715]">
                                                                {product.productName}
                                                            </h2>


                                                            <ConfidenceBadge
                                                                value={
                                                                    product.confidence
                                                                }
                                                            />

                                                        </div>


                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            {product.productCode} · {product.categoryName}
                                                        </p>

                                                    </div>


                                                    <div className="text-left xl:text-right">

                                                        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                                                            Recommended
                                                        </p>


                                                        <p className="mt-1 text-4xl font-bold text-[#7a1625]">
                                                            {number(product.recommendedQuantity)}
                                                        </p>


                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            likely {number(product.lowerQuantity)} – {number(product.upperQuantity)} units
                                                        </p>

                                                    </div>

                                                </div>


                                                <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">

                                                    <SmallMetric
                                                        label="Recent 4 Weeks"
                                                        value={
                                                            `${product.recentFourWeekAverage.toFixed(2)} avg`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Earlier 8 Weeks"
                                                        value={
                                                            `${product.previousEightWeekAverage.toFixed(2)} avg`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Trend"
                                                        value={
                                                            `${product.trendPercent > 0 ? "+" : ""}${product.trendPercent.toFixed(1)}%`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Variability"
                                                        value={
                                                            `${product.variabilityPercent.toFixed(1)}%`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Weeks With Sales"
                                                        value={
                                                            `${product.weeksWithSales}/${product.weeksObserved}`
                                                        }
                                                    />

                                                </div>


                                                <p className="mt-4 rounded-xl bg-[#fffaf3] px-4 py-3 text-sm leading-6 text-[#756763]">
                                                    {product.explanation}
                                                </p>

                                            </article>
                                        )
                                    )
                                }


                                {
                                    visibleProducts.length === 0
                                    && (
                                        <div className="rounded-2xl border border-[#eadfd6] bg-white p-8 text-sm text-[#756763]">
                                            No historical product demand is available for the selected branch and comparable weekday.
                                        </div>
                                    )
                                }

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">

                                <h2 className="font-bold text-[#241715]">
                                    How this first forecast works
                                </h2>


                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    The model looks at the same weekday over the previous 12 weeks. The most recent 4 weeks receive 65% weight and the earlier 8 weeks receive 35%. A limited trend adjustment is then applied, and historical variation creates the likely lower/upper range. Confidence is higher when the product sells on many comparable weeks and its quantities are relatively stable.
                                </p>


                                <p className="mt-3 text-sm leading-6 text-[#756763]">
                                    This is an explainable baseline forecast. Festival dates, holidays, weather, local events and manual business adjustments are not included yet; those can be layered on after we establish enough real historical data.
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


function ConfidenceBadge({
    value
}: {
    value: DemandForecastConfidence;
}) {

    const classes =
        value === "HIGH"
            ? "bg-green-50 text-green-800"
            : value === "MEDIUM"
                ? "bg-amber-50 text-amber-800"
                : "bg-red-50 text-red-700";


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {value} CONFIDENCE
        </span>
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
