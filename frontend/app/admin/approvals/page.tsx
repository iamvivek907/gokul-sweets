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
    approveAdminApproval,
    getAdminApprovalCounts,
    getAdminApprovalDetail,
    getAdminApprovalOptions,
    getAdminApprovals,
    rejectAdminApproval,
    sendBackAdminApproval
} from "@/services/adminApprovalsApi";

import type {
    AdminApprovalCounts,
    AdminApprovalDetail,
    AdminApprovalOptions,
    AdminApprovalRequest,
    ApprovalRequestStatus,
    ApprovalRequestType,
    SpringPage
} from "@/types/adminApprovals";


const EMPTY_PAGE: SpringPage<AdminApprovalRequest> = {

    content: [],

    totalElements: 0,

    totalPages: 0,

    number: 0,

    size: 25,

    first: true,

    last: true
};


const EMPTY_COUNTS: AdminApprovalCounts = {

    pending: 0,

    sentBack: 0,

    approved: 0,

    rejected: 0,

    cancelled: 0,

    total: 0
};


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
            dateStyle: "medium",
            timeStyle: "short"
        }
    ).format(
        date
    );
}


function pretty(
    value: string
) {

    return value
        .toLowerCase()
        .split("_")
        .map(
            part =>
                part.charAt(0)
                    .toUpperCase()
                +
                part.slice(
                    1
                )
        )
        .join(
            " "
        );
}


function statusClass(
    status: ApprovalRequestStatus
) {

    switch (status) {

        case "PENDING":
            return "bg-amber-50 text-amber-800 border-amber-200";

        case "APPROVED":
            return "bg-green-50 text-green-800 border-green-200";

        case "REJECTED":
            return "bg-red-50 text-red-700 border-red-200";

        case "SENT_BACK":
            return "bg-blue-50 text-blue-800 border-blue-200";

        case "CANCELLED":
            return "bg-gray-100 text-gray-700 border-gray-200";
    }
}


export default function AdminApprovalsPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const canView =
        hasPermission(
            "APPROVAL_VIEW"
        );


    const canManage =
        hasPermission(
            "APPROVAL_MANAGE"
        );


    const [
        options,
        setOptions
    ] =
        useState<AdminApprovalOptions | null>(
            null
        );


    const [
        approvals,
        setApprovals
    ] =
        useState<
            SpringPage<AdminApprovalRequest>
        >(
            EMPTY_PAGE
        );


    const [
        counts,
        setCounts
    ] =
        useState<AdminApprovalCounts>(
            EMPTY_COUNTS
        );


    const [
        branchId,
        setBranchId
    ] =
        useState<number | null>(
            null
        );


    const [
        requestType,
        setRequestType
    ] =
        useState<ApprovalRequestType | null>(
            null
        );


    const [
        status,
        setStatus
    ] =
        useState<ApprovalRequestStatus | null>(
            "PENDING"
        );


    const [
        page,
        setPage
    ] =
        useState(
            0
        );


    const [
        loading,
        setLoading
    ] =
        useState(
            true
        );


    const [
        detailLoading,
        setDetailLoading
    ] =
        useState(
            false
        );


    const [
        selectedId,
        setSelectedId
    ] =
        useState<number | null>(
            null
        );


    const [
        detail,
        setDetail
    ] =
        useState<AdminApprovalDetail | null>(
            null
        );


    const [
        actionMode,
        setActionMode
    ] =
        useState<
            "approve"
            | "reject"
            | "send-back"
            | null
        >(
            null
        );


    const [
        actionComment,
        setActionComment
    ] =
        useState(
            ""
        );


    const [
        actionSaving,
        setActionSaving
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


    const filters =
        useMemo(
            () => ({
                branchId,
                type:
                    requestType
            }),
            [
                branchId,
                requestType
            ]
        );


    const loadInbox =
        useCallback(
            async (
                adminAuthorization: string,
                requestedPage: number,
                signal?: AbortSignal
            ) => {

                const [
                    pageResult,
                    countResult
                ] =
                    await Promise.all([
                        getAdminApprovals(
                            adminAuthorization,
                            {
                                branchId,
                                type:
                                    requestType,
                                status,
                                page:
                                    requestedPage,
                                size:
                                    25
                            },
                            signal
                        ),
                        getAdminApprovalCounts(
                            adminAuthorization,
                            filters,
                            signal
                        )
                    ]);


                setApprovals(
                    pageResult
                );


                setCounts(
                    countResult
                );


                setLoading(
                    false
                );
            },
            [
                branchId,
                requestType,
                status,
                filters
            ]
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
                getAdminApprovalOptions(
                    authorization,
                    controller.signal
                ),
                getAdminApprovals(
                    authorization,
                    {
                        branchId,
                        type:
                            requestType,
                        status,
                        page:
                            0,
                        size:
                            25
                    },
                    controller.signal
                ),
                getAdminApprovalCounts(
                    authorization,
                    {
                        branchId,
                        type:
                            requestType
                    },
                    controller.signal
                )
            ])
                .then(
                    ([
                        optionsResult,
                        pageResult,
                        countResult
                    ]) => {

                        setOptions(
                            optionsResult
                        );


                        setApprovals(
                            pageResult
                        );


                        setCounts(
                            countResult
                        );


                        setPage(
                            0
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
                                : "Unable to load approvals."
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
            branchId,
            requestType,
            status
        ]
    );


    async function refreshCurrentPage() {

        if (
            authorization === null
        ) {

            return;
        }


        setLoading(
            true
        );


        setError(
            null
        );


        try {

            await loadInbox(
                authorization,
                page
            );

        } catch (exception) {

            setLoading(
                false
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to refresh approvals."
            );
        }
    }


    async function openDetail(
        approvalRequestId: number
    ) {

        if (
            authorization === null
        ) {

            return;
        }


        setSelectedId(
            approvalRequestId
        );


        setDetailLoading(
            true
        );


        setError(
            null
        );


        setActionMode(
            null
        );


        setActionComment(
            ""
        );


        try {

            const result =
                await getAdminApprovalDetail(
                    approvalRequestId,
                    authorization
                );


            setDetail(
                result
            );

        } catch (exception) {

            setDetail(
                null
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to load approval details."
            );

        } finally {

            setDetailLoading(
                false
            );
        }
    }


    async function changePage(
        nextPage: number
    ) {

        if (
            authorization === null
            ||
            loading
            ||
            nextPage < 0
            ||
            (
                approvals.totalPages > 0
                &&
                nextPage >= approvals.totalPages
            )
        ) {

            return;
        }


        setLoading(
            true
        );


        setError(
            null
        );


        try {

            await loadInbox(
                authorization,
                nextPage
            );


            setPage(
                nextPage
            );

        } catch (exception) {

            setLoading(
                false
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to load approval page."
            );
        }
    }


    async function submitAction() {

        if (
            authorization === null
            ||
            detail === null
            ||
            actionMode === null
            ||
            actionSaving
        ) {

            return;
        }


        const comment =
            actionComment.trim();


        if (
            (
                actionMode === "reject"
                ||
                actionMode === "send-back"
            )
            &&
            !comment
        ) {

            setError(
                actionMode === "reject"
                    ? "Enter a reason before rejecting the request."
                    : "Enter the correction required before sending the request back."
            );


            return;
        }


        setActionSaving(
            true
        );


        setError(
            null
        );


        setSuccess(
            null
        );


        try {

            const approvalId =
                detail.request.id;


            let updated:
                AdminApprovalRequest;


            if (
                actionMode === "approve"
            ) {

                updated =
                    await approveAdminApproval(
                        approvalId,
                        comment
                            || null,
                        authorization
                    );

            } else if (
                actionMode === "reject"
            ) {

                updated =
                    await rejectAdminApproval(
                        approvalId,
                        comment,
                        authorization
                    );

            } else {

                updated =
                    await sendBackAdminApproval(
                        approvalId,
                        comment,
                        authorization
                    );
            }


            const refreshedDetail =
                await getAdminApprovalDetail(
                    approvalId,
                    authorization
                );


            setDetail(
                refreshedDetail
            );


            setActionMode(
                null
            );


            setActionComment(
                ""
            );


            setSuccess(
                `${updated.requestNumber} is now ${pretty(updated.status)}.`
            );


            await loadInbox(
                authorization,
                page
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to action approval request."
            );

        } finally {

            setActionSaving(
                false
            );
        }
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
                    You do not have permission to view the approval inbox.
                </div>

            </div>
        );
    }


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

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
                            Workforce
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
                            Approval Inbox
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
                            Review staff leave, attendance and payment requests from one queue.
                        </p>

                    </div>


                    <button
                        type="button"
                        disabled={
                            loading
                        }
                        onClick={
                            () => {

                                void refreshCurrentPage();
                            }
                        }
                        className="
                            min-h-11
                            rounded-xl
                            border
                            border-[#eadfd6]
                            bg-white
                            px-5
                            text-sm
                            font-semibold
                            text-[#7a1625]

                            disabled:opacity-50
                        "
                    >
                        {
                            loading
                                ? "Refreshing..."
                                : "Refresh"
                        }
                    </button>

                </div>


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
                        label="Pending"
                        value={
                            counts.pending
                        }
                        active={
                            status === "PENDING"
                        }
                        onClick={
                            () =>
                                setStatus(
                                    "PENDING"
                                )
                        }
                    />


                    <CountCard
                        label="Sent Back"
                        value={
                            counts.sentBack
                        }
                        active={
                            status === "SENT_BACK"
                        }
                        onClick={
                            () =>
                                setStatus(
                                    "SENT_BACK"
                                )
                        }
                    />


                    <CountCard
                        label="Approved"
                        value={
                            counts.approved
                        }
                        active={
                            status === "APPROVED"
                        }
                        onClick={
                            () =>
                                setStatus(
                                    "APPROVED"
                                )
                        }
                    />


                    <CountCard
                        label="Rejected"
                        value={
                            counts.rejected
                        }
                        active={
                            status === "REJECTED"
                        }
                        onClick={
                            () =>
                                setStatus(
                                    "REJECTED"
                                )
                        }
                    />


                    <CountCard
                        label="All"
                        value={
                            counts.total
                        }
                        active={
                            status === null
                        }
                        onClick={
                            () =>
                                setStatus(
                                    null
                                )
                        }
                    />

                </div>


                <div
                    className="
                        mt-6
                        grid
                        gap-3
                        rounded-2xl
                        border
                        border-[#eadfd6]
                        bg-white
                        p-4
                        md:grid-cols-3
                    "
                >

                    <select
                        value={
                            branchId
                            ?? ""
                        }
                        onChange={
                            event => {

                                const value =
                                    event.target.value;


                                setBranchId(
                                    value
                                        ? Number(
                                            value
                                        )
                                        : null
                                );
                            }
                        }
                        className={INPUT_CLASS}
                    >

                        <option value="">
                            All accessible branches
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
                                        {
                                            branch.active
                                                ? ""
                                                : " (Inactive)"
                                        }
                                    </option>

                                )
                            )
                        }

                    </select>


                    <select
                        value={
                            requestType
                            ?? ""
                        }
                        onChange={
                            event =>
                                setRequestType(
                                    event.target.value
                                        ? event.target.value as ApprovalRequestType
                                        : null
                                )
                        }
                        className={INPUT_CLASS}
                    >

                        <option value="">
                            All request types
                        </option>


                        {
                            options?.requestTypes.map(
                                type => (

                                    <option
                                        key={
                                            type
                                        }
                                        value={
                                            type
                                        }
                                    >
                                        {pretty(type)}
                                    </option>

                                )
                            )
                        }

                    </select>


                    <select
                        value={
                            status
                            ?? ""
                        }
                        onChange={
                            event =>
                                setStatus(
                                    event.target.value
                                        ? event.target.value as ApprovalRequestStatus
                                        : null
                                )
                        }
                        className={INPUT_CLASS}
                    >

                        <option value="">
                            All statuses
                        </option>


                        {
                            options?.statuses.map(
                                item => (

                                    <option
                                        key={
                                            item
                                        }
                                        value={
                                            item
                                        }
                                    >
                                        {pretty(item)}
                                    </option>

                                )
                            )
                        }

                    </select>

                </div>


                {
                    error
                    && (

                        <div
                            role="alert"
                            className="
                                mt-6
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
                                mt-6
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
                            ✓ {success}
                        </div>

                    )
                }


                <div
                    className="
                        mt-6
                        grid
                        gap-6
                        xl:grid-cols-[minmax(0,1fr)_420px]
                    "
                >

                    <section
                        className="
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
                                items-center
                                justify-between
                                gap-3
                                border-b
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                px-5
                                py-4
                            "
                        >

                            <div>

                                <h2
                                    className="
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    Requests
                                </h2>


                                <p
                                    className="
                                        mt-1
                                        text-xs
                                        text-[#756763]
                                    "
                                >
                                    {approvals.totalElements} matching requests
                                </p>

                            </div>


                            <span
                                className="
                                    text-xs
                                    font-semibold
                                    text-[#756763]
                                "
                            >
                                Page {
                                    approvals.totalPages === 0
                                        ? 0
                                        : approvals.number + 1
                                } of {approvals.totalPages}
                            </span>

                        </div>


                        {
                            loading
                            &&
                            approvals.content.length === 0
                                ? (

                                    <div className="p-8 text-sm text-[#756763]">
                                        Loading approvals...
                                    </div>

                                )
                                : approvals.content.length === 0
                                    ? (

                                        <div className="p-8 text-sm text-[#756763]">
                                            No approval requests match the current filters.
                                        </div>

                                    )
                                    : (

                                        <div className="divide-y divide-[#eadfd6]">

                                            {
                                                approvals.content.map(
                                                    request => (

                                                        <button
                                                            key={
                                                                request.id
                                                            }
                                                            type="button"
                                                            onClick={
                                                                () => {

                                                                    void openDetail(
                                                                        request.id
                                                                    );
                                                                }
                                                            }
                                                            className={`
                                                                w-full
                                                                px-5
                                                                py-4
                                                                text-left
                                                                transition

                                                                ${
                                                                    selectedId
                                                                    === request.id
                                                                        ? "bg-[#fff1e9]"
                                                                        : "bg-white hover:bg-[#fffaf3]"
                                                                }
                                                            `}
                                                        >

                                                            <div
                                                                className="
                                                                    flex
                                                                    flex-col
                                                                    gap-3
                                                                    sm:flex-row
                                                                    sm:items-start
                                                                    sm:justify-between
                                                                "
                                                            >

                                                                <div className="min-w-0">

                                                                    <div
                                                                        className="
                                                                            flex
                                                                            flex-wrap
                                                                            items-center
                                                                            gap-2
                                                                        "
                                                                    >

                                                                        <span
                                                                            className="
                                                                                text-sm
                                                                                font-bold
                                                                                text-[#241715]
                                                                            "
                                                                        >
                                                                            {request.title}
                                                                        </span>


                                                                        <span
                                                                            className="
                                                                                rounded-full
                                                                                bg-[#fff8e8]
                                                                                px-2.5
                                                                                py-1
                                                                                text-xs
                                                                                font-semibold
                                                                                text-[#8b6117]
                                                                            "
                                                                        >
                                                                            {pretty(request.requestType)}
                                                                        </span>

                                                                    </div>


                                                                    <p
                                                                        className="
                                                                            mt-2
                                                                            text-sm
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {request.staffName} · @{request.staffUsername}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {request.branchName} · {request.requestNumber} · {formatDateTime(request.submittedAt)}
                                                                    </p>

                                                                </div>


                                                                <span
                                                                    className={`
                                                                        inline-flex
                                                                        w-fit
                                                                        shrink-0
                                                                        rounded-full
                                                                        border
                                                                        px-2.5
                                                                        py-1
                                                                        text-xs
                                                                        font-semibold
                                                                        ${statusClass(request.status)}
                                                                    `}
                                                                >
                                                                    {pretty(request.status)}
                                                                </span>

                                                            </div>

                                                        </button>

                                                    )
                                                )
                                            }

                                        </div>

                                    )
                        }


                        <div
                            className="
                                flex
                                items-center
                                justify-between
                                gap-3
                                border-t
                                border-[#eadfd6]
                                px-5
                                py-4
                            "
                        >

                            <button
                                type="button"
                                disabled={
                                    approvals.first
                                    ||
                                    loading
                                }
                                onClick={
                                    () => {

                                        void changePage(
                                            page - 1
                                        );
                                    }
                                }
                                className={SECONDARY_BUTTON_CLASS}
                            >
                                Previous
                            </button>


                            <button
                                type="button"
                                disabled={
                                    approvals.last
                                    ||
                                    loading
                                }
                                onClick={
                                    () => {

                                        void changePage(
                                            page + 1
                                        );
                                    }
                                }
                                className={SECONDARY_BUTTON_CLASS}
                            >
                                Next
                            </button>

                        </div>

                    </section>


                    <aside
                        className="
                            h-fit
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            xl:sticky
                            xl:top-6
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

                            <h2 className="font-bold text-[#241715]">
                                Request Detail
                            </h2>

                        </div>


                        {
                            detailLoading
                                ? (

                                    <div className="p-6 text-sm text-[#756763]">
                                        Loading request...
                                    </div>

                                )
                                : detail === null
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            Select a request to review its details and approval history.
                                        </div>

                                    )
                                    : (

                                        <div className="p-5">

                                            <div
                                                className="
                                                    flex
                                                    items-start
                                                    justify-between
                                                    gap-3
                                                "
                                            >

                                                <div>

                                                    <p
                                                        className="
                                                            text-lg
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {detail.request.title}
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        {detail.request.requestNumber}
                                                    </p>

                                                </div>


                                                <span
                                                    className={`
                                                        rounded-full
                                                        border
                                                        px-2.5
                                                        py-1
                                                        text-xs
                                                        font-semibold
                                                        ${statusClass(detail.request.status)}
                                                    `}
                                                >
                                                    {pretty(detail.request.status)}
                                                </span>

                                            </div>


                                            <dl
                                                className="
                                                    mt-5
                                                    grid
                                                    gap-3
                                                    text-sm
                                                "
                                            >

                                                <DetailRow
                                                    label="Staff"
                                                    value={`${detail.request.staffName} (@${detail.request.staffUsername})`}
                                                />


                                                <DetailRow
                                                    label="Branch"
                                                    value={detail.request.branchName}
                                                />


                                                <DetailRow
                                                    label="Type"
                                                    value={pretty(detail.request.requestType)}
                                                />


                                                <DetailRow
                                                    label="Submitted"
                                                    value={formatDateTime(detail.request.submittedAt)}
                                                />


                                                <DetailRow
                                                    label="Workflow version"
                                                    value={String(detail.request.workflowVersion)}
                                                />

                                            </dl>


                                            {
                                                detail.request.summary
                                                && (

                                                    <div
                                                        className="
                                                            mt-5
                                                            rounded-xl
                                                            border
                                                            border-[#eadfd6]
                                                            bg-[#fffaf3]
                                                            p-4
                                                        "
                                                    >

                                                        <p
                                                            className="
                                                                text-xs
                                                                font-semibold
                                                                uppercase
                                                                tracking-[0.12em]
                                                                text-[#756763]
                                                            "
                                                        >
                                                            Summary
                                                        </p>


                                                        <p
                                                            className="
                                                                mt-2
                                                                whitespace-pre-wrap
                                                                text-sm
                                                                leading-6
                                                                text-[#241715]
                                                            "
                                                        >
                                                            {detail.request.summary}
                                                        </p>

                                                    </div>

                                                )
                                            }


                                            {
                                                canManage
                                                &&
                                                detail.request.status === "PENDING"
                                                && (

                                                    <div className="mt-5">

                                                        <div
                                                            className="
                                                                grid
                                                                grid-cols-3
                                                                gap-2
                                                            "
                                                        >

                                                            <button
                                                                type="button"
                                                                onClick={
                                                                    () =>
                                                                        setActionMode(
                                                                            "approve"
                                                                        )
                                                                }
                                                                className="
                                                                    rounded-xl
                                                                    bg-green-50
                                                                    px-3
                                                                    py-2.5
                                                                    text-xs
                                                                    font-semibold
                                                                    text-green-800
                                                                "
                                                            >
                                                                Approve
                                                            </button>


                                                            <button
                                                                type="button"
                                                                onClick={
                                                                    () =>
                                                                        setActionMode(
                                                                            "send-back"
                                                                        )
                                                                }
                                                                className="
                                                                    rounded-xl
                                                                    bg-blue-50
                                                                    px-3
                                                                    py-2.5
                                                                    text-xs
                                                                    font-semibold
                                                                    text-blue-800
                                                                "
                                                            >
                                                                Send Back
                                                            </button>


                                                            <button
                                                                type="button"
                                                                onClick={
                                                                    () =>
                                                                        setActionMode(
                                                                            "reject"
                                                                        )
                                                                }
                                                                className="
                                                                    rounded-xl
                                                                    bg-red-50
                                                                    px-3
                                                                    py-2.5
                                                                    text-xs
                                                                    font-semibold
                                                                    text-red-700
                                                                "
                                                            >
                                                                Reject
                                                            </button>

                                                        </div>


                                                        {
                                                            actionMode
                                                            && (

                                                                <div
                                                                    className="
                                                                        mt-3
                                                                        rounded-xl
                                                                        border
                                                                        border-[#eadfd6]
                                                                        bg-[#fffaf3]
                                                                        p-4
                                                                    "
                                                                >

                                                                    <p
                                                                        className="
                                                                            text-sm
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {pretty(actionMode)}
                                                                    </p>


                                                                    <textarea
                                                                        rows={
                                                                            3
                                                                        }
                                                                        value={
                                                                            actionComment
                                                                        }
                                                                        onChange={
                                                                            event =>
                                                                                setActionComment(
                                                                                    event.target.value
                                                                                )
                                                                        }
                                                                        placeholder={
                                                                            actionMode === "approve"
                                                                                ? "Optional note"
                                                                                : "Reason / correction required"
                                                                        }
                                                                        className={`${INPUT_CLASS} mt-3 py-3`}
                                                                    />


                                                                    <div
                                                                        className="
                                                                            mt-3
                                                                            flex
                                                                            gap-2
                                                                        "
                                                                    >

                                                                        <button
                                                                            type="button"
                                                                            disabled={
                                                                                actionSaving
                                                                            }
                                                                            onClick={
                                                                                () => {

                                                                                    void submitAction();
                                                                                }
                                                                            }
                                                                            className="
                                                                                min-h-10
                                                                                rounded-xl
                                                                                bg-[#7a1625]
                                                                                px-4
                                                                                text-xs
                                                                                font-semibold
                                                                                text-white

                                                                                disabled:opacity-50
                                                                            "
                                                                        >
                                                                            {
                                                                                actionSaving
                                                                                    ? "Saving..."
                                                                                    : "Confirm"
                                                                            }
                                                                        </button>


                                                                        <button
                                                                            type="button"
                                                                            disabled={
                                                                                actionSaving
                                                                            }
                                                                            onClick={
                                                                                () => {

                                                                                    setActionMode(
                                                                                        null
                                                                                    );


                                                                                    setActionComment(
                                                                                        ""
                                                                                    );
                                                                                }
                                                                            }
                                                                            className={SECONDARY_BUTTON_CLASS}
                                                                        >
                                                                            Cancel
                                                                        </button>

                                                                    </div>

                                                                </div>

                                                            )
                                                        }

                                                    </div>

                                                )
                                            }


                                            <div className="mt-6">

                                                <h3
                                                    className="
                                                        text-sm
                                                        font-bold
                                                        text-[#241715]
                                                    "
                                                >
                                                    Approval History
                                                </h3>


                                                <div
                                                    className="
                                                        mt-3
                                                        space-y-3
                                                    "
                                                >

                                                    {
                                                        detail.history.map(
                                                            item => (

                                                                <div
                                                                    key={
                                                                        item.id
                                                                    }
                                                                    className="
                                                                        rounded-xl
                                                                        border
                                                                        border-[#eadfd6]
                                                                        p-3
                                                                    "
                                                                >

                                                                    <div
                                                                        className="
                                                                            flex
                                                                            items-start
                                                                            justify-between
                                                                            gap-3
                                                                        "
                                                                    >

                                                                        <div>

                                                                            <p
                                                                                className="
                                                                                    text-sm
                                                                                    font-semibold
                                                                                    text-[#241715]
                                                                                "
                                                                            >
                                                                                {pretty(item.action)}
                                                                            </p>


                                                                            <p
                                                                                className="
                                                                                    mt-1
                                                                                    text-xs
                                                                                    text-[#756763]
                                                                                "
                                                                            >
                                                                                {item.actorName}
                                                                            </p>

                                                                        </div>


                                                                        <p
                                                                            className="
                                                                                shrink-0
                                                                                text-[11px]
                                                                                text-[#756763]
                                                                            "
                                                                        >
                                                                            {formatDateTime(item.createdAt)}
                                                                        </p>

                                                                    </div>


                                                                    {
                                                                        item.comment
                                                                        && (

                                                                            <p
                                                                                className="
                                                                                    mt-2
                                                                                    text-xs
                                                                                    leading-5
                                                                                    text-[#756763]
                                                                                "
                                                                            >
                                                                                {item.comment}
                                                                            </p>

                                                                        )
                                                                    }

                                                                </div>

                                                            )
                                                        )
                                                    }

                                                </div>

                                            </div>

                                        </div>

                                    )
                        }

                    </aside>

                </div>

            </div>

        </div>
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

        disabled:bg-[#f8f4f1]
        disabled:opacity-70
    `;


const SECONDARY_BUTTON_CLASS =
    `
        min-h-10
        rounded-xl
        border
        border-[#eadfd6]
        bg-white
        px-4
        text-xs
        font-semibold
        text-[#7a1625]

        disabled:cursor-not-allowed
        disabled:opacity-50
    `;


function CountCard({
    label,
    value,
    active,
    onClick
}: {
    label: string;
    value: number;
    active: boolean;
    onClick: () => void;
}) {

    return (
        <button
            type="button"
            onClick={
                onClick
            }
            className={`
                rounded-2xl
                border
                p-5
                text-left
                transition

                ${
                    active
                        ? "border-[#c88a20] bg-[#fff8e8]"
                        : "border-[#eadfd6] bg-white hover:bg-[#fffaf3]"
                }
            `}
        >

            <p
                className="
                    text-xs
                    font-semibold
                    uppercase
                    tracking-[0.12em]
                    text-[#756763]
                "
            >
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-3xl
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </button>
    );
}


function DetailRow({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div
            className="
                grid
                grid-cols-[110px_minmax(0,1fr)]
                gap-3
            "
        >

            <dt className="text-[#756763]">
                {label}
            </dt>


            <dd
                className="
                    wrap-break-word
                    font-semibold
                    text-[#241715]
                "
            >
                {value}
            </dd>

        </div>
    );
}
