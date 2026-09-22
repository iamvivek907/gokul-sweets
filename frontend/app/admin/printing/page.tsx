"use client";

import {
    useCallback,
    useEffect,
    useMemo,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getAdminPrintJobCounts,
    getAdminPrintJobs,
    retryAdminPrintJob
} from "@/services/adminPrintJobsApi";

import {
    getAdminPrintingHealth
} from "@/services/adminPrintingHealthApi";

import type {
    AdminPrintJob,
    AdminPrintJobCounts,
    AdminPrintJobPageResponse,
    PrintJobStatus
} from "@/types/adminPrintJobs";

import type {
    AdminPrintingHealth,
    PrintAgentHealthStatus,
    PrinterHealthStatus
} from "@/types/adminPrintingHealth";


interface Branch {

    id: number;

    code: string;

    name: string;

    active: boolean;
}


type StatusFilter =
    | "ALL"
    | PrintJobStatus;


const API_BASE =
    process.env.NEXT_PUBLIC_API_BASE_URL
    ?? "http://localhost:8080";


const PAGE_SIZE =
    20;


const REFRESH_INTERVAL_MS =
    10_000;


const STATUS_FILTERS: {
    value: StatusFilter;
    label: string;
}[] = [
    {
        value: "ALL",
        label: "All"
    },
    {
        value: "FAILED",
        label: "Failed"
    },
    {
        value: "QUEUED",
        label: "Queued"
    },
    {
        value: "CLAIMED",
        label: "Printing"
    },
    {
        value: "PRINTED",
        label: "Printed"
    }
];


function formatStatus(
    status: PrintJobStatus
) {

    switch (
        status
    ) {

        case "QUEUED":
            return "Queued";

        case "CLAIMED":
            return "Printing";

        case "PRINTED":
            return "Printed";

        case "FAILED":
            return "Failed";

        default:
            return status;
    }
}


function statusClasses(
    status: PrintJobStatus
) {

    switch (
        status
    ) {

        case "QUEUED":
            return "border-blue-200 bg-blue-50 text-blue-800";

        case "CLAIMED":
            return "border-amber-200 bg-amber-50 text-amber-800";

        case "PRINTED":
            return "border-green-200 bg-green-50 text-green-800";

        case "FAILED":
            return "border-red-200 bg-red-50 text-red-700";

        default:
            return "border-gray-200 bg-gray-100 text-gray-700";
    }
}


function formatDateTime(
    value: string | null
) {

    if (!value) {
        return "—";
    }


    const date =
        new Date(
            value
        );


    if (
        Number.isNaN(
            date.getTime()
        )
    ) {

        return value;
    }


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            dateStyle:
                "medium",

            timeStyle:
                "short"
        }
    ).format(
        date
    );
}


function agentHealthClasses(
    status: PrintAgentHealthStatus
) {

    switch (
        status
    ) {

        case "ONLINE":
            return "border-green-200 bg-green-50 text-green-800";

        case "STALE":
            return "border-amber-200 bg-amber-50 text-amber-800";

        case "OFFLINE":
            return "border-red-200 bg-red-50 text-red-800";

        default:
            return "border-gray-200 bg-gray-100 text-gray-700";
    }
}


function printerHealthClasses(
    status: PrinterHealthStatus
) {

    switch (
        status
    ) {

        case "READY":
            return "border-green-200 bg-green-50 text-green-800";

        case "DEGRADED":
            return "border-amber-200 bg-amber-50 text-amber-800";

        case "OFFLINE":
            return "border-red-200 bg-red-50 text-red-800";

        case "NOT_CONFIGURED":
            return "border-gray-300 bg-gray-100 text-gray-700";

        case "UNKNOWN":
            return "border-blue-200 bg-blue-50 text-blue-800";

        default:
            return "border-gray-200 bg-gray-100 text-gray-700";
    }
}


function formatHealthStatus(
    status: string
) {

    return status
        .split(
            "_"
        )
        .map(
            part =>
                part.charAt(
                    0
                )
                +
                part.slice(
                    1
                )
                    .toLowerCase()
        )
        .join(
            " "
        );
}


function formatHeartbeatAge(
    seconds: number | null
) {

    if (
        seconds === null
    ) {

        return "Never seen";
    }


    if (
        seconds < 60
    ) {

        return `${seconds} sec ago`;
    }


    const minutes =
        Math.floor(
            seconds / 60
        );


    if (
        minutes < 60
    ) {

        return `${minutes} min ago`;
    }


    const hours =
        Math.floor(
            minutes / 60
        );


    return `${hours} hr ago`;
}


function CountCard({
    label,
    value,
    tone = "normal"
}: {
    label: string;
    value: number;
    tone?: "normal" | "warning" | "danger" | "success";
}) {

    const toneClasses = {

        normal:
            "border-[#eadfd6] bg-white text-[#241715]",

        warning:
            "border-amber-200 bg-amber-50 text-amber-900",

        danger:
            "border-red-200 bg-red-50 text-red-800",

        success:
            "border-green-200 bg-green-50 text-green-800"
    };


    return (
        <div
            className={`
                rounded-xl
                border
                p-4
                ${toneClasses[tone]}
            `}
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-[0.12em]
                    opacity-70
                "
            >
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-2xl
                    font-bold
                "
            >
                {value}
            </p>

        </div>
    );
}


export default function AdminPrintingPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const [
        branches,
        setBranches
    ] =
        useState<Branch[]>(
            []
        );


    const [
        selectedBranchId,
        setSelectedBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        statusFilter,
        setStatusFilter
    ] =
        useState<StatusFilter>(
            "ALL"
        );


    const [
        page,
        setPage
    ] =
        useState(
            0
        );


    const [
        result,
        setResult
    ] =
        useState<AdminPrintJobPageResponse | null>(
            null
        );


    const [
        counts,
        setCounts
    ] =
        useState<AdminPrintJobCounts | null>(
            null
        );


    const [
        health,
        setHealth
    ] =
        useState<AdminPrintingHealth | null>(
            null
        );


    const [
        loading,
        setLoading
    ] =
        useState(
            false
        );


    const [
        refreshing,
        setRefreshing
    ] =
        useState(
            false
        );


    const [
        error,
        setError
    ] =
        useState<string | null>(
            null
        );


    const [
        success,
        setSuccess
    ] =
        useState<string | null>(
            null
        );


    const [
        retryingJobId,
        setRetryingJobId
    ] =
        useState<number | null>(
            null
        );


    const [
        lastUpdated,
        setLastUpdated
    ] =
        useState<Date | null>(
            null
        );


    const canView =
        hasPermission(
            "ORDER_VIEW"
        );


    const canRetry =
        hasPermission(
            "ORDER_START_PREPARATION"
        );


    /*
     * =========================================================
     * LOAD BRANCHES
     * =========================================================
     */

    useEffect(
        () => {

            if (!profile) {
                return;
            }


            const currentProfile =
                profile;


            const controller =
                new AbortController();


            async function loadBranches() {

                try {

                    const response =
                        await fetch(
                            `${API_BASE}/api/branches`,
                            {
                                signal:
                                    controller.signal,

                                cache:
                                    "no-store"
                            }
                        );


                    if (!response.ok) {

                        throw new Error(
                            "Unable to load branches."
                        );
                    }


                    const allBranches:
                        Branch[] =
                        await response.json();


                    const activeBranches =
                        allBranches.filter(
                            branch =>
                                branch.active
                        );


                    const allowed =
                        currentProfile.roleName
                        === "OWNER_ADMIN"
                            ? activeBranches
                            : activeBranches.filter(
                                branch =>
                                    currentProfile.branchIds.includes(
                                        branch.id
                                    )
                            );


                    setBranches(
                        allowed
                    );


                    if (
                        allowed.length
                        >
                        0
                    ) {

                        setSelectedBranchId(
                            allowed[0].id
                        );
                    }

                } catch (exception) {

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
            }


            void loadBranches();


            return () => {

                controller.abort();
            };

        },
        [
            profile
        ]
    );


    /*
     * =========================================================
     * LOAD PRINT QUEUE
     * =========================================================
     */

    const loadPrintJobs =
        useCallback(
            async (
                branchId: number,
                adminAuthorization: string,
                currentPage: number,
                currentStatus: StatusFilter,
                options?: {
                    background?: boolean;
                    signal?: AbortSignal;
                }
            ) => {

                if (
                    options?.background
                ) {

                    setRefreshing(
                        true
                    );

                } else {

                    setLoading(
                        true
                    );
                }


                setError(
                    null
                );


                try {

                    const [
                        pageResult,
                        countResult,
                        healthResult
                    ] =
                        await Promise.all([
                            getAdminPrintJobs(
                                branchId,
                                adminAuthorization,
                                {
                                    status:
                                        currentStatus === "ALL"
                                            ? undefined
                                            : currentStatus,

                                    page:
                                        currentPage,

                                    size:
                                        PAGE_SIZE,

                                    signal:
                                        options?.signal
                                }
                            ),

                            getAdminPrintJobCounts(
                                branchId,
                                adminAuthorization,
                                options?.signal
                            ),

                            getAdminPrintingHealth(
                                branchId,
                                adminAuthorization,
                                "KITCHEN",
                                options?.signal
                            )
                        ]);


                    setResult(
                        pageResult
                    );


                    setCounts(
                        countResult
                    );


                    setHealth(
                        healthResult
                    );


                    setLastUpdated(
                        new Date()
                    );

                } finally {

                    if (
                        options?.background
                    ) {

                        setRefreshing(
                            false
                        );

                    } else {

                        setLoading(
                            false
                        );
                    }
                }
            },
            []
        );


    useEffect(
        () => {

            if (
                selectedBranchId === null
                ||
                authorization === null
            ) {

                return;
            }


            const branchId =
                selectedBranchId;


            const adminAuthorization =
                authorization;


            const currentPage =
                page;


            const currentStatus =
                statusFilter;


            const controller =
                new AbortController();


            async function run() {

                try {

                    await loadPrintJobs(
                        branchId,
                        adminAuthorization,
                        currentPage,
                        currentStatus,
                        {
                            signal:
                                controller.signal
                        }
                    );

                } catch (exception) {

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
                            : "Unable to load printer queue."
                    );
                }
            }


            void run();


            return () => {

                controller.abort();
            };

        },
        [
            selectedBranchId,
            authorization,
            page,
            statusFilter,
            loadPrintJobs
        ]
    );


    const refresh =
        useCallback(
            async () => {

                if (
                    selectedBranchId === null
                    ||
                    authorization === null
                    ||
                    refreshing
                ) {

                    return;
                }


                try {

                    await loadPrintJobs(
                        selectedBranchId,
                        authorization,
                        page,
                        statusFilter,
                        {
                            background:
                                true
                        }
                    );

                } catch (exception) {

                    setError(
                        exception instanceof Error
                            ? exception.message
                            : "Unable to refresh printer queue."
                    );
                }

            },
            [
                selectedBranchId,
                authorization,
                refreshing,
                page,
                statusFilter,
                loadPrintJobs
            ]
        );


    /*
     * =========================================================
     * AUTO REFRESH
     * =========================================================
     */

    useEffect(
        () => {

            if (
                selectedBranchId === null
                ||
                authorization === null
            ) {

                return;
            }


            const interval =
                window.setInterval(
                    () => {

                        void refresh();

                    },
                    REFRESH_INTERVAL_MS
                );


            return () => {

                window.clearInterval(
                    interval
                );
            };

        },
        [
            selectedBranchId,
            authorization,
            refresh
        ]
    );


    /*
     * =========================================================
     * RETRY
     * =========================================================
     */

    async function handleRetry(
        job: AdminPrintJob
    ) {

        if (
            authorization === null
            ||
            retryingJobId !== null
            ||
            job.status !== "FAILED"
        ) {

            return;
        }


        const confirmed =
            window.confirm(
                `Retry print job #${job.id} for ${job.kotNumber}?`
            );


        if (!confirmed) {
            return;
        }


        setRetryingJobId(
            job.id
        );


        setError(
            null
        );


        setSuccess(
            null
        );


        try {

            await retryAdminPrintJob(
                job.id,
                authorization
            );


            setSuccess(
                `Print job #${job.id} has been requeued.`
            );


            await refresh();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to retry print job."
            );

        } finally {

            setRetryingJobId(
                null
            );
        }
    }


    const jobs =
        useMemo(
            () =>
                result?.jobs
                ?? [],
            [
                result
            ]
        );


    if (
        profile
        &&
        !canView
    ) {

        return (
            <div
                className="
                    p-6
                "
            >
                <div
                    className="
                        rounded-2xl
                        border
                        border-red-200
                        bg-red-50
                        p-6
                        text-red-800
                    "
                >
                    You do not have permission to view printer jobs.
                </div>
            </div>
        );
    }


    return (
        <div
            className="
                px-4
                py-6
                sm:px-6
                lg:px-8
            "
        >

            <div
                className="
                    mx-auto
                    max-w-7xl
                "
            >

                <div
                    className="
                        flex
                        flex-col
                        gap-5
                        lg:flex-row
                        lg:items-start
                        lg:justify-between
                    "
                >

                    <div>

                        <p
                            className="
                                text-xs
                                font-semibold
                                uppercase
                                tracking-[0.16em]
                                text-[#c88a20]
                            "
                        >
                            Kitchen printing
                        </p>


                        <h1
                            className="
                                mt-2
                                text-3xl
                                font-bold
                                tracking-tight
                                text-[#241715]
                                sm:text-4xl
                            "
                        >
                            Printer Queue
                        </h1>


                        <p
                            className="
                                mt-3
                                max-w-3xl
                                text-sm
                                leading-6
                                text-[#756763]
                            "
                        >
                            Monitor automatic KOT printing, failures and retries.
                        </p>

                    </div>


                    <div
                        className="
                            w-full
                            lg:w-80
                        "
                    >

                        <label
                            htmlFor="printing-branch"
                            className="
                                mb-2
                                block
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >
                            Branch
                        </label>


                        <select
                            id="printing-branch"
                            value={
                                selectedBranchId
                                ?? ""
                            }
                            onChange={
                                event => {

                                    setSelectedBranchId(
                                        Number(
                                            event.target.value
                                        )
                                    );


                                    setPage(
                                        0
                                    );


                                    setSuccess(
                                        null
                                    );
                                }
                            }
                            className="
                                min-h-12
                                w-full
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                px-4
                                text-sm
                                font-semibold
                                text-[#241715]
                            "
                        >

                            {
                                branches.map(
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


                <section
                    className="
                        mt-6
                        overflow-hidden
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                    "
                >

                    <div
                        className="
                            border-b
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-5
                            py-4
                        "
                    >

                        <div
                            className="
                                flex
                                flex-col
                                gap-2
                                sm:flex-row
                                sm:items-center
                                sm:justify-between
                            "
                        >

                            <div>

                                <p
                                    className="
                                        text-xs
                                        font-semibold
                                        uppercase
                                        tracking-[0.14em]
                                        text-[#c88a20]
                                    "
                                >
                                    System monitoring
                                </p>


                                <h2
                                    className="
                                        mt-1
                                        text-xl
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Printing Health
                                </h2>

                            </div>


                            <p
                                className="
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                Health refreshes with the printer queue every 10 seconds.
                            </p>

                        </div>

                    </div>


                    <div
                        className="
                            grid
                            gap-4
                            p-5

                            lg:grid-cols-2
                        "
                    >

                        <div
                            className="
                                rounded-xl
                                border
                                border-[#eadfd6]
                                p-4
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-wrap
                                    items-center
                                    justify-between
                                    gap-3
                                "
                            >

                                <div>

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-wide
                                            text-[#756763]
                                        "
                                    >
                                        Print Agent
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.agent.agentId
                                            ?? "No agent heartbeat"
                                        }
                                    </p>

                                </div>


                                <span
                                    className={`
                                        rounded-full
                                        border
                                        px-3
                                        py-1.5
                                        text-xs
                                        font-bold

                                        ${
                                            health
                                                ? agentHealthClasses(
                                                    health.agent.status
                                                )
                                                : "border-gray-200 bg-gray-100 text-gray-700"
                                        }
                                    `}
                                >
                                    {
                                        health
                                            ? formatHealthStatus(
                                                health.agent.status
                                            )
                                            : "Loading"
                                    }
                                </span>

                            </div>


                            <dl
                                className="
                                    mt-4
                                    grid
                                    gap-3
                                    text-sm
                                    sm:grid-cols-2
                                "
                            >

                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Last heartbeat
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health
                                                ? formatHeartbeatAge(
                                                    health.agent.secondsSinceHeartbeat
                                                )
                                                : "—"
                                        }
                                    </dd>

                                </div>


                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Heartbeats received
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.agent.heartbeatCount
                                            ?? 0
                                        }
                                    </dd>

                                </div>


                                <div
                                    className="
                                        sm:col-span-2
                                    "
                                >

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Last seen
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatDateTime(
                                                health?.agent.lastSeenAt
                                                ?? null
                                            )
                                        }
                                    </dd>

                                </div>

                            </dl>

                        </div>


                        <div
                            className="
                                rounded-xl
                                border
                                border-[#eadfd6]
                                p-4
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-wrap
                                    items-center
                                    justify-between
                                    gap-3
                                "
                            >

                                <div>

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-wide
                                            text-[#756763]
                                        "
                                    >
                                        Kitchen Printer
                                    </p>


                                    <p
                                        className="
                                            mt-2
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.printer.name
                                            ?? "No active printer configured"
                                        }
                                    </p>

                                </div>


                                <span
                                    className={`
                                        rounded-full
                                        border
                                        px-3
                                        py-1.5
                                        text-xs
                                        font-bold

                                        ${
                                            health
                                                ? printerHealthClasses(
                                                    health.printer.status
                                                )
                                                : "border-gray-200 bg-gray-100 text-gray-700"
                                        }
                                    `}
                                >
                                    {
                                        health
                                            ? formatHealthStatus(
                                                health.printer.status
                                            )
                                            : "Loading"
                                    }
                                </span>

                            </div>


                            <dl
                                className="
                                    mt-4
                                    grid
                                    gap-3
                                    text-sm
                                    sm:grid-cols-2
                                "
                            >

                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Station
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.station
                                            ?? "KITCHEN"
                                        }
                                    </dd>

                                </div>


                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Connection
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.printer.host
                                            &&
                                            health.printer.port
                                                ? `${health.printer.host}:${health.printer.port}`
                                                : "—"
                                        }
                                    </dd>

                                </div>


                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Protocol
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.printer.protocol
                                            ?? "—"
                                        }
                                    </dd>

                                </div>


                                <div>

                                    <dt
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Printer code
                                    </dt>


                                    <dd
                                        className="
                                            mt-1
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            health?.printer.code
                                            ?? "—"
                                        }
                                    </dd>

                                </div>

                            </dl>

                        </div>

                    </div>


                    <div
                        className="
                            grid
                            gap-3
                            border-t
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            p-5

                            sm:grid-cols-2
                            lg:grid-cols-4
                        "
                    >

                        <CountCard
                            label="Health queued"
                            value={
                                health?.queue.queued
                                ?? 0
                            }
                        />


                        <CountCard
                            label="Health printing"
                            value={
                                health?.queue.claimed
                                ?? 0
                            }
                            tone="warning"
                        />


                        <CountCard
                            label="Health failed"
                            value={
                                health?.queue.failed
                                ?? 0
                            }
                            tone="danger"
                        />


                        <CountCard
                            label="Needs attention"
                            value={
                                health?.queue.permanentlyFailed
                                ?? 0
                            }
                            tone="danger"
                        />

                    </div>


                    <div
                        className="
                            grid
                            gap-4
                            border-t
                            border-[#eadfd6]
                            p-5

                            lg:grid-cols-2
                        "
                    >

                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#756763]
                                "
                            >
                                Last successful print
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                {
                                    formatDateTime(
                                        health?.activity.lastSuccessfulPrintAt
                                        ?? null
                                    )
                                }
                            </p>


                            {
                                health?.activity.lastSuccessfulPrintJobId
                                && (

                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Print job #{
                                            health.activity.lastSuccessfulPrintJobId
                                        }
                                    </p>

                                )
                            }

                        </div>


                        <div>

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-wide
                                    text-[#756763]
                                "
                            >
                                Last failure
                            </p>


                            <p
                                className="
                                    mt-2
                                    text-sm
                                    font-semibold
                                    text-[#241715]
                                "
                            >
                                {
                                    formatDateTime(
                                        health?.activity.lastFailureAt
                                        ?? null
                                    )
                                }
                            </p>


                            {
                                health?.activity.lastFailureCode
                                && (

                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            font-semibold
                                            text-red-700
                                        "
                                    >
                                        {
                                            health.activity.lastFailureCode
                                        }
                                    </p>

                                )
                            }


                            {
                                health?.activity.lastFailureMessage
                                && (

                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            leading-5
                                            text-[#756763]
                                        "
                                    >
                                        {
                                            health.activity.lastFailureMessage
                                        }
                                    </p>

                                )
                            }

                        </div>

                    </div>

                </section>


                <div
                    className="
                        mt-6
                        grid
                        gap-3
                        sm:grid-cols-2
                        lg:grid-cols-5
                    "
                >

                    <CountCard
                        label="Queued"
                        value={
                            counts?.queued
                            ?? 0
                        }
                    />


                    <CountCard
                        label="Printing"
                        value={
                            counts?.claimed
                            ?? 0
                        }
                        tone="warning"
                    />


                    <CountCard
                        label="Failed"
                        value={
                            counts?.failed
                            ?? 0
                        }
                        tone="danger"
                    />


                    <CountCard
                        label="Needs attention"
                        value={
                            counts?.permanentlyFailed
                            ?? 0
                        }
                        tone="danger"
                    />


                    <CountCard
                        label="Printed"
                        value={
                            counts?.printed
                            ?? 0
                        }
                        tone="success"
                    />

                </div>


                {
                    error
                    && (

                        <div
                            role="alert"
                            className="
                                mt-5
                                rounded-xl
                                border
                                border-red-200
                                bg-red-50
                                px-4
                                py-3
                                text-sm
                                font-medium
                                text-red-700
                            "
                        >
                            {error}
                        </div>

                    )
                }


                {
                    success
                    && (

                        <div
                            role="status"
                            className="
                                mt-5
                                rounded-xl
                                border
                                border-green-200
                                bg-green-50
                                px-4
                                py-3
                                text-sm
                                font-medium
                                text-green-800
                            "
                        >
                            {success}
                        </div>

                    )
                }


                <section
                    className="
                        mt-6
                        overflow-hidden
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                    "
                >

                    <div
                        className="
                            flex
                            flex-col
                            gap-4
                            border-b
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            p-5
                            lg:flex-row
                            lg:items-center
                            lg:justify-between
                        "
                    >

                        <div
                            className="
                                flex
                                flex-wrap
                                gap-2
                            "
                        >

                            {
                                STATUS_FILTERS.map(
                                    filter => (

                                        <button
                                            key={
                                                filter.value
                                            }
                                            type="button"
                                            onClick={
                                                () => {

                                                    setStatusFilter(
                                                        filter.value
                                                    );


                                                    setPage(
                                                        0
                                                    );
                                                }
                                            }
                                            className={`
                                                min-h-9
                                                rounded-lg
                                                px-4
                                                text-sm
                                                font-semibold

                                                ${
                                                    statusFilter
                                                    === filter.value
                                                        ? "bg-[#7a1625] text-white"
                                                        : "border border-[#eadfd6] bg-white text-[#7a1625]"
                                                }
                                            `}
                                        >
                                            {filter.label}
                                        </button>

                                    )
                                )
                            }

                        </div>


                        <div
                            className="
                                flex
                                items-center
                                gap-3
                            "
                        >

                            <span
                                className="
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                Updated{" "}
                                {
                                    lastUpdated
                                        ? lastUpdated.toLocaleTimeString(
                                            "en-IN"
                                        )
                                        : "—"
                                }
                            </span>


                            <button
                                type="button"
                                disabled={
                                    refreshing
                                    ||
                                    loading
                                }
                                onClick={
                                    () => {

                                        void refresh();
                                    }
                                }
                                className="
                                    min-h-9
                                    rounded-lg
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    px-4
                                    text-xs
                                    font-semibold
                                    text-[#7a1625]
                                    disabled:opacity-50
                                "
                            >
                                {
                                    refreshing
                                        ? "Refreshing..."
                                        : "Refresh"
                                }
                            </button>

                        </div>

                    </div>


                    {
                        loading
                            ? (

                                <div
                                    className="
                                        p-8
                                        text-center
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    Loading printer jobs...
                                </div>

                            )
                            : jobs.length === 0
                                ? (

                                    <div
                                        className="
                                            p-8
                                            text-center
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        No print jobs found.
                                    </div>

                                )
                                : (

                                    <div
                                        className="
                                            divide-y
                                            divide-[#eadfd6]
                                        "
                                    >

                                        {
                                            jobs.map(
                                                job => {

                                                    const permanentFailure =
                                                        job.status === "FAILED"
                                                        &&
                                                        job.attemptCount
                                                        >=
                                                        job.maxAttempts;


                                                    return (
                                                        <div
                                                            key={
                                                                job.id
                                                            }
                                                            className="
                                                                p-5
                                                            "
                                                        >

                                                            <div
                                                                className="
                                                                    flex
                                                                    flex-col
                                                                    gap-4
                                                                    lg:flex-row
                                                                    lg:items-start
                                                                    lg:justify-between
                                                                "
                                                            >

                                                                <div>

                                                                    <div
                                                                        className="
                                                                            flex
                                                                            flex-wrap
                                                                            items-center
                                                                            gap-2
                                                                        "
                                                                    >

                                                                        <span
                                                                            className={`
                                                                                rounded-full
                                                                                border
                                                                                px-3
                                                                                py-1
                                                                                text-xs
                                                                                font-semibold
                                                                                ${statusClasses(
                                                                                    job.status
                                                                                )}
                                                                            `}
                                                                        >
                                                                            {
                                                                                formatStatus(
                                                                                    job.status
                                                                                )
                                                                            }
                                                                        </span>


                                                                        {
                                                                            permanentFailure
                                                                            && (

                                                                                <span
                                                                                    className="
                                                                                        rounded-full
                                                                                        bg-red-100
                                                                                        px-3
                                                                                        py-1
                                                                                        text-xs
                                                                                        font-bold
                                                                                        text-red-800
                                                                                    "
                                                                                >
                                                                                    Needs attention
                                                                                </span>

                                                                            )
                                                                        }

                                                                    </div>


                                                                    <h2
                                                                        className="
                                                                            mt-3
                                                                            text-lg
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {job.kotNumber}
                                                                    </h2>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-sm
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        Order {job.orderNumber}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-sm
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        Printer:{" "}
                                                                        <span
                                                                            className="
                                                                                font-semibold
                                                                                text-[#241715]
                                                                            "
                                                                        >
                                                                            {
                                                                                job.printerName
                                                                                ?? "Not assigned"
                                                                            }
                                                                        </span>
                                                                    </p>

                                                                </div>


                                                                <div
                                                                    className="
                                                                        lg:text-right
                                                                    "
                                                                >

                                                                    <p
                                                                        className="
                                                                            text-sm
                                                                            font-semibold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        Attempt{" "}
                                                                        {job.attemptCount}
                                                                        {" / "}
                                                                        {job.maxAttempts}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        Queued{" "}
                                                                        {
                                                                            formatDateTime(
                                                                                job.queuedAt
                                                                            )
                                                                        }
                                                                    </p>


                                                                    {
                                                                        job.status === "FAILED"
                                                                        &&
                                                                        canRetry
                                                                        && (

                                                                            <button
                                                                                type="button"
                                                                                disabled={
                                                                                    retryingJobId
                                                                                    !== null
                                                                                }
                                                                                onClick={
                                                                                    () => {

                                                                                        void handleRetry(
                                                                                            job
                                                                                        );
                                                                                    }
                                                                                }
                                                                                className="
                                                                                    mt-4
                                                                                    min-h-10
                                                                                    rounded-xl
                                                                                    bg-[#7a1625]
                                                                                    px-4
                                                                                    text-sm
                                                                                    font-semibold
                                                                                    text-white
                                                                                    hover:bg-[#5d0f1b]
                                                                                    disabled:opacity-50
                                                                                "
                                                                            >
                                                                                {
                                                                                    retryingJobId
                                                                                    === job.id
                                                                                        ? "Retrying..."
                                                                                        : "Retry"
                                                                                }
                                                                            </button>

                                                                        )
                                                                    }

                                                                </div>

                                                            </div>


                                                            {
                                                                job.status === "FAILED"
                                                                && (

                                                                    <div
                                                                        className="
                                                                            mt-4
                                                                            rounded-xl
                                                                            border
                                                                            border-red-100
                                                                            bg-red-50
                                                                            p-4
                                                                        "
                                                                    >

                                                                        <p
                                                                            className="
                                                                                text-sm
                                                                                font-semibold
                                                                                text-red-800
                                                                            "
                                                                        >
                                                                            {
                                                                                job.lastErrorCode
                                                                                ?? "PRINT_FAILED"
                                                                            }
                                                                        </p>


                                                                        {
                                                                            job.lastErrorMessage
                                                                            && (

                                                                                <p
                                                                                    className="
                                                                                        mt-1
                                                                                        text-sm
                                                                                        text-red-700
                                                                                    "
                                                                                >
                                                                                    {job.lastErrorMessage}
                                                                                </p>

                                                                            )
                                                                        }


                                                                        {
                                                                            job.nextAttemptAt
                                                                            && (

                                                                                <p
                                                                                    className="
                                                                                        mt-2
                                                                                        text-xs
                                                                                        text-red-700
                                                                                    "
                                                                                >
                                                                                    Automatic retry:{" "}
                                                                                    {
                                                                                        formatDateTime(
                                                                                            job.nextAttemptAt
                                                                                        )
                                                                                    }
                                                                                </p>

                                                                            )
                                                                        }

                                                                    </div>

                                                                )
                                                            }

                                                        </div>
                                                    );
                                                }
                                            )
                                        }

                                    </div>

                                )
                    }


                    {
                        result
                        &&
                        result.totalPages > 1
                        && (

                            <div
                                className="
                                    flex
                                    items-center
                                    justify-between
                                    border-t
                                    border-[#eadfd6]
                                    bg-[#fffaf3]
                                    px-5
                                    py-4
                                "
                            >

                                <button
                                    type="button"
                                    disabled={
                                        page <= 0
                                    }
                                    onClick={
                                        () =>
                                            setPage(
                                                current =>
                                                    Math.max(
                                                        0,
                                                        current - 1
                                                    )
                                            )
                                    }
                                    className="
                                        rounded-lg
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-4
                                        py-2
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                        disabled:opacity-40
                                    "
                                >
                                    Previous
                                </button>


                                <span
                                    className="
                                        text-sm
                                        text-[#756763]
                                    "
                                >
                                    Page{" "}
                                    {page + 1}
                                    {" of "}
                                    {result.totalPages}
                                </span>


                                <button
                                    type="button"
                                    disabled={
                                        page + 1
                                        >=
                                        result.totalPages
                                    }
                                    onClick={
                                        () =>
                                            setPage(
                                                current =>
                                                    current + 1
                                            )
                                    }
                                    className="
                                        rounded-lg
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-4
                                        py-2
                                        text-sm
                                        font-semibold
                                        text-[#7a1625]
                                        disabled:opacity-40
                                    "
                                >
                                    Next
                                </button>

                            </div>

                        )
                    }

                </section>

            </div>

        </div>
    );
}