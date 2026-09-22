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
    getBasketAnalysis
} from "@/services/adminBasketReportsApi";

import type {
    ExecutiveDashboardOptions
} from "@/types/adminReports";

import type {
    BasketAnalysisResponse,
    BasketPairStrength
} from "@/types/adminBasketReports";


type StrengthFilter =
    | "ALL"
    | BasketPairStrength;


export default function AdminBasketReportsPage() {

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
        useState<BasketAnalysisResponse | null>(
            null
        );


    const [
        strength,
        setStrength
    ] =
        useState<StrengthFilter>(
            "ALL"
        );


    const [
        search,
        setSearch
    ] =
        useState("");


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
                getBasketAnalysis(
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
                                : "Unable to load basket analysis."
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


    const visiblePairs =
        useMemo(
            () => {

                if (
                    !report
                ) {

                    return [];
                }


                const normalized =
                    search
                        .trim()
                        .toLowerCase();


                return report.pairs.filter(
                    pair => {

                        if (
                            strength !== "ALL"
                            &&
                            pair.strength !== strength
                        ) {

                            return false;
                        }


                        if (
                            !normalized
                        ) {

                            return true;
                        }


                        return (
                            pair.productAName
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            ||
                            pair.productBName
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            ||
                            pair.productACode
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                            ||
                            pair.productBCode
                                .toLowerCase()
                                .includes(
                                    normalized
                                )
                        );
                    }
                );
            },
            [
                report,
                strength,
                search
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
                    Basket Intelligence
                </p>


                <h1 className="mt-2 text-3xl font-bold tracking-tight text-[#241715] sm:text-4xl">
                    Products Bought Together
                </h1>


                <p className="mt-3 max-w-4xl text-sm leading-6 text-[#756763]">
                    Find product combinations that appear together in completed orders and distinguish genuine affinity from pairs that are common only because both products are individually popular.
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

                            <div className="mt-6 grid gap-3 sm:grid-cols-2 xl:grid-cols-5">

                                <Metric
                                    label="Completed Orders"
                                    value={
                                        number(
                                            report.summary.completedOrders
                                        )
                                    }
                                />

                                <Metric
                                    label="Products In Orders"
                                    value={
                                        number(
                                            report.summary.productsInOrders
                                        )
                                    }
                                />

                                <Metric
                                    label="Pair Relationships"
                                    value={
                                        number(
                                            report.summary.pairRelationships
                                        )
                                    }
                                />

                                <Metric
                                    label="Strong Pairs"
                                    value={
                                        number(
                                            report.summary.strongPairs
                                        )
                                    }
                                />

                                <Metric
                                    label="Moderate Pairs"
                                    value={
                                        number(
                                            report.summary.moderatePairs
                                        )
                                    }
                                />

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-white p-4">

                                <div className="grid gap-3 lg:grid-cols-[1fr_240px]">

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
                                        placeholder="Search product name or code"
                                        className={INPUT_CLASS}
                                    />


                                    <select
                                        value={
                                            strength
                                        }
                                        onChange={
                                            event =>
                                                setStrength(
                                                    event.target.value as StrengthFilter
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    >
                                        <option value="ALL">All pair strengths</option>
                                        <option value="STRONG">Strong</option>
                                        <option value="MODERATE">Moderate</option>
                                        <option value="WEAK">Weak</option>
                                        <option value="INSUFFICIENT_DATA">Insufficient data</option>
                                    </select>

                                </div>

                            </div>


                            <div className="mt-6 space-y-4">

                                {
                                    visiblePairs.map(
                                        pair => (
                                            <article
                                                key={
                                                    `${pair.productAId}-${pair.productBId}`
                                                }
                                                className="rounded-2xl border border-[#eadfd6] bg-white p-5 sm:p-6"
                                            >

                                                <div className="flex flex-col gap-4 xl:flex-row xl:items-start xl:justify-between">

                                                    <div>

                                                        <div className="flex flex-wrap items-center gap-2">

                                                            <h2 className="text-lg font-bold text-[#241715]">
                                                                {pair.productAName} + {pair.productBName}
                                                            </h2>


                                                            <StrengthBadge
                                                                value={
                                                                    pair.strength
                                                                }
                                                            />

                                                        </div>


                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            {pair.productACode} + {pair.productBCode}
                                                        </p>

                                                    </div>


                                                    <div className="text-left xl:text-right">

                                                        <p className="text-xs font-semibold uppercase tracking-[0.08em] text-[#756763]">
                                                            Together in
                                                        </p>


                                                        <p className="mt-1 text-3xl font-bold text-[#7a1625]">
                                                            {number(pair.pairOrderCount)}
                                                        </p>


                                                        <p className="mt-1 text-xs text-[#756763]">
                                                            completed orders
                                                        </p>

                                                    </div>

                                                </div>


                                                <div className="mt-5 grid gap-3 sm:grid-cols-2 lg:grid-cols-5">

                                                    <SmallMetric
                                                        label="Support"
                                                        value={
                                                            `${pair.supportPercent.toFixed(1)}%`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label={`${pair.productAName} → ${pair.productBName}`}
                                                        value={
                                                            `${pair.confidenceAToBPercent.toFixed(1)}%`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label={`${pair.productBName} → ${pair.productAName}`}
                                                        value={
                                                            `${pair.confidenceBToAPercent.toFixed(1)}%`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="Lift"
                                                        value={
                                                            `${pair.lift.toFixed(2)}×`
                                                        }
                                                    />

                                                    <SmallMetric
                                                        label="All Orders"
                                                        value={
                                                            number(
                                                                pair.totalCompletedOrders
                                                            )
                                                        }
                                                    />

                                                </div>


                                                <p className="mt-4 rounded-xl bg-[#fffaf3] px-4 py-3 text-sm leading-6 text-[#756763]">
                                                    {pair.explanation}
                                                </p>

                                            </article>
                                        )
                                    )
                                }


                                {
                                    visiblePairs.length === 0
                                    && (
                                        <div className="rounded-2xl border border-[#eadfd6] bg-white p-8 text-sm text-[#756763]">
                                            No product-pair history matches the selected filters.
                                        </div>
                                    )
                                }

                            </div>


                            <div className="mt-6 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-5">

                                <h2 className="font-bold text-[#241715]">
                                    How to read basket affinity
                                </h2>


                                <p className="mt-2 text-sm leading-6 text-[#756763]">
                                    Support is the share of completed orders containing both products. Confidence A → B answers: “When A is bought, how often is B also bought?” Lift corrects for general product popularity. A lift above 1 means the products occur together more often than expected if purchases were independent.
                                </p>


                                <p className="mt-3 text-sm leading-6 text-[#756763]">
                                    The current rule marks a pair STRONG when it has at least 3 observed pair orders, at least 10% support and lift of 1.5× or more. MODERATE requires at least 5% support and lift above 1.1×. These are explainable starting thresholds and can be tuned after more real order history accumulates.
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

            <p className="truncate text-[11px] font-semibold uppercase tracking-[0.04em] text-[#756763]">
                {label}
            </p>

            <p className="mt-1 font-bold text-[#241715]">
                {value}
            </p>

        </div>
    );
}


function StrengthBadge({
    value
}: {
    value: BasketPairStrength;
}) {

    const classes =
        value === "STRONG"
            ? "bg-green-50 text-green-800"
            : value === "MODERATE"
                ? "bg-amber-50 text-amber-800"
                : value === "WEAK"
                    ? "bg-gray-100 text-gray-700"
                    : "bg-blue-50 text-blue-800";


    const label =
        value === "INSUFFICIENT_DATA"
            ? "INSUFFICIENT DATA"
            : value;


    return (
        <span className={`rounded-full px-2.5 py-1 text-xs font-bold ${classes}`}>
            {label}
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
