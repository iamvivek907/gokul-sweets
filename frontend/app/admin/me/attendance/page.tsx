"use client";

import Link
    from "next/link";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import AttendanceApprovalHistory
    from "@/components/admin/AttendanceApprovalHistory";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    cancelAttendance,
    createAttendance,
    getAttendanceOptions,
    getMyAttendance,
    resubmitAttendance,
    updateAttendance
} from "@/services/adminAttendanceApi";

import type {
    AttendanceItem,
    AttendanceOptions,
    AttendanceStatus,
    AttendanceType,
    CreateAttendancePayload,
    SpringPage,
    UpdateAttendancePayload
} from "@/types/adminAttendance";


const EMPTY_PAGE:
    SpringPage<AttendanceItem> = {

    content: [],

    totalElements: 0,

    totalPages: 0,

    number: 0,

    size: 20,

    first: true,

    last: true
};


interface AttendanceForm {

    branchId: string;

    attendanceDate: string;

    attendanceType: AttendanceType;

    checkInTime: string;

    checkOutTime: string;

    note: string;
}


const EMPTY_FORM: AttendanceForm = {

    branchId: "",

    attendanceDate: "",

    attendanceType: "PRESENT",

    checkInTime: "",

    checkOutTime: "",

    note: ""
};


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
                part.slice(1)
        )
        .join(" ");
}


function statusClass(
    status: AttendanceStatus
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


function formFromItem(
    item: AttendanceItem
): AttendanceForm {

    return {

        branchId:
            String(
                item.branchId
            ),

        attendanceDate:
            item.attendanceDate,

        attendanceType:
            item.attendanceType,

        checkInTime:
            item.checkInTime
            ?? "",

        checkOutTime:
            item.checkOutTime
            ?? "",

        note:
            item.note
            ?? ""
    };
}


function nullable(
    value: string
) {

    const trimmed =
        value.trim();


    return trimmed
        ? trimmed
        : null;
}


function todayInBusinessZone() {

    return new Intl.DateTimeFormat(
        "en-CA",
        {
            timeZone: "Asia/Kolkata",
            year: "numeric",
            month: "2-digit",
            day: "2-digit"
        }
    ).format(
        new Date()
    );
}


export default function MyAttendancePage() {

    const {
        authorization,
        isAuthenticated
    } =
        useAdminAuth();


    const [
        options,
        setOptions
    ] =
        useState<AttendanceOptions | null>(
            null
        );


    const [
        attendance,
        setAttendance
    ] =
        useState<
            SpringPage<AttendanceItem>
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
        quickBranchId,
        setQuickBranchId
    ] =
        useState(
            ""
        );


    const [
        form,
        setForm
    ] =
        useState<AttendanceForm>(
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


    const selected =
        useMemo(
            () =>
                attendance.content.find(
                    item =>
                        item.id
                        === selectedId
                )
                ?? null,
            [
                attendance.content,
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
                getAttendanceOptions(
                    authorization,
                    controller.signal
                ),
                getMyAttendance(
                    authorization,
                    0,
                    20,
                    controller.signal
                )
            ])
                .then(
                    ([
                        optionResult,
                        attendanceResult
                    ]) => {

                        setOptions(
                            optionResult
                        );


                        setQuickBranchId(
                            optionResult.branches[0]?.id
                                ? String(
                                    optionResult.branches[0].id
                                )
                                : ""
                        );


                        setAttendance(
                            attendanceResult
                        );


                        setPage(
                            0
                        );


                        const first =
                            attendanceResult.content[0]
                            ?? null;


                        setSelectedId(
                            first?.id
                            ?? null
                        );


                        if (first) {

                            setForm(
                                formFromItem(
                                    first
                                )
                            );
                        }


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
                                : "Unable to load attendance."
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
            await getMyAttendance(
                authorization,
                requestedPage,
                20
            );


        setAttendance(
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


            if (preferred) {

                setForm(
                    formFromItem(
                        preferred
                    )
                );
            }
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
                quickBranchId
                ||
                (
                    options?.branches[0]?.id
                        ? String(
                            options.branches[0].id
                        )
                        : ""
                ),

            attendanceDate:
                todayInBusinessZone()
        });


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    async function markPresentToday() {

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
                quickBranchId
            );


        if (
            !Number.isFinite(
                branchId
            )
            ||
            branchId <= 0
        ) {

            setError(
                "Choose your working branch first."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            const created =
                await createAttendance(
                    {
                        branchId,

                        attendanceDate:
                            todayInBusinessZone(),

                        attendanceType:
                            "PRESENT",

                        checkInTime:
                            null,

                        checkOutTime:
                            null,

                        note:
                            null
                    },
                    authorization
                );


            setSuccess(
                "Present marked successfully and sent for approval."
            );


            setCreateMode(
                false
            );


            await refresh(
                created.id,
                0
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to mark attendance."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    function selectAttendance(
        item: AttendanceItem
    ) {

        setCreateMode(
            false
        );


        setSelectedId(
            item.id
        );


        setForm(
            formFromItem(
                item
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


    async function saveAttendance() {

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
            !form.attendanceDate
        ) {

            setError(
                "Attendance date is required."
            );


            return;
        }


        if (
            form.attendanceType === "ABSENT"
            &&
            (
                form.checkInTime
                ||
                form.checkOutTime
            )
        ) {

            setError(
                "Absent attendance cannot contain check-in or check-out time."
            );


            return;
        }


        if (
            form.checkInTime
            &&
            form.checkOutTime
            &&
            form.checkOutTime
            <= form.checkInTime
        ) {

            setError(
                "Check-out time must be after check-in time."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            const payload:
                CreateAttendancePayload = {

                branchId,

                attendanceDate:
                    form.attendanceDate,

                attendanceType:
                    form.attendanceType,

                checkInTime:
                    form.attendanceType
                    === "ABSENT"
                        ? null
                        : nullable(
                            form.checkInTime
                        ),

                checkOutTime:
                    form.attendanceType
                    === "ABSENT"
                        ? null
                        : nullable(
                            form.checkOutTime
                        ),

                note:
                    nullable(
                        form.note
                    )
            };


            if (
                createMode
            ) {

                const created =
                    await createAttendance(
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
                selected === null
            ) {

                return;
            }


            if (
                selected.status
                !== "SENT_BACK"
            ) {

                setError(
                    "Only sent-back attendance can be edited."
                );


                return;
            }


            const updatePayload:
                UpdateAttendancePayload = {
                    ...payload
                };


            const updated =
                await updateAttendance(
                    selected.id,
                    updatePayload,
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
                    : "Unable to save attendance."
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
            selected === null
            ||
            selected.status
                !== "SENT_BACK"
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
                await resubmitAttendance(
                    selected.id,
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
                    : "Unable to resubmit attendance."
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
            selected === null
            ||
            (
                selected.status
                    !== "PENDING"
                &&
                selected.status
                    !== "SENT_BACK"
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
                await cancelAttendance(
                    selected.id,
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
                    : "Unable to cancel attendance."
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
                attendance.totalPages > 0
                &&
                nextPage
                >= attendance.totalPages
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
                    : "Unable to load attendance."
            );

        } finally {

            setLoading(
                false
            );
        }
    }


    const editable =
        createMode
        ||
        selected?.status
            === "SENT_BACK";


    const today =
        todayInBusinessZone();


    const selectedQuickBranchId =
        Number(
            quickBranchId
        );


    const todayAttendance =
        attendance.content.find(
            item =>
                item.attendanceDate === today
                &&
                item.branchId === selectedQuickBranchId
                &&
                (
                    item.status === "PENDING"
                    ||
                    item.status === "APPROVED"
                    ||
                    item.status === "SENT_BACK"
                )
        )
        ?? null;


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
                            My Attendance
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
                            Submit daily attendance, track approval status and correct sent-back records.
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
                            border
                            border-[#eadfd6]
                            bg-white
                            px-5
                            text-sm
                            font-semibold
                            text-[#7a1625]

                            hover:bg-[#fff1e9]

                            disabled:opacity-50
                        "
                    >
                        Other Attendance Options
                    </button>

                </div>


                {
                    !createMode
                    && (

                        <section
                            className="
                                mt-6
                                rounded-2xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                                sm:p-6
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-5
                                    lg:flex-row
                                    lg:items-center
                                    lg:justify-between
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
                                        Today · {formatDate(today)}
                                    </p>


                                    <h2
                                        className="
                                            mt-2
                                            text-2xl
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        Mark Today&apos;s Attendance
                                    </h2>


                                    <p
                                        className="
                                            mt-2
                                            text-sm
                                            leading-6
                                            text-[#756763]
                                        "
                                    >
                                        For a normal working day, just tap the button below. No time or note is required.
                                    </p>

                                </div>


                                {
                                    options
                                    &&
                                    options.branches.length > 1
                                    && (

                                        <div className="w-full lg:max-w-xs">

                                            <label
                                                className="
                                                    mb-2
                                                    block
                                                    text-xs
                                                    font-semibold
                                                    text-[#756763]
                                                "
                                            >
                                                Working branch
                                            </label>


                                            <select
                                                value={
                                                    quickBranchId
                                                }
                                                disabled={
                                                    saving
                                                }
                                                onChange={
                                                    event =>
                                                        setQuickBranchId(
                                                            event.target.value
                                                        )
                                                }
                                                className={INPUT_CLASS}
                                            >

                                                <option value="">
                                                    Choose branch
                                                </option>


                                                {
                                                    options.branches.map(
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

                                    )
                                }

                            </div>


                            {
                                options
                                &&
                                options.branches.length === 1
                                && (

                                    <p
                                        className="
                                            mt-4
                                            text-sm
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        Branch: {options.branches[0].name}
                                    </p>

                                )
                            }


                            {
                                todayAttendance
                                    ? (

                                        <div
                                            className="
                                                mt-5
                                                rounded-xl
                                                border
                                                border-green-200
                                                bg-green-50
                                                px-4
                                                py-4
                                            "
                                        >

                                            <p
                                                className="
                                                    text-sm
                                                    font-bold
                                                    text-green-800
                                                "
                                            >
                                                ✓ Attendance already marked for today
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    text-xs
                                                    text-green-700
                                                "
                                            >
                                                {pretty(todayAttendance.status)} · {todayAttendance.branchName}
                                            </p>

                                        </div>

                                    )
                                    : (

                                        <button
                                            type="button"
                                            disabled={
                                                loading
                                                ||
                                                saving
                                                ||
                                                !quickBranchId
                                            }
                                            onClick={
                                                () => {

                                                    void markPresentToday();
                                                }
                                            }
                                            className="
                                                mt-5
                                                min-h-14
                                                w-full
                                                rounded-2xl
                                                bg-[#7a1625]
                                                px-6
                                                text-lg
                                                font-bold
                                                text-white
                                                shadow-sm
                                                transition

                                                hover:bg-[#5d0f1b]

                                                disabled:cursor-not-allowed
                                                disabled:opacity-50
                                            "
                                        >
                                            {
                                                saving
                                                    ? "Marking Present..."
                                                    : "✓ MARK PRESENT TODAY"
                                            }
                                        </button>

                                    )
                            }

                        </section>

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
                                Attendance History
                            </h2>


                            <p
                                className="
                                    mt-1
                                    text-xs
                                    text-[#756763]
                                "
                            >
                                {attendance.totalElements} records
                            </p>

                        </div>


                        {
                            loading
                            &&
                            attendance.content.length === 0
                                ? (

                                    <div className="p-6 text-sm text-[#756763]">
                                        Loading attendance...
                                    </div>

                                )
                                : attendance.content.length === 0
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            No attendance records yet.
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
                                                attendance.content.map(
                                                    item => (

                                                        <button
                                                            key={
                                                                item.id
                                                            }
                                                            type="button"
                                                            onClick={
                                                                () =>
                                                                    selectAttendance(
                                                                        item
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
                                                                    selectedId === item.id
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
                                                                        {formatDate(item.attendanceDate)}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {item.branchName} · {pretty(item.attendanceType)}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            truncate
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {item.approvalRequestNumber}
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
                                                                        ${statusClass(item.status)}
                                                                    `}
                                                                >
                                                                    {pretty(item.status)}
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
                                    attendance.first
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
                                    attendance.totalPages === 0
                                        ? "0 / 0"
                                        : `${attendance.number + 1} / ${attendance.totalPages}`
                                }
                            </span>


                            <button
                                type="button"
                                disabled={
                                    attendance.last
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
                                        ? "Other attendance options"
                                        : "Attendance detail"
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
                                        ? "Half Day / Absent / Different Date"
                                        : selected?.approvalRequestNumber
                                        ?? "Select a record"
                                }
                            </h2>

                        </div>


                        {
                            !createMode
                            &&
                            selected === null
                                ? (

                                    <div className="p-8 text-sm text-[#756763]">
                                        Select an attendance record to view its details.
                                    </div>

                                )
                                : (

                                    <div className="p-5 sm:p-6">

                                        {
                                            selected
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
                                                            ${statusClass(selected.status)}
                                                        `}
                                                    >
                                                        {pretty(selected.status)}
                                                    </span>


                                                    <span
                                                        className="
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Version {selected.workflowVersion}
                                                    </span>

                                                </div>

                                            )
                                        }


                                        {
                                            selected
                                            &&
                                            !createMode
                                            && (

                                                <AttendanceApprovalHistory
                                                    attendanceId={
                                                        selected.id
                                                    }
                                                />

                                            )
                                        }


                                        <div
                                            className="
                                                mt-6
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
                                                        !editable
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
                                                                </option>

                                                            )
                                                        )
                                                    }

                                                </select>

                                            </Field>


                                            <Field label="Attendance Date">

                                                <input
                                                    type="date"
                                                    value={
                                                        form.attendanceDate
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        !editable
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    attendanceDate:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            <Field label="Attendance Type">

                                                <select
                                                    value={
                                                        form.attendanceType
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        !editable
                                                    }
                                                    onChange={
                                                        event => {

                                                            const value =
                                                                event.target.value as AttendanceType;


                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    attendanceType:
                                                                        value,
                                                                    checkInTime:
                                                                        value === "ABSENT"
                                                                            ? ""
                                                                            : current.checkInTime,
                                                                    checkOutTime:
                                                                        value === "ABSENT"
                                                                            ? ""
                                                                            : current.checkOutTime
                                                                })
                                                            );
                                                        }
                                                    }
                                                    className={INPUT_CLASS}
                                                >

                                                    {
                                                        options?.attendanceTypes.map(
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

                                            </Field>


                                            <Field label="Check-in Time">

                                                <input
                                                    type="time"
                                                    value={
                                                        form.checkInTime
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        !editable
                                                        ||
                                                        form.attendanceType === "ABSENT"
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    checkInTime:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            <Field label="Check-out Time">

                                                <input
                                                    type="time"
                                                    value={
                                                        form.checkOutTime
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        !editable
                                                        ||
                                                        form.attendanceType === "ABSENT"
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    checkOutTime:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>

                                        </div>


                                        <div className="mt-5">

                                            <Field label="Note">

                                                <textarea
                                                    rows={
                                                        4
                                                    }
                                                    value={
                                                        form.note
                                                    }
                                                    disabled={
                                                        saving
                                                        ||
                                                        !editable
                                                    }
                                                    onChange={
                                                        event =>
                                                            setForm(
                                                                current => ({
                                                                    ...current,
                                                                    note:
                                                                        event.target.value
                                                                })
                                                            )
                                                    }
                                                    className={`${INPUT_CLASS} py-3`}
                                                    placeholder="Optional note"
                                                />

                                            </Field>

                                        </div>


                                        {
                                            editable
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

                                                                void saveAttendance();
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
                                                                    ? "Submit Attendance"
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
                                                                            attendance.content[0]
                                                                            ?? null;


                                                                        setSelectedId(
                                                                            first?.id
                                                                            ?? null
                                                                        );


                                                                        if (first) {

                                                                            setForm(
                                                                                formFromItem(
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
                                            selected?.status
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
                                                        placeholder="Optional correction note"
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
                                            selected
                                            &&
                                            (
                                                selected.status === "PENDING"
                                                ||
                                                selected.status === "SENT_BACK"
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
                                                        Cancel Attendance
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
                                                        Cancel Attendance
                                                    </button>

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
