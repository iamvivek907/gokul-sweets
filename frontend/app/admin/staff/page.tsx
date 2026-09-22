"use client";

import {
    useEffect,
    useMemo,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    createAdminStaff,
    getAdminStaff,
    getAdminStaffOptions,
    resetAdminStaffPassword,
    updateAdminStaff
} from "@/services/adminStaffApi";

import type {
    AdminStaff,
    AdminStaffManagementOptions,
    CreateAdminStaffRequest,
    UpdateAdminStaffRequest
} from "@/types/adminStaff";


interface StaffFormState {

    username: string;

    password: string;

    confirmPassword: string;

    fullName: string;

    phone: string;

    roleName: string;

    branchIds: number[];

    active: boolean;

    payrollEnabled: boolean;

    payrollEffectiveFrom: string;

    dailyRate: string;

    halfDayRate: string;

    openingBalanceEnabled: boolean;

    openingAsOfDate: string;

    openingEarnedAmount: string;

    openingTakenAmount: string;

    openingNote: string;
}


const EMPTY_FORM: StaffFormState = {

    username: "",

    password: "",

    confirmPassword: "",

    fullName: "",

    phone: "",

    roleName: "",

    branchIds: [],

    active: true,

    payrollEnabled: true,

    payrollEffectiveFrom:
        todayInBusinessZone(),

    dailyRate: "",

    halfDayRate: "",

    openingBalanceEnabled: false,

    openingAsOfDate: "",

    openingEarnedAmount: "",

    openingTakenAmount: "",

    openingNote: ""
};


function toFormState(
    staff: AdminStaff
): StaffFormState {

    return {
        username:
            staff.username,

        password: "",

        confirmPassword: "",

        fullName:
            staff.fullName,

        phone:
            staff.phone
            ?? "",

        roleName:
            staff.roleName,

        branchIds:
            [...staff.branchIds],

        active:
            staff.active,

        payrollEnabled:
            false,

        payrollEffectiveFrom:
            todayInBusinessZone(),

        dailyRate:
            "",

        halfDayRate:
            "",

        openingBalanceEnabled:
            false,

        openingAsOfDate:
            "",

        openingEarnedAmount:
            "",

        openingTakenAmount:
            "",

        openingNote:
            ""
    };
}


function nullableText(
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


export default function AdminStaffPage() {

    const {
        profile,
        authorization,
        hasPermission
    } =
        useAdminAuth();


    const canManage =
        hasPermission(
            "STAFF_MANAGE"
        );


    const canManagePayroll =
        hasPermission(
            "PAYROLL_MANAGE"
        );


    const [
        staff,
        setStaff
    ] =
        useState<AdminStaff[]>(
            []
        );


    const [
        options,
        setOptions
    ] =
        useState<AdminStaffManagementOptions | null>(
            null
        );


    const [
        selectedStaffId,
        setSelectedStaffId
    ] =
        useState<number | null>(
            null
        );


    const [
        form,
        setForm
    ] =
        useState<StaffFormState>(
            EMPTY_FORM
        );


    const [
        createMode,
        setCreateMode
    ] =
        useState(
            false
        );


    const [
        search,
        setSearch
    ] =
        useState(
            ""
        );


    const [
        statusFilter,
        setStatusFilter
    ] =
        useState<
            "ALL"
            | "ACTIVE"
            | "INACTIVE"
        >(
            "ALL"
        );


    const [
        roleFilter,
        setRoleFilter
    ] =
        useState(
            "ALL"
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


    const [
        passwordResetOpen,
        setPasswordResetOpen
    ] =
        useState(
            false
        );


    const [
        newPassword,
        setNewPassword
    ] =
        useState(
            ""
        );


    const [
        confirmNewPassword,
        setConfirmNewPassword
    ] =
        useState(
            ""
        );


    const selectedStaff =
        useMemo(
            () =>
                staff.find(
                    item =>
                        item.id
                        === selectedStaffId
                )
                ?? null,
            [
                staff,
                selectedStaffId
            ]
        );


    const filteredStaff =
        useMemo(
            () => {

                const normalizedSearch =
                    search
                        .trim()
                        .toLowerCase();


                return staff.filter(
                    item => {

                        if (
                            statusFilter === "ACTIVE"
                            &&
                            !item.active
                        ) {

                            return false;
                        }


                        if (
                            statusFilter === "INACTIVE"
                            &&
                            item.active
                        ) {

                            return false;
                        }


                        if (
                            roleFilter !== "ALL"
                            &&
                            item.roleName
                            !== roleFilter
                        ) {

                            return false;
                        }


                        if (!normalizedSearch) {

                            return true;
                        }


                        return (
                            item.fullName
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                            ||
                            item.username
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                            ||
                            (
                                item.phone
                                ?? ""
                            )
                                .toLowerCase()
                                .includes(
                                    normalizedSearch
                                )
                        );
                    }
                );
            },
            [
                staff,
                search,
                statusFilter,
                roleFilter
            ]
        );


    useEffect(
        () => {

            if (
                authorization === null
                ||
                !canManage
            ) {

                return;
            }


            const controller =
                new AbortController();


            Promise.all([
                getAdminStaff(
                    authorization,
                    controller.signal
                ),
                getAdminStaffOptions(
                    authorization,
                    controller.signal
                )
            ])
                .then(
                    ([
                        staffResult,
                        optionsResult
                    ]) => {

                        const firstStaff =
                            staffResult[0]
                            ?? null;


                        setStaff(
                            staffResult
                        );


                        setOptions(
                            optionsResult
                        );


                        setSelectedStaffId(
                            firstStaff?.id
                            ?? null
                        );


                        setForm(
                            firstStaff
                                ? toFormState(
                                    firstStaff
                                )
                                : EMPTY_FORM
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
                                : "Unable to load staff management."
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
            canManage
        ]
    );


    function updateField<
        K extends keyof StaffFormState
    >(
        field: K,
        value: StaffFormState[K]
    ) {

        setForm(
            current => ({
                ...current,
                [field]: value
            })
        );


        setSuccess(
            null
        );
    }


    function selectStaff(
        item: AdminStaff
    ) {

        setCreateMode(
            false
        );


        setSelectedStaffId(
            item.id
        );


        setForm(
            toFormState(
                item
            )
        );


        setPasswordResetOpen(
            false
        );


        setNewPassword(
            ""
        );


        setConfirmNewPassword(
            ""
        );


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    function beginCreate() {

        setCreateMode(
            true
        );


        setSelectedStaffId(
            null
        );


        setForm({
            ...EMPTY_FORM,

            roleName:
                options?.roles[0]?.name
                ?? "",

            payrollEnabled:
                canManagePayroll,

            payrollEffectiveFrom:
                todayInBusinessZone()
        });


        setPasswordResetOpen(
            false
        );


        setNewPassword(
            ""
        );


        setConfirmNewPassword(
            ""
        );


        setError(
            null
        );


        setSuccess(
            null
        );
    }


    async function refreshStaff(
        preferredStaffId?: number | null
    ) {

        if (
            authorization === null
        ) {

            return;
        }


        const staffResult =
            await getAdminStaff(
                authorization
            );


        setStaff(
            staffResult
        );


        const nextStaff =
            preferredStaffId !== undefined
                ? staffResult.find(
                    item =>
                        item.id
                        === preferredStaffId
                )
                ?? staffResult[0]
                ?? null
                : staffResult[0]
                ?? null;


        setSelectedStaffId(
            nextStaff?.id
            ?? null
        );


        setForm(
            nextStaff
                ? toFormState(
                    nextStaff
                )
                : EMPTY_FORM
        );
    }


    async function handleSave() {

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


        setSuccess(
            null
        );


        try {

            if (
                !form.fullName.trim()
            ) {

                throw new Error(
                    "Full name is required."
                );
            }


            if (
                !form.roleName
            ) {

                throw new Error(
                    "Role is required."
                );
            }


            const selectedRole =
                options?.roles.find(
                    role =>
                        role.name
                        === form.roleName
                )
                ?? null;


            const ownerRole =
                selectedRole?.name
                === "OWNER_ADMIN";


            if (
                !ownerRole
                &&
                form.branchIds.length === 0
            ) {

                throw new Error(
                    "At least one branch must be assigned."
                );
            }


            if (
                createMode
            ) {

                if (
                    !form.username.trim()
                ) {

                    throw new Error(
                        "Username is required."
                    );
                }


                if (
                    form.password.length < 8
                ) {

                    throw new Error(
                        "Password must be at least 8 characters."
                    );
                }


                if (
                    form.password
                    !== form.confirmPassword
                ) {

                    throw new Error(
                        "Password and confirm password do not match."
                    );
                }


                if (
                    form.payrollEnabled
                    &&
                    !canManagePayroll
                ) {

                    throw new Error(
                        "You do not have permission to configure payroll."
                    );
                }


                let payroll:
                    {
                        effectiveFrom: string;
                        dailyRate: number;
                        halfDayRate: number;
                        openingBalance:
                            {
                                asOfDate: string;
                                earnedAmount: number;
                                takenAmount: number;
                                note: string | null;
                            }
                            | null;
                    }
                    | null =
                    null;


                if (
                    form.payrollEnabled
                ) {

                    if (
                        !form.payrollEffectiveFrom
                    ) {

                        throw new Error(
                            "Payroll effective date is required."
                        );
                    }


                    const dailyRate =
                        Number(
                            form.dailyRate
                        );


                    const halfDayRate =
                        Number(
                            form.halfDayRate
                        );


                    if (
                        !Number.isFinite(
                            dailyRate
                        )
                        ||
                        dailyRate < 0
                    ) {

                        throw new Error(
                            "Enter a valid daily rate."
                        );
                    }


                    if (
                        !Number.isFinite(
                            halfDayRate
                        )
                        ||
                        halfDayRate < 0
                    ) {

                        throw new Error(
                            "Enter a valid half-day rate."
                        );
                    }


                    let openingBalance:
                        {
                            asOfDate: string;
                            earnedAmount: number;
                            takenAmount: number;
                            note: string | null;
                        }
                        | null =
                        null;


                    if (
                        form.openingBalanceEnabled
                    ) {

                        if (
                            !form.openingAsOfDate
                        ) {

                            throw new Error(
                                "Opening balance date is required."
                            );
                        }


                        const earnedAmount =
                            Number(
                                form.openingEarnedAmount
                            );


                        const takenAmount =
                            Number(
                                form.openingTakenAmount
                            );


                        if (
                            !Number.isFinite(
                                earnedAmount
                            )
                            ||
                            earnedAmount < 0
                        ) {

                            throw new Error(
                                "Enter a valid previously earned amount."
                            );
                        }


                        if (
                            !Number.isFinite(
                                takenAmount
                            )
                            ||
                            takenAmount < 0
                        ) {

                            throw new Error(
                                "Enter a valid previously taken amount."
                            );
                        }


                        if (
                            takenAmount > earnedAmount
                        ) {

                            throw new Error(
                                "Previously taken amount cannot be more than previously earned amount."
                            );
                        }


                        openingBalance = {

                            asOfDate:
                                form.openingAsOfDate,

                            earnedAmount,

                            takenAmount,

                            note:
                                nullableText(
                                    form.openingNote
                                )
                        };
                    }


                    payroll = {

                        effectiveFrom:
                            form.payrollEffectiveFrom,

                        dailyRate,

                        halfDayRate,

                        openingBalance
                    };
                }


                const request:
                    CreateAdminStaffRequest
                    & {
                        payroll:
                            typeof payroll;
                    } = {

                    username:
                        form.username.trim(),

                    password:
                        form.password,

                    fullName:
                        form.fullName.trim(),

                    phone:
                        nullableText(
                            form.phone
                        ),

                    roleName:
                        form.roleName,

                    branchIds:
                        ownerRole
                            ? []
                            : form.branchIds,

                    payroll
                };


                const created =
                    await createAdminStaff(
                        request,
                        authorization
                    );


                setCreateMode(
                    false
                );


                setSuccess(
                    `${created.fullName} was added successfully.`
                );


                await refreshStaff(
                    created.id
                );


                return;
            }


            if (
                selectedStaffId === null
            ) {

                return;
            }


            const request:
                UpdateAdminStaffRequest = {

                fullName:
                    form.fullName.trim(),

                phone:
                    nullableText(
                        form.phone
                    ),

                roleName:
                    form.roleName,

                branchIds:
                    ownerRole
                        ? []
                        : form.branchIds,

                active:
                    form.active
            };


            const updated =
                await updateAdminStaff(
                    selectedStaffId,
                    request,
                    authorization
                );


            setSuccess(
                `${updated.fullName} was updated successfully.`
            );


            await refreshStaff(
                updated.id
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save staff member."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function handlePasswordReset() {

        if (
            authorization === null
            ||
            selectedStaff === null
            ||
            saving
        ) {

            return;
        }


        if (
            newPassword.length < 8
        ) {

            setError(
                "New password must be at least 8 characters."
            );


            return;
        }


        if (
            newPassword
            !== confirmNewPassword
        ) {

            setError(
                "New password and confirm password do not match."
            );


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

            await resetAdminStaffPassword(
                selectedStaff.id,
                newPassword,
                authorization
            );


            setPasswordResetOpen(
                false
            );


            setNewPassword(
                ""
            );


            setConfirmNewPassword(
                ""
            );


            setSuccess(
                `Password reset for ${selectedStaff.fullName}.`
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to reset password."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    function toggleBranch(
        branchId: number
    ) {

        updateField(
            "branchIds",
            form.branchIds.includes(
                branchId
            )
                ? form.branchIds.filter(
                    id =>
                        id !== branchId
                )
                : [
                    ...form.branchIds,
                    branchId
                ]
        );
    }


    if (
        profile
        &&
        !canManage
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
                    You do not have permission to manage staff.
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
                            Staff Management
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
                            Add staff, manage access levels, branch access and account status.
                        </p>

                    </div>


                    <button
                        type="button"
                        disabled={
                            loading
                            ||
                            saving
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
                        Add Staff
                    </button>

                </div>


                <div
                    className="
                        mt-6
                        grid
                        gap-3
                        sm:grid-cols-3
                    "
                >

                    <SummaryCard
                        label="Accessible Staff"
                        value={
                            staff.length
                        }
                    />


                    <SummaryCard
                        label="Active"
                        value={
                            staff.filter(
                                item =>
                                    item.active
                            ).length
                        }
                    />


                    <SummaryCard
                        label="Inactive"
                        value={
                            staff.filter(
                                item =>
                                    !item.active
                            ).length
                        }
                    />

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
                                p-4
                            "
                        >

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
                                placeholder="Search name, username or phone"
                                className={INPUT_CLASS}
                            />


                            <div
                                className="
                                    mt-3
                                    grid
                                    grid-cols-2
                                    gap-2
                                "
                            >

                                <select
                                    value={
                                        statusFilter
                                    }
                                    onChange={
                                        event =>
                                            setStatusFilter(
                                                event.target.value as
                                                    | "ALL"
                                                    | "ACTIVE"
                                                    | "INACTIVE"
                                            )
                                    }
                                    className={INPUT_CLASS}
                                >
                                    <option value="ALL">
                                        All status
                                    </option>

                                    <option value="ACTIVE">
                                        Active
                                    </option>

                                    <option value="INACTIVE">
                                        Inactive
                                    </option>
                                </select>


                                <select
                                    value={
                                        roleFilter
                                    }
                                    onChange={
                                        event =>
                                            setRoleFilter(
                                                event.target.value
                                            )
                                    }
                                    className={INPUT_CLASS}
                                >
                                    <option value="ALL">
                                        All roles
                                    </option>

                                    {
                                        options?.roles.map(
                                            role => (

                                                <option
                                                    key={
                                                        role.name
                                                    }
                                                    value={
                                                        role.name
                                                    }
                                                >
                                                    {role.name}
                                                </option>

                                            )
                                        )
                                    }
                                </select>

                            </div>

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
                                            No staff match these filters.
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
                                                filteredStaff.map(
                                                    item => (

                                                        <button
                                                            key={
                                                                item.id
                                                            }
                                                            type="button"
                                                            onClick={
                                                                () =>
                                                                    selectStaff(
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
                                                                    selectedStaffId === item.id
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
                                                                            truncate
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {item.fullName}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            truncate
                                                                            text-xs
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        @{item.username} · {item.roleName}
                                                                    </p>

                                                                </div>


                                                                <span
                                                                    className={`
                                                                        shrink-0
                                                                        rounded-full
                                                                        px-2.5
                                                                        py-1
                                                                        text-xs
                                                                        font-semibold

                                                                        ${
                                                                            item.active
                                                                                ? "bg-green-50 text-green-800"
                                                                                : "bg-gray-100 text-gray-700"
                                                                        }
                                                                    `}
                                                                >
                                                                    {
                                                                        item.active
                                                                            ? "Active"
                                                                            : "Inactive"
                                                                    }
                                                                </span>

                                                            </div>

                                                        </button>

                                                    )
                                                )
                                            }

                                        </div>

                                    )
                        }

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
                                        ? "New staff member"
                                        : "Staff details"
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
                                        ? "Add Staff"
                                        : selectedStaff?.fullName
                                        ?? "Select a staff member"
                                }
                            </h2>

                        </div>


                        {
                            !createMode
                            &&
                            selectedStaff === null
                                ? (

                                    <div className="p-8 text-sm text-[#756763]">
                                        Select a staff member to manage their account.
                                    </div>

                                )
                                : (

                                    <div className="p-5 sm:p-6">

                                        <div
                                            className="
                                                grid
                                                gap-5
                                                md:grid-cols-2
                                            "
                                        >

                                            <Field label="Username">

                                                <input
                                                    value={
                                                        form.username
                                                    }
                                                    disabled={
                                                        !createMode
                                                        ||
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "username",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            {
                                                createMode
                                                && (

                                                    <>

                                                        <Field label="Temporary Password">

                                                            <input
                                                                type="password"
                                                                value={
                                                                    form.password
                                                                }
                                                                disabled={
                                                                    saving
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        updateField(
                                                                            "password",
                                                                            event.target.value
                                                                        )
                                                                }
                                                                className={INPUT_CLASS}
                                                            />

                                                        </Field>


                                                        <Field label="Confirm Password">

                                                            <input
                                                                type="password"
                                                                value={
                                                                    form.confirmPassword
                                                                }
                                                                disabled={
                                                                    saving
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        updateField(
                                                                            "confirmPassword",
                                                                            event.target.value
                                                                        )
                                                                }
                                                                className={INPUT_CLASS}
                                                                placeholder="Re-enter initial password"
                                                            />

                                                        </Field>

                                                    </>

                                                )
                                            }


                                            <Field label="Full Name">

                                                <input
                                                    value={
                                                        form.fullName
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "fullName",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            <Field label="Phone">

                                                <input
                                                    value={
                                                        form.phone
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "phone",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </Field>


                                            <Field label="Access Level / Role">

                                                <select
                                                    value={
                                                        form.roleName
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "roleName",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                >

                                                    <option value="">
                                                        Select role
                                                    </option>


                                                    {
                                                        options?.roles.map(
                                                            role => (

                                                                <option
                                                                    key={
                                                                        role.name
                                                                    }
                                                                    value={
                                                                        role.name
                                                                    }
                                                                >
                                                                    {role.name}
                                                                </option>

                                                            )
                                                        )
                                                    }

                                                </select>

                                            </Field>


                                            {
                                                !createMode
                                                && (

                                                    <Field label="Account Status">

                                                        <select
                                                            value={
                                                                form.active
                                                                    ? "ACTIVE"
                                                                    : "INACTIVE"
                                                            }
                                                            disabled={
                                                                saving
                                                            }
                                                            onChange={
                                                                event =>
                                                                    updateField(
                                                                        "active",
                                                                        event.target.value
                                                                            === "ACTIVE"
                                                                    )
                                                            }
                                                            className={INPUT_CLASS}
                                                        >
                                                            <option value="ACTIVE">
                                                                Active
                                                            </option>

                                                            <option value="INACTIVE">
                                                                Inactive
                                                            </option>
                                                        </select>

                                                    </Field>

                                                )
                                            }

                                        </div>


                                        {
                                            form.roleName
                                            !== "OWNER_ADMIN"
                                            && (

                                                <div className="mt-6">

                                                    <div
                                                        className="
                                                            flex
                                                            items-end
                                                            justify-between
                                                            gap-3
                                                        "
                                                    >

                                                        <div>

                                                            <h3
                                                                className="
                                                                    text-sm
                                                                    font-bold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                Branch Access
                                                            </h3>


                                                            <p
                                                                className="
                                                                    mt-1
                                                                    text-xs
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Choose every branch this staff member is allowed to access.
                                                            </p>

                                                        </div>

                                                    </div>


                                                    <div
                                                        className="
                                                            mt-3
                                                            grid
                                                            gap-3
                                                            sm:grid-cols-2
                                                        "
                                                    >

                                                        {
                                                            options?.branches.map(
                                                                branch => {

                                                                    const checked =
                                                                        form.branchIds.includes(
                                                                            branch.id
                                                                        );


                                                                    return (
                                                                        <label
                                                                            key={
                                                                                branch.id
                                                                            }
                                                                            className={`
                                                                                flex
                                                                                cursor-pointer
                                                                                items-start
                                                                                gap-3
                                                                                rounded-xl
                                                                                border
                                                                                p-4

                                                                                ${
                                                                                    checked
                                                                                        ? "border-[#c88a20] bg-[#fff8e8]"
                                                                                        : "border-[#eadfd6] bg-white"
                                                                                }
                                                                            `}
                                                                        >

                                                                            <input
                                                                                type="checkbox"
                                                                                checked={
                                                                                    checked
                                                                                }
                                                                                disabled={
                                                                                    saving
                                                                                }
                                                                                onChange={
                                                                                    () =>
                                                                                        toggleBranch(
                                                                                            branch.id
                                                                                        )
                                                                                }
                                                                                className="mt-1"
                                                                            />


                                                                            <span>

                                                                                <span
                                                                                    className="
                                                                                        block
                                                                                        text-sm
                                                                                        font-semibold
                                                                                        text-[#241715]
                                                                                    "
                                                                                >
                                                                                    {branch.name}
                                                                                </span>


                                                                                <span
                                                                                    className="
                                                                                        mt-1
                                                                                        block
                                                                                        text-xs
                                                                                        text-[#756763]
                                                                                    "
                                                                                >
                                                                                    {branch.code}
                                                                                    {
                                                                                        !branch.active
                                                                                            ? " · inactive branch"
                                                                                            : ""
                                                                                    }
                                                                                </span>

                                                                            </span>

                                                                        </label>
                                                                    );
                                                                }
                                                            )
                                                        }

                                                    </div>

                                                </div>

                                            )
                                        }


                                        {
                                            createMode
                                            && (

                                                <div
                                                    className="
                                                        mt-6
                                                        rounded-2xl
                                                        border
                                                        border-[#eadfd6]
                                                        bg-[#fffaf3]
                                                        p-5
                                                    "
                                                >

                                                    <div
                                                        className="
                                                            flex
                                                            flex-col
                                                            gap-4
                                                            sm:flex-row
                                                            sm:items-start
                                                            sm:justify-between
                                                        "
                                                    >

                                                        <div>

                                                            <h3
                                                                className="
                                                                    text-base
                                                                    font-bold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                Payroll Setup
                                                            </h3>


                                                            <p
                                                                className="
                                                                    mt-1
                                                                    max-w-2xl
                                                                    text-xs
                                                                    leading-5
                                                                    text-[#756763]
                                                                "
                                                            >
                                                                Configure the first salary rate now so approved attendance can start generating earnings immediately.
                                                            </p>

                                                        </div>


                                                        <label
                                                            className={`
                                                                inline-flex
                                                                min-h-11
                                                                items-center
                                                                gap-3
                                                                rounded-xl
                                                                border
                                                                px-4
                                                                text-sm
                                                                font-semibold

                                                                ${
                                                                    form.payrollEnabled
                                                                        ? "border-[#c88a20] bg-white text-[#241715]"
                                                                        : "border-[#eadfd6] bg-white text-[#756763]"
                                                                }
                                                            `}
                                                        >

                                                            <input
                                                                type="checkbox"
                                                                checked={
                                                                    form.payrollEnabled
                                                                }
                                                                disabled={
                                                                    saving
                                                                    ||
                                                                    !canManagePayroll
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        updateField(
                                                                            "payrollEnabled",
                                                                            event.target.checked
                                                                        )
                                                                }
                                                            />

                                                            Enable Payroll

                                                        </label>

                                                    </div>


                                                    {
                                                        !canManagePayroll
                                                        && (

                                                            <div
                                                                className="
                                                                    mt-4
                                                                    rounded-xl
                                                                    border
                                                                    border-amber-200
                                                                    bg-amber-50
                                                                    px-4
                                                                    py-3
                                                                    text-xs
                                                                    leading-5
                                                                    text-amber-800
                                                                "
                                                            >
                                                                You can create the staff account, but payroll setup requires PAYROLL_MANAGE permission.
                                                            </div>

                                                        )
                                                    }


                                                    {
                                                        form.payrollEnabled
                                                        && (

                                                            <>

                                                                <div
                                                                    className="
                                                                        mt-5
                                                                        grid
                                                                        gap-5
                                                                        md:grid-cols-3
                                                                    "
                                                                >

                                                                    <Field label="Effective From">

                                                                        <input
                                                                            type="date"
                                                                            value={
                                                                                form.payrollEffectiveFrom
                                                                            }
                                                                            disabled={
                                                                                saving
                                                                            }
                                                                            onChange={
                                                                                event =>
                                                                                    updateField(
                                                                                        "payrollEffectiveFrom",
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
                                                                                form.dailyRate
                                                                            }
                                                                            disabled={
                                                                                saving
                                                                            }
                                                                            onChange={
                                                                                event =>
                                                                                    updateField(
                                                                                        "dailyRate",
                                                                                        event.target.value
                                                                                    )
                                                                            }
                                                                            className={INPUT_CLASS}
                                                                            placeholder="500.00"
                                                                        />

                                                                    </Field>


                                                                    <Field label="Half-Day Rate">

                                                                        <input
                                                                            type="number"
                                                                            min="0"
                                                                            step="0.01"
                                                                            value={
                                                                                form.halfDayRate
                                                                            }
                                                                            disabled={
                                                                                saving
                                                                            }
                                                                            onChange={
                                                                                event =>
                                                                                    updateField(
                                                                                        "halfDayRate",
                                                                                        event.target.value
                                                                                    )
                                                                            }
                                                                            className={INPUT_CLASS}
                                                                            placeholder="250.00"
                                                                        />

                                                                    </Field>

                                                                </div>


                                                                <div
                                                                    className="
                                                                        mt-5
                                                                        border-t
                                                                        border-[#eadfd6]
                                                                        pt-5
                                                                    "
                                                                >

                                                                    <label
                                                                        className="
                                                                            flex
                                                                            cursor-pointer
                                                                            items-start
                                                                            gap-3
                                                                            rounded-xl
                                                                            border
                                                                            border-[#eadfd6]
                                                                            bg-white
                                                                            p-4
                                                                        "
                                                                    >

                                                                        <input
                                                                            type="checkbox"
                                                                            checked={
                                                                                form.openingBalanceEnabled
                                                                            }
                                                                            disabled={
                                                                                saving
                                                                            }
                                                                            onChange={
                                                                                event =>
                                                                                    updateField(
                                                                                        "openingBalanceEnabled",
                                                                                        event.target.checked
                                                                                    )
                                                                            }
                                                                            className="mt-1"
                                                                        />


                                                                        <span>

                                                                            <span
                                                                                className="
                                                                                    block
                                                                                    text-sm
                                                                                    font-bold
                                                                                    text-[#241715]
                                                                                "
                                                                            >
                                                                                Existing employee — carry forward previous payroll balance
                                                                            </span>


                                                                            <span
                                                                                className="
                                                                                    mt-1
                                                                                    block
                                                                                    text-xs
                                                                                    leading-5
                                                                                    text-[#756763]
                                                                                "
                                                                            >
                                                                                Use this only when the employee already worked before this payroll system started.
                                                                            </span>

                                                                        </span>

                                                                    </label>


                                                                    {
                                                                        form.openingBalanceEnabled
                                                                        && (

                                                                            <div
                                                                                className="
                                                                                    mt-4
                                                                                    grid
                                                                                    gap-5
                                                                                    md:grid-cols-3
                                                                                "
                                                                            >

                                                                                <Field label="Opening Balance Date">

                                                                                    <input
                                                                                        type="date"
                                                                                        value={
                                                                                            form.openingAsOfDate
                                                                                        }
                                                                                        disabled={
                                                                                            saving
                                                                                        }
                                                                                        onChange={
                                                                                            event =>
                                                                                                updateField(
                                                                                                    "openingAsOfDate",
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
                                                                                            form.openingEarnedAmount
                                                                                        }
                                                                                        disabled={
                                                                                            saving
                                                                                        }
                                                                                        onChange={
                                                                                            event =>
                                                                                                updateField(
                                                                                                    "openingEarnedAmount",
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
                                                                                            form.openingTakenAmount
                                                                                        }
                                                                                        disabled={
                                                                                            saving
                                                                                        }
                                                                                        onChange={
                                                                                            event =>
                                                                                                updateField(
                                                                                                    "openingTakenAmount",
                                                                                                    event.target.value
                                                                                                )
                                                                                        }
                                                                                        className={INPUT_CLASS}
                                                                                        placeholder="0.00"
                                                                                    />

                                                                                </Field>


                                                                                <div className="md:col-span-3">

                                                                                    <Field label="Opening Balance Note">

                                                                                        <textarea
                                                                                            rows={
                                                                                                3
                                                                                            }
                                                                                            value={
                                                                                                form.openingNote
                                                                                            }
                                                                                            disabled={
                                                                                                saving
                                                                                            }
                                                                                            onChange={
                                                                                                event =>
                                                                                                    updateField(
                                                                                                        "openingNote",
                                                                                                        event.target.value
                                                                                                    )
                                                                                            }
                                                                                            className={`${INPUT_CLASS} py-3`}
                                                                                            placeholder="Example: Balance carried forward when payroll system started"
                                                                                        />

                                                                                    </Field>

                                                                                </div>

                                                                            </div>

                                                                        )
                                                                    }

                                                                </div>

                                                            </>

                                                        )
                                                    }

                                                </div>

                                            )
                                        }


                                        {
                                            form.roleName
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
                                                        Role permissions
                                                    </p>


                                                    <div
                                                        className="
                                                            mt-3
                                                            flex
                                                            flex-wrap
                                                            gap-2
                                                        "
                                                    >

                                                        {
                                                            options?.roles
                                                                .find(
                                                                    role =>
                                                                        role.name
                                                                        === form.roleName
                                                                )
                                                                ?.permissions
                                                                .map(
                                                                    permission => (

                                                                        <span
                                                                            key={
                                                                                permission
                                                                            }
                                                                            className="
                                                                                rounded-full
                                                                                bg-white
                                                                                px-3
                                                                                py-1
                                                                                text-xs
                                                                                font-semibold
                                                                                text-[#756763]
                                                                            "
                                                                        >
                                                                            {permission}
                                                                        </span>

                                                                    )
                                                                )
                                                        }

                                                    </div>

                                                </div>

                                            )
                                        }


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

                                                        void handleSave();
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
                                                            ? "Create Staff"
                                                            : "Save Changes"
                                                }
                                            </button>


                                            {
                                                !createMode
                                                &&
                                                selectedStaff
                                                && (

                                                    <button
                                                        type="button"
                                                        disabled={
                                                            saving
                                                        }
                                                        onClick={
                                                            () => {

                                                                setPasswordResetOpen(
                                                                    current =>
                                                                        !current
                                                                );


                                                                setNewPassword(
                                                                    ""
                                                                );


                                                                setConfirmNewPassword(
                                                                    ""
                                                                );


                                                                setError(
                                                                    null
                                                                );
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
                                                        Reset Password
                                                    </button>

                                                )
                                            }


                                            {
                                                createMode
                                                && (

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


                                                                const firstStaff =
                                                                    staff[0]
                                                                    ?? null;


                                                                setSelectedStaffId(
                                                                    firstStaff?.id
                                                                    ?? null
                                                                );


                                                                setForm(
                                                                    firstStaff
                                                                        ? toFormState(
                                                                            firstStaff
                                                                        )
                                                                        : EMPTY_FORM
                                                                );
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
                                                        "
                                                    >
                                                        Cancel
                                                    </button>

                                                )
                                            }

                                        </div>


                                        {
                                            passwordResetOpen
                                            &&
                                            selectedStaff
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
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        Reset password for {selectedStaff.fullName}
                                                    </p>


                                                    <div
                                                        className="
                                                            mt-3
                                                            grid
                                                            gap-3
                                                            md:grid-cols-2
                                                        "
                                                    >

                                                        <input
                                                            type="password"
                                                            value={
                                                                newPassword
                                                            }
                                                            disabled={
                                                                saving
                                                            }
                                                            onChange={
                                                                event =>
                                                                    setNewPassword(
                                                                        event.target.value
                                                                    )
                                                            }
                                                            placeholder="New password (minimum 8 characters)"
                                                            className={INPUT_CLASS}
                                                        />


                                                        <input
                                                            type="password"
                                                            value={
                                                                confirmNewPassword
                                                            }
                                                            disabled={
                                                                saving
                                                            }
                                                            onChange={
                                                                event =>
                                                                    setConfirmNewPassword(
                                                                        event.target.value
                                                                    )
                                                            }
                                                            placeholder="Confirm new password"
                                                            className={INPUT_CLASS}
                                                        />


                                                        <div className="md:col-span-2">

                                                            <button
                                                                type="button"
                                                                disabled={
                                                                    saving
                                                                    ||
                                                                    newPassword.length < 8
                                                                    ||
                                                                    confirmNewPassword.length < 8
                                                                }
                                                                onClick={
                                                                    () => {

                                                                        void handlePasswordReset();
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

                                                                    disabled:opacity-50
                                                                "
                                                            >
                                                                Confirm Reset
                                                            </button>

                                                        </div>

                                                    </div>

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


function SummaryCard({
    label,
    value
}: {
    label: string;
    value: number;
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
                    text-3xl
                    font-bold
                    text-[#241715]
                "
            >
                {value}
            </p>

        </div>
    );
}
