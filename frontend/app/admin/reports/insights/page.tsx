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
    getBusinessInsights
} from "@/services/adminBusinessInsightsApi";

import type {
    ExecutiveDashboardOptions
} from "@/types/adminReports";

import type {
    BusinessInsightSeverity,
    BusinessInsightsResponse
} from "@/types/adminBusinessInsights";


type SeverityFilter =
    | "ALL"
    | BusinessInsightSeverity;


export default function AdminBusinessInsightsPage() {

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
        severity,
        setSeverity
    ] =
        useState<SeverityFilter>(
            "ALL"
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
        useState<BusinessInsightsResponse | null>(
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
                getBusinessInsights(
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
                                : "Unable to load business insights."
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


    const visibleInsights =
        report
            ? report.insights.filter(
                insight =>
                    severity === "ALL"
                    ||
                    insight.severity === severity
            )
            : [];


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
                    Business Insights
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    What Needs Attention
                </h1>


                <p className="mt-3 max-w-4xl text-sm leading-6 text-[#756763]">
                    Deterministic business signals generated from completed sales, product trends, customer behavior, basket patterns and demand variability.
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

                            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-4">

                                <Metric
                                    label="Total Insights"
                                    value={
                                        number(
                                            report.summary.totalInsights
                                        )
                                    }
                                />

                                <Metric
                                    label="Action"
                                    value={
                                        number(
                                            report.summary.actionInsights
                                        )
                                    }
                                />

                                <Metric
                                    label="Watch"
                                    value={
                                        number(
                                            report.summary.watchInsights
                                        )
                                    }
                                />

                                <Metric
                                    label="Info"
                                    value={
                                        number(
                                            report.summary.infoInsights
                                        )
                                    }
                                />

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4">

                                <select
                                    value={
                                        severity
                                    }
                                    onChange={
                                        event =>
                                            setSeverity(
                                                event.target.value as SeverityFilter
                                            )
                                    }
                                    className={INPUT_CLASS}
                                >
                                    <option value="ALL">All severities</option>
                                    <option value="ACTION">Action</option>
                                    <option value="WATCH">Watch</option>
                                    <option value="INFO">Info</option>
                                </select>

                            </div>


                            <div className="mt-6 space-y-4">

                                {
                                    visibleInsights.map(
                                        (
                                            insight,
                                            index
                                        ) => (
                                            <article
                                                key={
                                                    `${insight.type}-${insight.entityId ?? "global"}-${index}`
                                                }
                                                className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6"
                                            >

                                                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">

                                                    <div>

                                                        <div className="flex flex-wrap items-center gap-2">

                                                            <SeverityBadge
                                                                value={
                                                                    insight.severity
                                                                }
                                                            />


                                                            <span className="rounded-full bg-gray-100 px-2.5 py-1 text-xs font-semibold text-gray-700">
                                                                {insight.type.replaceAll("_", " ")}
                                                            </span>

                                                        </div>


                                                        <h2 className="mt-3 text-lg font-bold text-[#241715]">
                                                            {insight.title}
                                                        </h2>


                                                        <p className="mt-2 text-sm leading-6 text-[#756763]">
                                                            {insight.message}
                                                        </p>

                                                    </div>


                                                    {
                                                        insight.primaryMetric !== null
                                                        &&
                                                        insight.primaryMetricLabel
                                                        && (
                                                            <div className="min-w-40 rounded-xl bg-[#fffaf3] p-4 xl:text-right">

                                                                <p className="text-xs font-semibold uppercase tracking-[0.06em] text-[#756763]">
                                                                    {insight.primaryMetricLabel}
                                                                </p>

                                                                <p className="mt-1 text-2xl font-bold text-[#7a1625]">
                                                                    {formatMetric(insight.primaryMetric, insight.primaryMetricLabel)}
                                                                </p>

                                                            </div>
                                                        )
                                                    }

                                                </div>


                                                <div className="mt-4 rounded-xl border border-[#eadfd6] bg-[#fffaf3] px-4 py-3">

                                                    <p className="text-xs font-semibold uppercase tracking-[0.06em] text-[#756763]">
                                                        Evidence
                                                    </p>

                                                    <p className="mt-1 text-sm leading-6 text-[#241715]">
                                                        {insight.evidence}
                                                    </p>

                                                </div>

                                            </article>
                                        )
                                    )
                                }


                                {
                                    visibleInsights.length === 0
                                    && (
                                        <div className="rounded-2xl border border-[#eadfd6] bg-white p-8 text-sm text-[#756763]">
                                            No insights match the selected period and severity filter.
                                        </div>
                                    )
                                }

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">

                                <h2 className="font-bold text-[#241715]">
                                    How these insights are produced
                                </h2>


                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    These are rule-based signals, not opaque AI judgments. Each alert is generated from explicit thresholds such as period-over-period revenue change, product decline, customer reorder-gap behavior, repeated basket relationships, demand variability, and branch revenue concentration.
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


function SeverityBadge({
    value
}: {
    value: BusinessInsightSeverity;
}) {

    const classes =
        value === "ACTION"
            ? "bg-red-50 text-red-700"
            : value === "WATCH"
                ? "bg-amber-50 text-amber-800"
                : "bg-blue-50 text-blue-800";


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {value}
        </span>
    );
}


function formatMetric(
    value: number,
    label: string
) {

    if (
        label.includes("%")
    ) {

        return `${value > 0 ? "+" : ""}${value.toFixed(1)}%`;
    }


    return number(
        value
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
