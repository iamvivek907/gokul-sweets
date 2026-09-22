"use client";

import Link
    from "next/link";

import {
    useEffect,
    useState
} from "react";

import PayrollPaymentApprovalHistory
    from "@/components/admin/PayrollPaymentApprovalHistory";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    cancelPayrollPaymentRequest,
    createPayrollPaymentRequest,
    getMyPayrollEarnings,
    getMyPayrollOptions,
    getMyPayrollPaymentRequests,
    getMyPayrollSummary,
    resubmitPayrollPaymentRequest,
    updatePayrollPaymentRequest
} from "@/services/adminPayrollApi";

import type {
    PayrollEarning,
    PayrollOptions,
    PayrollPaymentRequest,
    PayrollSummary,
    SpringPage
} from "@/types/adminPayroll";


const EMPTY_EARNINGS:
    SpringPage<PayrollEarning> = {

    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
    first: true,
    last: true
};


const EMPTY_PAYMENTS:
    SpringPage<PayrollPaymentRequest> = {

    content: [],
    totalElements: 0,
    totalPages: 0,
    number: 0,
    size: 20,
    first: true,
    last: true
};


function money(
    value: number
) {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            minimumFractionDigits: 2
        }
    ).format(value);
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
                part.slice(1)
        )
        .join(" ");
}


function statusClass(
    status: PayrollPaymentRequest["status"]
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


export default function MyPayrollPage() {

    const {
        authorization,
        isAuthenticated
    } =
        useAdminAuth();


    const [
        options,
        setOptions
    ] =
        useState<PayrollOptions | null>(
            null
        );


    const [
        summary,
        setSummary
    ] =
        useState<PayrollSummary | null>(
            null
        );


    const [
        earnings,
        setEarnings
    ] =
        useState<
            SpringPage<PayrollEarning>
        >(
            EMPTY_EARNINGS
        );


    const [
        payments,
        setPayments
    ] =
        useState<
            SpringPage<PayrollPaymentRequest>
        >(
            EMPTY_PAYMENTS
        );


    const [
        branchId,
        setBranchId
    ] =
        useState(
            ""
        );


    const [
        amount,
        setAmount
    ] =
        useState(
            ""
        );


    const [
        note,
        setNote
    ] =
        useState(
            ""
        );


    const [
        editingPaymentId,
        setEditingPaymentId
    ] =
        useState<number | null>(
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
                getMyPayrollOptions(
                    authorization,
                    controller.signal
                ),
                getMyPayrollSummary(
                    authorization,
                    controller.signal
                ),
                getMyPayrollEarnings(
                    authorization,
                    0,
                    20,
                    controller.signal
                ),
                getMyPayrollPaymentRequests(
                    authorization,
                    0,
                    20,
                    controller.signal
                )
            ])
                .then(
                    ([
                        optionResult,
                        summaryResult,
                        earningResult,
                        paymentResult
                    ]) => {

                        setOptions(
                            optionResult
                        );


                        setSummary(
                            summaryResult
                        );


                        setEarnings(
                            earningResult
                        );


                        setPayments(
                            paymentResult
                        );


                        setBranchId(
                            optionResult.branches[0]?.id
                                ? String(
                                    optionResult.branches[0].id
                                )
                                : ""
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
                                : "Unable to load payroll."
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


    async function refresh() {

        if (
            authorization === null
        ) {

            return;
        }


        const [
            summaryResult,
            earningResult,
            paymentResult
        ] =
            await Promise.all([
                getMyPayrollSummary(
                    authorization
                ),
                getMyPayrollEarnings(
                    authorization,
                    0,
                    20
                ),
                getMyPayrollPaymentRequests(
                    authorization,
                    0,
                    20
                )
            ]);


        setSummary(
            summaryResult
        );


        setEarnings(
            earningResult
        );


        setPayments(
            paymentResult
        );
    }


    function resetForm() {

        setEditingPaymentId(
            null
        );


        setAmount(
            ""
        );


        setNote(
            ""
        );


        setActionComment(
            ""
        );
    }


    async function savePaymentRequest() {

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


        const parsedBranchId =
            Number(
                branchId
            );


        const parsedAmount =
            Number(
                amount
            );


        if (
            !Number.isFinite(
                parsedBranchId
            )
            ||
            parsedBranchId <= 0
        ) {

            setError(
                "Select a branch."
            );


            return;
        }


        if (
            !Number.isFinite(
                parsedAmount
            )
            ||
            parsedAmount <= 0
        ) {

            setError(
                "Enter a valid amount."
            );


            return;
        }


        if (
            summary
            &&
            parsedAmount
                > summary.availableToRequest
            &&
            editingPaymentId === null
        ) {

            setError(
                "Requested amount is higher than your available earned balance."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            if (
                editingPaymentId === null
            ) {

                const created =
                    await createPayrollPaymentRequest(
                        {
                            branchId:
                                parsedBranchId,
                            amount:
                                parsedAmount,
                            note:
                                note.trim()
                                || null
                        },
                        authorization
                    );


                setSuccess(
                    `${created.approvalRequestNumber} was submitted for approval.`
                );

            } else {

                const updated =
                    await updatePayrollPaymentRequest(
                        editingPaymentId,
                        {
                            branchId:
                                parsedBranchId,
                            amount:
                                parsedAmount,
                            note:
                                note.trim()
                                || null
                        },
                        authorization
                    );


                setSuccess(
                    `${updated.approvalRequestNumber} was corrected.`
                );
            }


            resetForm();


            await refresh();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save payment request."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    function editPayment(
        payment: PayrollPaymentRequest
    ) {

        setEditingPaymentId(
            payment.id
        );


        setBranchId(
            String(
                payment.branchId
            )
        );


        setAmount(
            String(
                payment.amount
            )
        );


        setNote(
            payment.note
            ?? ""
        );


        setActionComment(
            ""
        );


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    async function resubmitPayment(
        payment: PayrollPaymentRequest
    ) {

        if (
            authorization === null
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


        try {

            await resubmitPayrollPaymentRequest(
                payment.id,
                actionComment.trim()
                    || null,
                authorization
            );


            setSuccess(
                `${payment.approvalRequestNumber} was resubmitted.`
            );


            resetForm();


            await refresh();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to resubmit payment request."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function cancelPayment(
        payment: PayrollPaymentRequest
    ) {

        if (
            authorization === null
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


        try {

            await cancelPayrollPaymentRequest(
                payment.id,
                actionComment.trim()
                    || null,
                authorization
            );


            setSuccess(
                `${payment.approvalRequestNumber} was cancelled.`
            );


            resetForm();


            await refresh();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to cancel payment request."
            );

        } finally {

            setSaving(
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


                <div className="mt-5">

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
                        My Payroll
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
                        View confirmed earnings and request partial payments from your available earned balance.
                    </p>

                </div>


                {
                    error
                    && (

                        <div
                            className="
                                mt-6
                                rounded-xl
                                border
                                border-red-200
                                bg-red-50
                                px-4
                                py-3
                                text-sm
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
                            className="
                                mt-6
                                rounded-xl
                                border
                                border-green-200
                                bg-green-50
                                px-4
                                py-3
                                text-sm
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
                        gap-4
                        md:grid-cols-3
                    "
                >

                    <SummaryCard
                        label="Total Earned"
                        value={
                            summary
                                ? money(
                                    summary.totalEarned
                                )
                                : "—"
                        }
                    />


                    <SummaryCard
                        label="Committed / Taken"
                        value={
                            summary
                                ? money(
                                    summary.committedPayments
                                )
                                : "—"
                        }
                    />


                    <SummaryCard
                        label="Available to Request"
                        value={
                            summary
                                ? money(
                                    summary.availableToRequest
                                )
                                : "—"
                        }
                    />

                </div>


                <div
                    className="
                        mt-6
                        grid
                        gap-6
                        xl:grid-cols-[minmax(0,1fr)_420px]
                    "
                >

                    <div className="space-y-6">

                        <section className={CARD_CLASS}>

                            <div className={CARD_HEADER_CLASS}>

                                <h2 className="font-bold text-[#241715]">
                                    Payment Requests
                                </h2>

                            </div>


                            {
                                payments.content.length === 0
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            No payment requests yet.
                                        </div>

                                    )
                                    : (

                                        <div className="divide-y divide-[#eadfd6]">

                                            {
                                                payments.content.map(
                                                    payment => (

                                                        <div
                                                            key={
                                                                payment.id
                                                            }
                                                            className="p-5"
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

                                                                <div>

                                                                    <p
                                                                        className="
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {money(payment.amount)}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {payment.branchName} · {payment.approvalRequestNumber}
                                                                    </p>


                                                                    {
                                                                        payment.note
                                                                        && (

                                                                            <p
                                                                                className="
                                                                                    mt-2
                                                                                    text-sm
                                                                                    text-[#756763]
                                                                                "
                                                                            >
                                                                                {payment.note}
                                                                            </p>

                                                                        )
                                                                    }

                                                                </div>


                                                                <span
                                                                    className={`
                                                                        rounded-full
                                                                        border
                                                                        px-2.5
                                                                        py-1
                                                                        text-xs
                                                                        font-semibold
                                                                        ${statusClass(payment.status)}
                                                                    `}
                                                                >
                                                                    {pretty(payment.status)}
                                                                </span>

                                                            </div>


                                                            {
                                                                (
                                                                    payment.status === "PENDING"
                                                                    ||
                                                                    payment.status === "SENT_BACK"
                                                                )
                                                                && (

                                                                    <div
                                                                        className="
                                                                            mt-4
                                                                            flex
                                                                            flex-wrap
                                                                            gap-2
                                                                        "
                                                                    >

                                                                        {
                                                                            payment.status === "SENT_BACK"
                                                                            && (

                                                                                <>
                                                                                    <button
                                                                                        type="button"
                                                                                        onClick={
                                                                                            () =>
                                                                                                editPayment(
                                                                                                    payment
                                                                                                )
                                                                                        }
                                                                                        className={SECONDARY_BUTTON_CLASS}
                                                                                    >
                                                                                        Edit
                                                                                    </button>

                                                                                    <button
                                                                                        type="button"
                                                                                        onClick={
                                                                                            () => {

                                                                                                void resubmitPayment(
                                                                                                    payment
                                                                                                );
                                                                                            }
                                                                                        }
                                                                                        className={SECONDARY_BUTTON_CLASS}
                                                                                    >
                                                                                        Resubmit
                                                                                    </button>
                                                                                </>

                                                                            )
                                                                        }


                                                                        <button
                                                                            type="button"
                                                                            onClick={
                                                                                () => {

                                                                                    void cancelPayment(
                                                                                        payment
                                                                                    );
                                                                                }
                                                                            }
                                                                            className="
                                                                                min-h-10
                                                                                rounded-xl
                                                                                border
                                                                                border-red-200
                                                                                bg-red-50
                                                                                px-4
                                                                                text-xs
                                                                                font-semibold
                                                                                text-red-700
                                                                            "
                                                                        >
                                                                            Cancel
                                                                        </button>

                                                                    </div>

                                                                )
                                                            }


                                                            <PayrollPaymentApprovalHistory
                                                                paymentRequestId={
                                                                    payment.id
                                                                }
                                                            />

                                                        </div>

                                                    )
                                                )
                                            }

                                        </div>

                                    )
                            }

                        </section>


                        <section className={CARD_CLASS}>

                            <div className={CARD_HEADER_CLASS}>

                                <h2 className="font-bold text-[#241715]">
                                    Earnings Ledger
                                </h2>

                            </div>


                            {
                                earnings.content.length === 0
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            No confirmed earnings yet.
                                        </div>

                                    )
                                    : (

                                        <div className="divide-y divide-[#eadfd6]">

                                            {
                                                earnings.content.map(
                                                    earning => (

                                                        <div
                                                            key={
                                                                earning.id
                                                            }
                                                            className="
                                                                flex
                                                                items-start
                                                                justify-between
                                                                gap-4
                                                                p-5
                                                            "
                                                        >

                                                            <div>

                                                                <p
                                                                    className="
                                                                        font-semibold
                                                                        text-[#241715]
                                                                    "
                                                                >
                                                                    {earning.earningDate}
                                                                </p>


                                                                <p
                                                                    className="
                                                                        mt-1
                                                                        text-xs
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    {earning.branchName} · {pretty(earning.attendanceType)}
                                                                </p>


                                                                <p
                                                                    className="
                                                                        mt-1
                                                                        text-xs
                                                                        text-[#756763]
                                                                    "
                                                                >
                                                                    Rate snapshot: {money(earning.rateSnapshot)}
                                                                </p>

                                                            </div>


                                                            <p
                                                                className="
                                                                    font-bold
                                                                    text-[#7a1625]
                                                                "
                                                            >
                                                                {money(earning.amount)}
                                                            </p>

                                                        </div>

                                                    )
                                                )
                                            }

                                        </div>

                                    )
                            }

                        </section>

                    </div>


                    <aside className={CARD_CLASS}>

                        <div className={CARD_HEADER_CLASS}>

                            <h2 className="font-bold text-[#241715]">
                                {
                                    editingPaymentId === null
                                        ? "Request Money Taken"
                                        : "Correct Payment Request"
                                }
                            </h2>

                        </div>


                        <div className="space-y-5 p-5">

                            <Field label="Branch">

                                <select
                                    value={
                                        branchId
                                    }
                                    onChange={
                                        event =>
                                            setBranchId(
                                                event.target.value
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


                            <Field label="Amount">

                                <input
                                    type="number"
                                    min="0.01"
                                    step="0.01"
                                    value={
                                        amount
                                    }
                                    onChange={
                                        event =>
                                            setAmount(
                                                event.target.value
                                            )
                                    }
                                    className={INPUT_CLASS}
                                    placeholder="0.00"
                                />

                            </Field>


                            <Field label="Note">

                                <textarea
                                    rows={
                                        4
                                    }
                                    value={
                                        note
                                    }
                                    onChange={
                                        event =>
                                            setNote(
                                                event.target.value
                                            )
                                    }
                                    className={`${INPUT_CLASS} py-3`}
                                    placeholder="Optional note"
                                />

                            </Field>


                            {
                                editingPaymentId !== null
                                && (

                                    <Field label="Resubmit / cancellation note">

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
                                            className={`${INPUT_CLASS} py-3`}
                                            placeholder="Optional note"
                                        />

                                    </Field>

                                )
                            }


                            <button
                                type="button"
                                disabled={
                                    loading
                                    ||
                                    saving
                                }
                                onClick={
                                    () => {

                                        void savePaymentRequest();
                                    }
                                }
                                className="
                                    min-h-11
                                    w-full
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
                                        : editingPaymentId === null
                                            ? "Submit Payment Request"
                                            : "Save Correction"
                                }
                            </button>


                            {
                                editingPaymentId !== null
                                && (

                                    <button
                                        type="button"
                                        onClick={
                                            resetForm
                                        }
                                        className={`${SECONDARY_BUTTON_CLASS} w-full`}
                                    >
                                        Stop Editing
                                    </button>

                                )
                            }

                        </div>

                    </aside>

                </div>

            </div>

        </div>
    );
}


const CARD_CLASS =
    `
        overflow-hidden
        rounded-2xl
        border
        border-[#eadfd6]
        bg-white
    `;


const CARD_HEADER_CLASS =
    `
        border-b
        border-[#eadfd6]
        bg-[#fffaf3]
        px-5
        py-4
    `;


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
    `;


function SummaryCard({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div
            className="
                rounded-2xl
                border
                border-[#eadfd6]
                bg-white
                p-5
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
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-2xl
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </div>
    );
}


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
