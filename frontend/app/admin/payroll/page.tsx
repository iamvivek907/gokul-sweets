"use client";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import PayrollOpeningBalancePanel
    from "@/components/admin/PayrollOpeningBalancePanel";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    createAdminPayrollCompensation,
    getAdminPayrollCompensation,
    getAdminPayrollStaffOptions,
    getAdminPayrollStaffSummary
} from "@/services/adminPayrollAdminApi";

import type {
    AdminPayrollStaffOption
} from "@/types/adminPayrollAdmin";

import type {
    PayrollSummary,
    StaffCompensation
} from "@/types/adminPayroll";


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


export default function AdminPayrollPage() {

    const {
        authorization,
        isAuthenticated,
        hasPermission
    } =
        useAdminAuth();


    const canView =
        hasPermission(
            "PAYROLL_VIEW"
        );


    const canManage =
        hasPermission(
            "PAYROLL_MANAGE"
        );


    const [
        staffOptions,
        setStaffOptions
    ] =
        useState<AdminPayrollStaffOption[]>(
            []
        );


    const [
        search,
        setSearch
    ] =
        useState(
            ""
        );


    const [
        selectedStaffId,
        setSelectedStaffId
    ] =
        useState<number | null>(
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
        compensation,
        setCompensation
    ] =
        useState<StaffCompensation[]>(
            []
        );


    const [
        effectiveFrom,
        setEffectiveFrom
    ] =
        useState(
            ""
        );


    const [
        dailyRate,
        setDailyRate
    ] =
        useState(
            ""
        );


    const [
        halfDayRate,
        setHalfDayRate
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
        detailLoading,
        setDetailLoading
    ] =
        useState(
            false
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


    const filteredStaff =
        useMemo(
            () => {

                const query =
                    search.trim()
                        .toLowerCase();


                if (!query) {

                    return staffOptions;
                }


                return staffOptions.filter(
                    staff =>
                        staff.fullName
                            .toLowerCase()
                            .includes(
                                query
                            )
                        ||
                        staff.username
                            .toLowerCase()
                            .includes(
                                query
                            )
                        ||
                        staff.roleName
                            .toLowerCase()
                            .includes(
                                query
                            )
                );
            },
            [
                search,
                staffOptions
            ]
        );


    const selectedStaff =
        useMemo(
            () =>
                staffOptions.find(
                    staff =>
                        staff.id
                        === selectedStaffId
                )
                ?? null,
            [
                selectedStaffId,
                staffOptions
            ]
        );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !isAuthenticated
                ||
                !canView
            ) {

                return;
            }


            const controller =
                new AbortController();


            getAdminPayrollStaffOptions(
                authorization,
                controller.signal
            )
                .then(
                    result => {

                        setStaffOptions(
                            result
                        );


                        setSelectedStaffId(
                            result[0]?.id
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
                                : "Unable to load payroll staff."
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
            isAuthenticated,
            canView
        ]
    );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                selectedStaffId === null
                ||
                !canView
            ) {

                return;
            }


            const controller =
                new AbortController();


            Promise.all([
                getAdminPayrollStaffSummary(
                    selectedStaffId,
                    authorization,
                    controller.signal
                ),
                getAdminPayrollCompensation(
                    selectedStaffId,
                    authorization,
                    controller.signal
                )
            ])
                .then(
                    ([
                        summaryResult,
                        compensationResult
                    ]) => {

                        setSummary(
                            summaryResult
                        );


                        setCompensation(
                            compensationResult
                        );


                        setDetailLoading(
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
                                : "Unable to load payroll details."
                        );


                        setDetailLoading(
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
            selectedStaffId,
            canView
        ]
    );


    async function selectStaff(
        staffId: number
    ) {

        setSelectedStaffId(
            staffId
        );


        setDetailLoading(
            true
        );


        setError(
            null
        );


        setSuccess(
            null
        );


        setEffectiveFrom(
            ""
        );


        setDailyRate(
            ""
        );


        setHalfDayRate(
            ""
        );
    }


    async function refreshSelected() {

        if (
            authorization === null
            ||
            selectedStaffId === null
        ) {

            return;
        }


        const [
            summaryResult,
            compensationResult
        ] =
            await Promise.all([
                getAdminPayrollStaffSummary(
                    selectedStaffId,
                    authorization
                ),
                getAdminPayrollCompensation(
                    selectedStaffId,
                    authorization
                )
            ]);


        setSummary(
            summaryResult
        );


        setCompensation(
            compensationResult
        );
    }


    async function saveCompensation() {

        if (
            authorization === null
            ||
            selectedStaffId === null
            ||
            !canManage
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


        const parsedDailyRate =
            Number(
                dailyRate
            );


        const parsedHalfDayRate =
            Number(
                halfDayRate
            );


        if (
            !effectiveFrom
        ) {

            setError(
                "Effective date is required."
            );


            return;
        }


        if (
            !Number.isFinite(
                parsedDailyRate
            )
            ||
            parsedDailyRate < 0
        ) {

            setError(
                "Enter a valid daily rate."
            );


            return;
        }


        if (
            !Number.isFinite(
                parsedHalfDayRate
            )
            ||
            parsedHalfDayRate < 0
        ) {

            setError(
                "Enter a valid half-day rate."
            );


            return;
        }


        setSaving(
            true
        );


        try {

            await createAdminPayrollCompensation(
                selectedStaffId,
                {
                    effectiveFrom,
                    dailyRate:
                        parsedDailyRate,
                    halfDayRate:
                        parsedHalfDayRate
                },
                authorization
            );


            setSuccess(
                "New compensation rate was saved."
            );


            setEffectiveFrom(
                ""
            );


            setDailyRate(
                ""
            );


            setHalfDayRate(
                ""
            );


            await refreshSelected();

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save compensation."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    if (!canView) {

        return (
            <div className="p-8 text-sm text-[#756763]">
                You do not have payroll viewing permission.
            </div>
        );
    }


    return (
        <div className="px-4 py-6 sm:px-6 sm:py-8 lg:px-8">

            <div className="mx-auto max-w-7xl">

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
                        Payroll
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
                        Review earned balances and manage effective-dated staff compensation.
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
                        gap-6
                        lg:grid-cols-[340px_minmax(0,1fr)]
                    "
                >

                    <aside className={CARD_CLASS}>

                        <div className={CARD_HEADER_CLASS}>

                            <h2 className="font-bold text-[#241715]">
                                Staff
                            </h2>


                            <input
                                type="search"
                                value={
                                    search
                                }
                                onChange={
                                    event =>
                                        setSearch(
                                            event.target.value
                                        )
                                }
                                className={`${INPUT_CLASS} mt-3`}
                                placeholder="Search staff..."
                            />

                        </div>


                        {
                            loading
                                ? (

                                    <div className="p-6 text-sm text-[#756763]">
                                        Loading staff...
                                    </div>

                                )
                                : filteredStaff.length === 0
                                    ? (

                                        <div className="p-6 text-sm text-[#756763]">
                                            No staff found.
                                        </div>

                                    )
                                    : (

                                        <div
                                            className="
                                                max-h-720px
                                                divide-y
                                                divide-[#eadfd6]
                                                overflow-y-auto
                                            "
                                        >

                                            {
                                                filteredStaff.map(
                                                    staff => (

                                                        <button
                                                            key={
                                                                staff.id
                                                            }
                                                            type="button"
                                                            onClick={
                                                                () => {

                                                                    void selectStaff(
                                                                        staff.id
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
                                                                    selectedStaffId
                                                                    === staff.id
                                                                        ? "bg-[#fff1e9]"
                                                                        : "bg-white hover:bg-[#fffaf3]"
                                                                }
                                                            `}
                                                        >

                                                            <p
                                                                className="
                                                                    font-bold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                {staff.fullName}
                                                            </p>


                                                            <p
                                                                className="
                                                                    mt-1
                                                                    text-xs
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                {staff.username} · {staff.roleName}
                                                            </p>


                                                            {
                                                                !staff.active
                                                                && (

                                                                    <span
                                                                        className="
                                                                            mt-2
                                                                            inline-flex
                                                                            rounded-full
                                                                            border
                                                                            border-gray-200
                                                                            bg-gray-100
                                                                            px-2
                                                                            py-1
                                                                            text-[11px]
                                                                            font-semibold
                                                                            text-gray-700
                                                                        "
                                                                    >
                                                                        Inactive
                                                                    </span>

                                                                )
                                                            }

                                                        </button>

                                                    )
                                                )
                                            }

                                        </div>

                                    )
                        }

                    </aside>


                    <main className="space-y-6">

                        {
                            selectedStaff === null
                                ? (

                                    <section className={CARD_CLASS}>

                                        <div className="p-8 text-sm text-[#756763]">
                                            Select a staff member to review payroll.
                                        </div>

                                    </section>

                                )
                                : (

                                    <>

                                        <section className={CARD_CLASS}>

                                            <div className={CARD_HEADER_CLASS}>

                                                <p
                                                    className="
                                                        text-xs
                                                        font-semibold
                                                        uppercase
                                                        tracking-[0.12em]
                                                        text-[#c88a20]
                                                    "
                                                >
                                                    Staff Payroll
                                                </p>


                                                <h2
                                                    className="
                                                        mt-1
                                                        text-xl
                                                        font-bold
                                                        text-[#241715]
                                                    "
                                                >
                                                    {selectedStaff.fullName}
                                                </h2>


                                                <p
                                                    className="
                                                        mt-1
                                                        text-xs
                                                        text-[#756763]
                                                    "
                                                >
                                                    {selectedStaff.username} · {selectedStaff.roleName}
                                                </p>

                                            </div>


                                            {
                                                detailLoading
                                                    ? (

                                                        <div className="p-6 text-sm text-[#756763]">
                                                            Loading payroll...
                                                        </div>

                                                    )
                                                    : (

                                                        <div
                                                            className="
                                                                grid
                                                                gap-4
                                                                p-5
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
                                                                label="Available"
                                                                value={
                                                                    summary
                                                                        ? money(
                                                                            summary.availableToRequest
                                                                        )
                                                                        : "—"
                                                                }
                                                            />

                                                        </div>

                                                    )
                                            }

                                        </section>


                                        {
                                            authorization
                                            && (

                                                <PayrollOpeningBalancePanel
                                                    staffUserId={
                                                        selectedStaff.id
                                                    }
                                                    authorization={
                                                        authorization
                                                    }
                                                    canManage={
                                                        canManage
                                                    }
                                                    onSaved={
                                                        refreshSelected
                                                    }
                                                />

                                            )
                                        }


                                        {
                                            canManage
                                            && (

                                                <section className={CARD_CLASS}>

                                                    <div className={CARD_HEADER_CLASS}>

                                                        <h2 className="font-bold text-[#241715]">
                                                            Set New Compensation Rate
                                                        </h2>


                                                        <p
                                                            className="
                                                                mt-1
                                                                text-xs
                                                                text-[#756763]
                                                            "
                                                        >
                                                            Existing historical rates remain unchanged.
                                                        </p>

                                                    </div>


                                                    <div
                                                        className="
                                                            grid
                                                            gap-5
                                                            p-5
                                                            md:grid-cols-3
                                                        "
                                                    >

                                                        <Field label="Effective From">

                                                            <input
                                                                type="date"
                                                                value={
                                                                    effectiveFrom
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        setEffectiveFrom(
                                                                            event.target.value
                                                                        )
                                                                }
                                                                className={INPUT_CLASS}
                                                            />

                                                        </Field>


                                                        <Field label="Daily Rate">

                                                            <input
                                                                type="number"
                                                                min="0"
                                                                step="0.01"
                                                                value={
                                                                    dailyRate
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        setDailyRate(
                                                                            event.target.value
                                                                        )
                                                                }
                                                                className={INPUT_CLASS}
                                                                placeholder="0.00"
                                                            />

                                                        </Field>


                                                        <Field label="Half-day Rate">

                                                            <input
                                                                type="number"
                                                                min="0"
                                                                step="0.01"
                                                                value={
                                                                    halfDayRate
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        setHalfDayRate(
                                                                            event.target.value
                                                                        )
                                                                }
                                                                className={INPUT_CLASS}
                                                                placeholder="0.00"
                                                            />

                                                        </Field>


                                                        <div className="md:col-span-3">

                                                            <button
                                                                type="button"
                                                                disabled={
                                                                    saving
                                                                }
                                                                onClick={
                                                                    () => {

                                                                        void saveCompensation();
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
                                                                        : "Save New Rate"
                                                                }
                                                            </button>

                                                        </div>

                                                    </div>

                                                </section>

                                            )
                                        }


                                        <section className={CARD_CLASS}>

                                            <div className={CARD_HEADER_CLASS}>

                                                <h2 className="font-bold text-[#241715]">
                                                    Compensation History
                                                </h2>

                                            </div>


                                            {
                                                compensation.length === 0
                                                    ? (

                                                        <div className="p-6 text-sm text-[#756763]">
                                                            No compensation rate has been configured yet.
                                                        </div>

                                                    )
                                                    : (

                                                        <div className="divide-y divide-[#eadfd6]">

                                                            {
                                                                compensation.map(
                                                                    item => (

                                                                        <div
                                                                            key={
                                                                                item.id
                                                                            }
                                                                            className="
                                                                                grid
                                                                                gap-3
                                                                                p-5
                                                                                sm:grid-cols-4
                                                                            "
                                                                        >

                                                                            <div>

                                                                                <p className={META_LABEL_CLASS}>
                                                                                    Effective From
                                                                                </p>


                                                                                <p className={META_VALUE_CLASS}>
                                                                                    {formatDate(item.effectiveFrom)}
                                                                                </p>

                                                                            </div>


                                                                            <div>

                                                                                <p className={META_LABEL_CLASS}>
                                                                                    Daily
                                                                                </p>


                                                                                <p className={META_VALUE_CLASS}>
                                                                                    {money(item.dailyRate)}
                                                                                </p>

                                                                            </div>


                                                                            <div>

                                                                                <p className={META_LABEL_CLASS}>
                                                                                    Half Day
                                                                                </p>


                                                                                <p className={META_VALUE_CLASS}>
                                                                                    {money(item.halfDayRate)}
                                                                                </p>

                                                                            </div>


                                                                            <div>

                                                                                <p className={META_LABEL_CLASS}>
                                                                                    Set By
                                                                                </p>


                                                                                <p className={META_VALUE_CLASS}>
                                                                                    {item.createdByName}
                                                                                </p>

                                                                            </div>

                                                                        </div>

                                                                    )
                                                                )
                                                            }

                                                        </div>

                                                    )
                                            }

                                        </section>

                                    </>

                                )
                        }

                    </main>

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


const META_LABEL_CLASS =
    `
        text-xs
        font-semibold
        uppercase
        tracking-[0.1em]
        text-[#756763]
    `;


const META_VALUE_CLASS =
    `
        mt-1
        text-sm
        font-semibold
        text-[#241715]
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
                rounded-xl
                border
                border-[#eadfd6]
                bg-[#fffaf3]
                p-4
            "
        >

            <p className={META_LABEL_CLASS}>
                {label}
            </p>


            <p
                className="
                    mt-2
                    text-xl
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
