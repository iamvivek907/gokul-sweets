"use client";

import {
    useEffect,
    useState
} from "react";

import {
    createPayrollOpeningBalance,
    getPayrollOpeningBalance
} from "@/services/adminPayrollOpeningBalanceApi";

import type {
    PayrollOpeningBalance
} from "@/types/adminPayrollOpeningBalance";


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
    ).format(
        value
    );
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


export default function PayrollOpeningBalancePanel({
    staffUserId,
    authorization,
    canManage,
    onSaved
}: {
    staffUserId: number;
    authorization: string;
    canManage: boolean;
    onSaved: () => void | Promise<void>;
}) {

    const [
        opening,
        setOpening
    ] =
        useState<PayrollOpeningBalance | null>(
            null
        );


    const [
        loadedStaffId,
        setLoadedStaffId
    ] =
        useState<number | null>(
            null
        );


    const [
        asOfDate,
        setAsOfDate
    ] =
        useState(
            ""
        );


    const [
        earnedAmount,
        setEarnedAmount
    ] =
        useState(
            ""
        );


    const [
        takenAmount,
        setTakenAmount
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


    const loading =
        loadedStaffId
        !== staffUserId;


    useEffect(
        () => {

            const controller =
                new AbortController();


            getPayrollOpeningBalance(
                staffUserId,
                authorization,
                controller.signal
            )
                .then(
                    result => {

                        setOpening(
                            result
                        );


                        setLoadedStaffId(
                            staffUserId
                        );


                        setAsOfDate(
                            ""
                        );


                        setEarnedAmount(
                            ""
                        );


                        setTakenAmount(
                            ""
                        );


                        setNote(
                            ""
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


                        setOpening(
                            null
                        );


                        setLoadedStaffId(
                            staffUserId
                        );


                        setError(
                            exception instanceof Error
                                ? exception.message
                                : "Unable to load opening balance."
                        );
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            staffUserId
        ]
    );


    async function saveOpeningBalance() {

        if (
            saving
            ||
            !canManage
        ) {

            return;
        }


        setError(
            null
        );


        const earned =
            Number(
                earnedAmount
            );


        const taken =
            Number(
                takenAmount
            );


        if (
            !asOfDate
        ) {

            setError(
                "Opening balance date is required."
            );


            return;
        }


        if (
            !Number.isFinite(
                earned
            )
            ||
            earned < 0
        ) {

            setError(
                "Enter a valid previously earned amount."
            );


            return;
        }


        if (
            !Number.isFinite(
                taken
            )
            ||
            taken < 0
        ) {

            setError(
                "Enter a valid previously taken amount."
            );


            return;
        }


        if (
            taken > earned
        ) {

            setError(
                "Previously taken amount cannot be more than previously earned amount."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            const created =
                await createPayrollOpeningBalance(
                    staffUserId,
                    {
                        asOfDate,
                        earnedAmount:
                            earned,
                        takenAmount:
                            taken,
                        note:
                            note.trim()
                            || null
                    },
                    authorization
                );


            setOpening(
                created
            );


            await onSaved();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save opening balance."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    return (
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
                    Historical / Opening Payroll Balance
                </h2>


                <p
                    className="
                        mt-1
                        text-xs
                        leading-5
                        text-[#756763]
                    "
                >
                    Use this once for earnings and payments that happened before this payroll system started. Do not create fake historical attendance.
                </p>

            </div>


            {
                loading
                    ? (

                        <div className="p-5 text-sm text-[#756763]">
                            Loading opening balance...
                        </div>

                    )
                    : opening
                        ? (

                            <div
                                className="
                                    grid
                                    gap-4
                                    p-5
                                    sm:grid-cols-2
                                    xl:grid-cols-4
                                "
                            >

                                <Value
                                    label="As Of"
                                    value={
                                        formatDate(
                                            opening.asOfDate
                                        )
                                    }
                                />


                                <Value
                                    label="Previously Earned"
                                    value={
                                        money(
                                            opening.earnedAmount
                                        )
                                    }
                                />


                                <Value
                                    label="Already Taken"
                                    value={
                                        money(
                                            opening.takenAmount
                                        )
                                    }
                                />


                                <Value
                                    label="Opening Balance"
                                    value={
                                        money(
                                            opening.netOpeningBalance
                                        )
                                    }
                                />


                                <div className="sm:col-span-2 xl:col-span-4">

                                    <p
                                        className="
                                            text-xs
                                            font-semibold
                                            uppercase
                                            tracking-0.1em
                                            text-[#756763]
                                        "
                                    >
                                        Recorded By
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-sm
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        {opening.createdByName}
                                    </p>


                                    {
                                        opening.note
                                        && (

                                            <p
                                                className="
                                                    mt-2
                                                    text-sm
                                                    leading-6
                                                    text-[#756763]
                                                "
                                            >
                                                {opening.note}
                                            </p>

                                        )
                                    }

                                </div>

                            </div>

                        )
                        : canManage
                            ? (

                                <div className="p-5">

                                    {
                                        error
                                        && (

                                            <div
                                                className="
                                                    mb-5
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


                                    <div
                                        className="
                                            grid
                                            gap-5
                                            md:grid-cols-3
                                        "
                                    >

                                        <Field label="Opening Balance Date">

                                            <input
                                                type="date"
                                                value={
                                                    asOfDate
                                                }
                                                disabled={
                                                    saving
                                                }
                                                onChange={
                                                    event =>
                                                        setAsOfDate(
                                                            event.target.value
                                                        )
                                                }
                                                className={INPUT_CLASS}
                                            />

                                        </Field>


                                        <Field label="Previously Earned">

                                            <input
                                                type="number"
                                                min="0"
                                                step="0.01"
                                                value={
                                                    earnedAmount
                                                }
                                                disabled={
                                                    saving
                                                }
                                                onChange={
                                                    event =>
                                                        setEarnedAmount(
                                                            event.target.value
                                                        )
                                                }
                                                className={INPUT_CLASS}
                                                placeholder="0.00"
                                            />

                                        </Field>


                                        <Field label="Already Taken">

                                            <input
                                                type="number"
                                                min="0"
                                                step="0.01"
                                                value={
                                                    takenAmount
                                                }
                                                disabled={
                                                    saving
                                                }
                                                onChange={
                                                    event =>
                                                        setTakenAmount(
                                                            event.target.value
                                                        )
                                                }
                                                className={INPUT_CLASS}
                                                placeholder="0.00"
                                            />

                                        </Field>

                                    </div>


                                    <div className="mt-5">

                                        <Field label="Note">

                                            <textarea
                                                rows={
                                                    3
                                                }
                                                value={
                                                    note
                                                }
                                                disabled={
                                                    saving
                                                }
                                                onChange={
                                                    event =>
                                                        setNote(
                                                            event.target.value
                                                        )
                                                }
                                                className={`${INPUT_CLASS} py-3`}
                                                placeholder="Example: Balance carried forward when payroll system started"
                                            />

                                        </Field>

                                    </div>


                                    <div
                                        className="
                                            mt-5
                                            rounded-xl
                                            border
                                            border-amber-200
                                            bg-amber-50
                                            px-4
                                            py-3
                                            text-xs
                                            leading-5
                                            text-amber-900
                                        "
                                    >
                                        Opening balance is a one-time historical record. After it is saved, future earnings should come from attendance and future money taken should use payment requests.
                                    </div>


                                    <button
                                        type="button"
                                        disabled={
                                            saving
                                        }
                                        onClick={
                                            () => {

                                                void saveOpeningBalance();
                                            }
                                        }
                                        className="
                                            mt-5
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
                                                : "Save Opening Balance"
                                        }
                                    </button>

                                </div>

                            )
                            : (

                                <div className="p-5 text-sm text-[#756763]">
                                    No historical opening balance has been configured.
                                </div>

                            )
            }

        </section>
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


function Value({
    label,
    value
}: {
    label: string;
    value: string;
}) {

    return (
        <div
            className="
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
                    tracking-0.1em
                    text-[#756763]
                "
            >
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-lg
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </div>
    );
}
