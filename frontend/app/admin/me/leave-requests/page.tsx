"use client";

import Link
    from "next/link";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import LeaveApprovalHistory
    from "@/components/admin/LeaveApprovalHistory";

import {
    cancelLeaveRequest,
    createLeaveRequest,
    getLeaveOptions,
    getMyLeaveRequests,
    resubmitLeaveRequest,
    updateLeaveRequest
} from "@/services/adminLeaveRequestsApi";

import type {
    CreateLeaveRequestPayload,
    LeaveOptions,
    LeaveRequestItem,
    LeaveRequestStatus,
    SpringPage,
    UpdateLeaveRequestPayload
} from "@/types/adminLeaveRequests";


const EMPTY_PAGE:
    SpringPage<LeaveRequestItem> = {

    content: [],

    totalElements: 0,

    totalPages: 0,

    number: 0,

    size: 20,

    first: true,

    last: true
};


interface LeaveForm {

    branchId: string;

    startDate: string;

    endDate: string;

    reason: string;
}


const EMPTY_FORM: LeaveForm = {

    branchId: "",

    startDate: "",

    endDate: "",

    reason: ""
};


function prettyStatus(
    status: LeaveRequestStatus
) {

    return status
        .toLowerCase()
        .split("_")
        .map(
            part =>
                part.charAt(0)
                    .toUpperCase()
                +
                part.slice(1)
        )
        .join(" ");
}


function statusClass(
    status: LeaveRequestStatus
) {

    switch (status) {

        case "PENDING":
            return "border-amber-200 bg-amber-50 text-amber-800";

        case "APPROVED":
            return "border-green-200 bg-green-50 text-green-800";

        case "REJECTED":
            return "border-red-200 bg-red-50 text-red-700";

        case "SENT_BACK":
            return "border-blue-200 bg-blue-50 text-blue-800";

        case "CANCELLED":
            return "border-gray-200 bg-gray-100 text-gray-700";
    }
}


function formatDate(
    value: string
) {

    const parts =
        value.split("-");


    if (
        parts.length !== 3
    ) {

        return value;
    }


    const [
        year,
        month,
        day
    ] =
        parts;


    return `${day}/${month}/${year}`;
}


function formatDateTime(
    value: string | null
) {

    if (!value) {

        return "—";
    }


    const date =
        new Date(value);


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
    ).format(date);
}


function formFromRequest(
    request: LeaveRequestItem
): LeaveForm {

    return {

        branchId:
            String(
                request.branchId
            ),

        startDate:
            request.startDate,

        endDate:
            request.endDate,

        reason:
            request.reason
    };
}


export default function MyLeaveRequestsPage() {

    const {
        authorization,
        isAuthenticated
    } =
        useAdminAuth();


    const [
        options,
        setOptions
    ] =
        useState<LeaveOptions | null>(
            null
        );


    const [
        requests,
        setRequests
    ] =
        useState<
            SpringPage<LeaveRequestItem>
        >(
            EMPTY_PAGE
        );


    const [
        page,
        setPage
    ] =
        useState(
            0
        );


    const [
        selectedId,
        setSelectedId
    ] =
        useState<number | null>(
            null
        );


    const [
        createMode,
        setCreateMode
    ] =
        useState(
            false
        );


    const [
        form,
        setForm
    ] =
        useState<LeaveForm>(
            EMPTY_FORM
        );


    const [
        resubmitComment,
        setResubmitComment
    ] =
        useState(
            ""
        );


    const [
        cancelComment,
        setCancelComment
    ] =
        useState(
            ""
        );


    const [
        loading,
        setLoading
    ] =
        useState(
            true
        );


    const [
        saving,
        setSaving
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


    const selectedRequest =
        useMemo(
            () =>
                requests.content.find(
                    item =>
                        item.id
                        === selectedId
                )
                ?? null,
            [
                requests.content,
                selectedId
            ]
        );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !isAuthenticated
            ) {

                return;
            }


            const controller =
                new AbortController();


            Promise.all([
                getLeaveOptions(
                    authorization,
                    controller.signal
                ),
                getMyLeaveRequests(
                    authorization,
                    0,
                    20,
                    controller.signal
                )
            ])
                .then(
                    ([
                        optionResult,
                        requestResult
                    ]) => {

                        setOptions(
                            optionResult
                        );


                        setRequests(
                            requestResult
                        );


                        setPage(
                            0
                        );


                        const first =
                            requestResult.content[0]
                            ?? null;


                        setSelectedId(
                            first?.id
                            ?? null
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
                                : "Unable to load leave requests."
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
            isAuthenticated
        ]
    );


    async function refresh(
        preferredId?: number | null,
        requestedPage = page
    ) {

        if (
            authorization === null
        ) {

            return;
        }


        const result =
            await getMyLeaveRequests(
                authorization,
                requestedPage,
                20
            );


        setRequests(
            result
        );


        setPage(
            result.number
        );


        if (
            preferredId !== undefined
        ) {

            const preferred =
                result.content.find(
                    item =>
                        item.id
                        === preferredId
                )
                ?? null;


            setSelectedId(
                preferred?.id
                ?? result.content[0]?.id
                ?? null
            );
        }
    }


    function beginCreate() {

        setCreateMode(
            true
        );


        setSelectedId(
            null
        );


        setForm({
            ...EMPTY_FORM,

            branchId:
                options?.branches[0]?.id
                    ? String(
                        options.branches[0].id
                    )
                    : ""
        });


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    function selectRequest(
        request: LeaveRequestItem
    ) {

        setCreateMode(
            false
        );


        setSelectedId(
            request.id
        );


        setForm(
            formFromRequest(
                request
            )
        );


        setResubmitComment(
            ""
        );


        setCancelComment(
            ""
        );


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    async function saveRequest() {

        if (
            authorization === null
            ||
            saving
        ) {

            return;
        }


        setError(
            null
        );


        setSuccess(
            null
        );


        const branchId =
            Number(
                form.branchId
            );


        if (
            !Number.isFinite(
                branchId
            )
            ||
            branchId <= 0
        ) {

            setError(
                "Select a branch."
            );


            return;
        }


        if (
            !form.startDate
            ||
            !form.endDate
        ) {

            setError(
                "Start date and end date are required."
            );


            return;
        }


        if (
            form.endDate
            < form.startDate
        ) {

            setError(
                "End date cannot be before start date."
            );


            return;
        }


        if (
            !form.reason.trim()
        ) {

            setError(
                "Leave reason is required."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            if (
                createMode
            ) {

                const payload:
                    CreateLeaveRequestPayload = {

                    branchId,

                    startDate:
                        form.startDate,

                    endDate:
                        form.endDate,

                    reason:
                        form.reason.trim()
                };


                const created =
                    await createLeaveRequest(
                        payload,
                        authorization
                    );


                setCreateMode(
                    false
                );


                setSuccess(
                    `${created.approvalRequestNumber} was submitted for approval.`
                );


                await refresh(
                    created.id,
                    0
                );


                return;
            }


            if (
                selectedRequest === null
            ) {

                return;
            }


            if (
                selectedRequest.status
                !== "SENT_BACK"
            ) {

                setError(
                    "Only a sent-back request can be edited."
                );


                return;
            }


            const payload:
                UpdateLeaveRequestPayload = {

                branchId,

                startDate:
                    form.startDate,

                endDate:
                    form.endDate,

                reason:
                    form.reason.trim()
            };


            const updated =
                await updateLeaveRequest(
                    selectedRequest.id,
                    payload,
                    authorization
                );


            setSuccess(
                `${updated.approvalRequestNumber} was corrected. Review it and resubmit when ready.`
            );


            await refresh(
                updated.id
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save leave request."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function resubmitSelected() {

        if (
            authorization === null
            ||
            selectedRequest === null
            ||
            selectedRequest.status !== "SENT_BACK"
            ||
            saving
        ) {

            return;
        }


        setSaving(
            true
        );


        setError(
            null
        );


        setSuccess(
            null
        );


        try {

            const updated =
                await resubmitLeaveRequest(
                    selectedRequest.id,
                    resubmitComment.trim()
                        || null,
                    authorization
                );


            setSuccess(
                `${updated.approvalRequestNumber} was resubmitted for approval.`
            );


            setResubmitComment(
                ""
            );


            await refresh(
                updated.id
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to resubmit leave request."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function cancelSelected() {

        if (
            authorization === null
            ||
            selectedRequest === null
            ||
            (
                selectedRequest.status !== "PENDING"
                &&
                selectedRequest.status !== "SENT_BACK"
            )
            ||
            saving
        ) {

            return;
        }


        setSaving(
            true
        );


        setError(
            null
        );


        setSuccess(
            null
        );


        try {

            const updated =
                await cancelLeaveRequest(
                    selectedRequest.id,
                    cancelComment.trim()
                        || null,
                    authorization
                );


            setSuccess(
                `${updated.approvalRequestNumber} was cancelled.`
            );


            setCancelComment(
                ""
            );


            await refresh(
                updated.id
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to cancel leave request."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function changePage(
        nextPage: number
    ) {

        if (
            loading
            ||
            nextPage < 0
            ||
            (
                requests.totalPages > 0
                &&
                nextPage >= requests.totalPages
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

            await refresh(
                undefined,
                nextPage
            );


            setSelectedId(
                null
            );


            setCreateMode(
                false
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to load leave requests."
            );

        } finally {

            setLoading(
                false
            );
        }
    }


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

                <Link
                    href="/admin/me"
                    className="
                        inline-flex
                        min-h-10
                        items-center
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-sm
                        font-semibold
                        text-[#7a1625]
                        transition

                        hover:bg-[#fff1e9]
                    "
                >
                    ← Back to My Details
                </Link>


                <div
                    className="
                        mt-5
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
                            My Workforce
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
                            My Leave Requests
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
                            Submit leave, track approval status, correct sent-back requests and resubmit them.
                        </p>

                    </div>


                    <button
                        type="button"
                        disabled={
                            loading
                            ||
                            saving
                            ||
                            (
                                options !== null
                                &&
                                options.branches.length === 0
                            )
                        }
                        onClick={
                            beginCreate
                        }
                        className="
                            min-h-11
                            rounded-xl
                            bg-[#7a1625]
                            px-5
                            text-sm
                            font-semibold
                            text-white

                            hover:bg-[#5d0f1b]

                            disabled:opacity-50
                        "
                    >
                        New Leave Request
                    </button>

                </div>


                {
                    options
                    &&
                    options.branches.length === 0
                    && (

                        <div
                            className="
                                mt-6
                                rounded-xl
                                border
                                border-amber-200
                                bg-amber-50
                                px-4
                                py-3
                                text-sm
                                text-amber-800
                            "
                        >
                            No branch is assigned to your staff account, so you cannot submit a leave request yet.
                        </div>

                    )
                }


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
                        lg:grid-cols-[360px_minmax(0,1fr)]
                    "
                >

                    <aside
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
                                border-b
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                px-5
                                py-4
                            "
                        >

                            <h2
                                className="
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                Leave History
                            </h2>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                {requests.totalElements} requests
                            </p>

                        </div>


                        {
                            loading
                            &&
                            requests.content.length === 0
                                ? (

                                    <div className="p-6 text-sm text-[#756763]">
                                        Loading leave requests...
                                    </div>

                                )
                                : requests.content.length === 0
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            You have not submitted any leave requests yet.
                                        </div>

                                    )
                                    : (

                                        <div
                                            className="
                                                max-h-680px
                                                divide-y
                                                divide-[#eadfd6]
                                                overflow-y-auto
                                            "
                                        >

                                            {
                                                requests.content.map(
                                                    request => (

                                                        <button
                                                            key={
                                                                request.id
                                                            }
                                                            type="button"
                                                            onClick={
                                                                () =>
                                                                    selectRequest(
                                                                        request
                                                                    )
                                                            }
                                                            className={`
                                                                w-full
                                                                px-5
                                                                py-4
                                                                text-left
                                                                transition

                                                                ${
                                                                    !createMode
                                                                    &&
                                                                    selectedId === request.id
                                                                        ? "bg-[#fff1e9]"
                                                                        : "bg-white hover:bg-[#fffaf3]"
                                                                }
                                                            `}
                                                        >

                                                            <div
                                                                className="
                                                                    flex
                                                                    items-start
                                                                    justify-between
                                                                    gap-3
                                                                "
                                                            >

                                                                <div className="min-w-0">

                                                                    <p
                                                                        className="
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {formatDate(request.startDate)}
                                                                        {
                                                                            request.startDate !== request.endDate
                                                                                ? ` – ${formatDate(request.endDate)}`
                                                                                : ""
                                                                        }
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {request.branchName} · {request.totalDays} day{request.totalDays === 1 ? "" : "s"}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            truncate
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {request.approvalRequestNumber}
                                                                    </p>

                                                                </div>


                                                                <span
                                                                    className={`
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
                                                                    {prettyStatus(request.status)}
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
                                border-t
                                border-[#eadfd6]
                                px-4
                                py-3
                            "
                        >

                            <button
                                type="button"
                                disabled={
                                    requests.first
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


                            <span
                                className="
                                    text-xs
                                    font-semibold
                                    text-[#756763]
                                "
                            >
                                {
                                    requests.totalPages === 0
                                        ? "0 / 0"
                                        : `${requests.number + 1} / ${requests.totalPages}`
                                }
                            </span>


                            <button
                                type="button"
                                disabled={
                                    requests.last
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

                    </aside>


                    <section
                        className="
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
                                py-5
                                sm:px-6
                            "
                        >

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    uppercase
                                    tracking-[0.14em]
                                    text-[#c88a20]
                                "
                            >
                                {
                                    createMode
                                        ? "New request"
                                        : "Leave detail"
                                }
                            </p>


                            <h2
                                className="
                                    mt-1
                                    text-xl
                                    font-bold
                                    text-[#241715]
                                "
                            >
                                {
                                    createMode
                                        ? "Request Leave"
                                        : selectedRequest?.approvalRequestNumber
                                        ?? "Select a request"
                                }
                            </h2>

                        </div>


                        {
                            !createMode
                            &&
                            selectedRequest === null
                                ? (

                                    <div className="p-8 text-sm text-[#756763]">
                                        Select a leave request to view its details.
                                    </div>

                                )
                                : (

                                    <div className="p-5 sm:p-6">

                                        {
                                            selectedRequest
                                            &&
                                            !createMode
                                            && (

                                                <div
                                                    className="
                                                        mb-6
                                                        flex
                                                        flex-wrap
                                                        items-center
                                                        gap-3
                                                    "
                                                >

                                                    <span
                                                        className={`
                                                            rounded-full
                                                            border
                                                            px-3
                                                            py-1.5
                                                            text-xs
                                                            font-semibold
                                                            ${statusClass(selectedRequest.status)}
                                                        `}
                                                    >
                                                        {prettyStatus(selectedRequest.status)}
                                                    </span>


                                                    <span
                                                        className="
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Submitted {formatDateTime(selectedRequest.submittedAt)}
                                                    </span>


                                                    <span
                                                        className="
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Version {selectedRequest.workflowVersion}
                                                    </span>

                                                </div>

                                            )
                                        }


                                        {
                                            selectedRequest
                                            &&
                                            !createMode
                                            && (

                                                <LeaveApprovalHistory
                                                    leaveRequestId={
                                                        selectedRequest.id
                                                    }
                                                />

                                            )
                                        }


                                        <div
                                            className="
                                                grid
                                                gap-5
                                                md:grid-cols-2
                                            "
                                        >

                                            <Field label="Branch">

                                                <select
                                                    value={
                                                        form.branchId
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        (
                                                            !createMode
                                                            &&
                                                            selectedRequest?.status
                                                                !== "SENT_BACK"
                                                        )
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    branchId:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                >

                                                    <option value="">
                                                        Select branch
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

                                            </Field>


                                            <Field label="Start Date">

                                                <input
                                                    type="date"
                                                    value={
                                                        form.startDate
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        (
                                                            !createMode
                                                            &&
                                                            selectedRequest?.status
                                                                !== "SENT_BACK"
                                                        )
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    startDate:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            <Field label="End Date">

                                                <input
                                                    type="date"
                                                    value={
                                                        form.endDate
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        (
                                                            !createMode
                                                            &&
                                                            selectedRequest?.status
                                                                !== "SENT_BACK"
                                                        )
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    endDate:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>

                                        </div>


                                        <div className="mt-5">

                                            <Field label="Reason">

                                                <textarea
                                                    rows={
                                                        5
                                                    }
                                                    value={
                                                        form.reason
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        (
                                                            !createMode
                                                            &&
                                                            selectedRequest?.status
                                                                !== "SENT_BACK"
                                                        )
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    reason:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={`${INPUT_CLASS} py-3`}
                                                    placeholder="Why do you need leave?"
                                                />

                                            </Field>

                                        </div>


                                        {
                                            (
                                                createMode
                                                ||
                                                selectedRequest?.status
                                                    === "SENT_BACK"
                                            )
                                            && (

                                                <div
                                                    className="
                                                        mt-6
                                                        flex
                                                        flex-wrap
                                                        gap-3
                                                    "
                                                >

                                                    <button
                                                        type="button"
                                                        disabled={
                                                            saving
                                                        }
                                                        onClick={
                                                            () => {

                                                                void saveRequest();
                                                            }
                                                        }
                                                        className="
                                                            min-h-11
                                                            rounded-xl
                                                            bg-[#7a1625]
                                                            px-5
                                                            text-sm
                                                            font-semibold
                                                            text-white

                                                            hover:bg-[#5d0f1b]

                                                            disabled:opacity-50
                                                        "
                                                    >
                                                        {
                                                            saving
                                                                ? "Saving..."
                                                                : createMode
                                                                    ? "Submit Leave Request"
                                                                    : "Save Correction"
                                                        }
                                                    </button>


                                                    {
                                                        createMode
                                                        ? (

                                                            <button
                                                                type="button"
                                                                disabled={
                                                                    saving
                                                                }
                                                                onClick={
                                                                    () => {

                                                                        setCreateMode(
                                                                            false
                                                                        );


                                                                        const first =
                                                                            requests.content[0]
                                                                            ?? null;


                                                                        setSelectedId(
                                                                            first?.id
                                                                            ?? null
                                                                        );


                                                                        if (first) {

                                                                            setForm(
                                                                                formFromRequest(
                                                                                    first
                                                                                )
                                                                            );

                                                                        } else {

                                                                            setForm(
                                                                                EMPTY_FORM
                                                                            );
                                                                        }
                                                                    }
                                                                }
                                                                className={SECONDARY_BUTTON_CLASS}
                                                            >
                                                                Cancel
                                                            </button>

                                                        )
                                                        : null
                                                    }

                                                </div>

                                            )
                                        }


                                        {
                                            selectedRequest?.status
                                            === "SENT_BACK"
                                            && (

                                                <div
                                                    className="
                                                        mt-6
                                                        rounded-xl
                                                        border
                                                        border-blue-200
                                                        bg-blue-50
                                                        p-4
                                                    "
                                                >

                                                    <p
                                                        className="
                                                            text-sm
                                                            font-bold
                                                            text-blue-900
                                                        "
                                                    >
                                                        Ready to resubmit?
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            leading-5
                                                            text-blue-800
                                                        "
                                                    >
                                                        Save any corrections above first, then resubmit the request for manager approval.
                                                    </p>


                                                    <textarea
                                                        rows={
                                                            3
                                                        }
                                                        value={
                                                            resubmitComment
                                                        }
                                                        onChange={
                                                            event =>
                                                                setResubmitComment(
                                                                    event.target.value
                                                                )
                                                        }
                                                        placeholder="Optional note with your correction"
                                                        className={`${INPUT_CLASS} mt-3 py-3`}
                                                    />


                                                    <button
                                                        type="button"
                                                        disabled={
                                                            saving
                                                        }
                                                        onClick={
                                                            () => {

                                                                void resubmitSelected();
                                                            }
                                                        }
                                                        className="
                                                            mt-3
                                                            min-h-10
                                                            rounded-xl
                                                            bg-blue-700
                                                            px-4
                                                            text-xs
                                                            font-semibold
                                                            text-white

                                                            disabled:opacity-50
                                                        "
                                                    >
                                                        Resubmit for Approval
                                                    </button>

                                                </div>

                                            )
                                        }


                                        {
                                            selectedRequest
                                            &&
                                            (
                                                selectedRequest.status
                                                    === "PENDING"
                                                ||
                                                selectedRequest.status
                                                    === "SENT_BACK"
                                            )
                                            && (

                                                <div
                                                    className="
                                                        mt-6
                                                        border-t
                                                        border-[#eadfd6]
                                                        pt-6
                                                    "
                                                >

                                                    <p
                                                        className="
                                                            text-sm
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        Cancel Request
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        You can cancel while the request is pending or sent back.
                                                    </p>


                                                    <textarea
                                                        rows={
                                                            2
                                                        }
                                                        value={
                                                            cancelComment
                                                        }
                                                        onChange={
                                                            event =>
                                                                setCancelComment(
                                                                    event.target.value
                                                                )
                                                        }
                                                        placeholder="Optional cancellation note"
                                                        className={`${INPUT_CLASS} mt-3 py-3`}
                                                    />


                                                    <button
                                                        type="button"
                                                        disabled={
                                                            saving
                                                        }
                                                        onClick={
                                                            () => {

                                                                void cancelSelected();
                                                            }
                                                        }
                                                        className="
                                                            mt-3
                                                            min-h-10
                                                            rounded-xl
                                                            border
                                                            border-red-200
                                                            bg-red-50
                                                            px-4
                                                            text-xs
                                                            font-semibold
                                                            text-red-700

                                                            disabled:opacity-50
                                                        "
                                                    >
                                                        Cancel Leave Request
                                                    </button>

                                                </div>

                                            )
                                        }


                                        {
                                            selectedRequest
                                            &&
                                            (
                                                selectedRequest.status
                                                    === "APPROVED"
                                                ||
                                                selectedRequest.status
                                                    === "REJECTED"
                                                ||
                                                selectedRequest.status
                                                    === "CANCELLED"
                                            )
                                            && (

                                                <div
                                                    className="
                                                        mt-6
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
                                                            font-semibold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        This request is closed.
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            leading-5
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Closed requests cannot be edited or resubmitted from this page.
                                                    </p>

                                                </div>

                                            )
                                        }

                                    </div>

                                )
                        }

                    </section>

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


function Field({
    label,
    children
}: {
    label: string;
    children: React.ReactNode;
}) {

    return (
        <div>

            <label
                className="
                    mb-2
                    block
                    text-sm
                    font-semibold
                    text-[#241715]
                "
            >
                {label}
            </label>


            {children}

        </div>
    );
}
