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
    createAdminBranch,
    getAdminBranches,
    updateAdminBranch,
    updateAdminBranchActive
} from "@/services/adminBranchesApi";

import {
    BranchOperationalSettings
} from "@/components/admin/BranchOperationalSettings";

import type {
    AdminBranch,
    AdminBranchCreateRequest,
    AdminBranchUpdateRequest
} from "@/types/adminBranches";


interface BranchFormState {

    code: string;

    name: string;

    address: string;

    city: string;

    state: string;

    pincode: string;

    phone: string;

    latitude: string;

    longitude: string;

    openingTime: string;

    closingTime: string;

    active: boolean;
}


const EMPTY_FORM: BranchFormState = {

    code: "",

    name: "",

    address: "",

    city: "",

    state: "",

    pincode: "",

    phone: "",

    latitude: "",

    longitude: "",

    openingTime: "",

    closingTime: "",

    active: true
};


function toFormState(
    branch: AdminBranch
): BranchFormState {

    return {
        code:
            branch.code,

        name:
            branch.name,

        address:
            branch.address
            ?? "",

        city:
            branch.city
            ?? "",

        state:
            branch.state
            ?? "",

        pincode:
            branch.pincode
            ?? "",

        phone:
            branch.phone
            ?? "",

        latitude:
            branch.latitude === null
                ? ""
                : String(
                    branch.latitude
                ),

        longitude:
            branch.longitude === null
                ? ""
                : String(
                    branch.longitude
                ),

        openingTime:
            branch.openingTime
                ? branch.openingTime.slice(
                    0,
                    5
                )
                : "",

        closingTime:
            branch.closingTime
                ? branch.closingTime.slice(
                    0,
                    5
                )
                : "",

        active:
            branch.active
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


function nullableNumber(
    value: string
) {

    const trimmed =
        value.trim();


    if (!trimmed) {

        return null;
    }


    const parsed =
        Number(
            trimmed
        );


    if (
        Number.isNaN(
            parsed
        )
    ) {

        throw new Error(
            "Latitude and longitude must contain valid numbers."
        );
    }


    return parsed;
}


function normalizeTime(
    value: string
) {

    return value
        ? `${value}:00`
        : null;
}


function formatDateTime(
    value: string
) {

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


export default function AdminBranchesPage() {

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
        useState<AdminBranch[]>(
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
        form,
        setForm
    ] =
        useState<BranchFormState>(
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


    const canManage =
        hasPermission(
            "BRANCH_MANAGE"
        );


    const canCreate =
        profile?.roleName
        === "OWNER_ADMIN";


    const selectedBranch =
        useMemo(
            () =>
                branches.find(
                    branch =>
                        branch.id
                        === selectedBranchId
                )
                ?? null,
            [
                branches,
                selectedBranchId
            ]
        );


    const loadBranches =
        useCallback(
            async (
                adminAuthorization: string,
                signal?: AbortSignal,
                preferredBranchId?: number | null
            ) => {

                const result =
                    await getAdminBranches(
                        adminAuthorization,
                        signal
                    );


                const targetBranch =
                    preferredBranchId !== undefined
                        ? result.find(
                            branch =>
                                branch.id
                                === preferredBranchId
                        )
                        ?? result[0]
                        ?? null
                        : result[0]
                        ?? null;


                setBranches(
                    result
                );


                setSelectedBranchId(
                    targetBranch?.id
                    ?? null
                );


                setForm(
                    targetBranch
                        ? toFormState(
                            targetBranch
                        )
                        : EMPTY_FORM
                );


                setLoading(
                    false
                );
            },
            []
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


            getAdminBranches(
                authorization,
                controller.signal
            )
                .then(
                    result => {

                        const firstBranch =
                            result[0]
                            ?? null;


                        setBranches(
                            result
                        );


                        setSelectedBranchId(
                            firstBranch?.id
                            ?? null
                        );


                        setForm(
                            firstBranch
                                ? toFormState(
                                    firstBranch
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
                                : "Unable to load branches."
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
        K extends keyof BranchFormState
    >(
        field: K,
        value: BranchFormState[K]
    ) {

        setForm(
            current => ({
                ...current,
                [field]: value
            })
        );
    }


    async function refreshBranches(
        preferredBranchId: number | null = selectedBranchId
    ) {

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

            await loadBranches(
                authorization,
                undefined,
                preferredBranchId
            );

        } catch (exception) {

            setLoading(
                false
            );


            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to refresh branches."
            );
        }
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
                createMode
            ) {

                const request:
                    AdminBranchCreateRequest = {

                    code:
                        form.code.trim(),

                    name:
                        form.name.trim(),

                    address:
                        nullableText(
                            form.address
                        ),

                    city:
                        nullableText(
                            form.city
                        ),

                    state:
                        nullableText(
                            form.state
                        ),

                    pincode:
                        nullableText(
                            form.pincode
                        ),

                    phone:
                        nullableText(
                            form.phone
                        ),

                    latitude:
                        nullableNumber(
                            form.latitude
                        ),

                    longitude:
                        nullableNumber(
                            form.longitude
                        ),

                    openingTime:
                        normalizeTime(
                            form.openingTime
                        ),

                    closingTime:
                        normalizeTime(
                            form.closingTime
                        ),

                    active:
                        form.active
                };


                const created =
                    await createAdminBranch(
                        request,
                        authorization
                    );


                setCreateMode(
                    false
                );


                setSuccess(
                    `Branch ${created.name} created successfully.`
                );


                await refreshBranches(
                    created.id
                );


                return;

            } else {

                if (
                    selectedBranchId === null
                ) {

                    return;
                }


                const request:
                    AdminBranchUpdateRequest = {

                    name:
                        form.name.trim(),

                    address:
                        nullableText(
                            form.address
                        ),

                    city:
                        nullableText(
                            form.city
                        ),

                    state:
                        nullableText(
                            form.state
                        ),

                    pincode:
                        nullableText(
                            form.pincode
                        ),

                    phone:
                        nullableText(
                            form.phone
                        ),

                    latitude:
                        nullableNumber(
                            form.latitude
                        ),

                    longitude:
                        nullableNumber(
                            form.longitude
                        ),

                    openingTime:
                        normalizeTime(
                            form.openingTime
                        ),

                    closingTime:
                        normalizeTime(
                            form.closingTime
                        )
                };


                const updated =
                    await updateAdminBranch(
                        selectedBranchId,
                        request,
                        authorization
                    );


                setSuccess(
                    `Branch ${updated.name} updated successfully.`
                );


                await refreshBranches(
                    updated.id
                );


                return;
            }

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save the branch."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    async function handleActiveToggle() {

        if (
            authorization === null
            ||
            selectedBranch === null
            ||
            saving
        ) {

            return;
        }


        const nextActive =
            !selectedBranch.active;


        const confirmed =
            window.confirm(
                nextActive
                    ? `Activate ${selectedBranch.name}?`
                    : `Deactivate ${selectedBranch.name}? It will no longer appear in the public active branch list.`
            );


        if (!confirmed) {

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
                await updateAdminBranchActive(
                    selectedBranch.id,
                    nextActive,
                    authorization
                );


            setSuccess(
                updated.active
                    ? `${updated.name} activated successfully.`
                    : `${updated.name} deactivated successfully.`
            );


            await refreshBranches(
                updated.id
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to change branch status."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    if (
        profile
        &&
        !canManage
    ) {

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
                        rounded-2xl
                        border
                        border-red-200
                        bg-red-50
                        p-6
                        text-red-800
                    "
                >
                    You do not have permission to manage branches.
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
                sm:py-8
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
                            Business control
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
                            Branch Management
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
                            Manage branch identity, contact details, operating hours and public availability.
                        </p>

                    </div>


                    {
                        canCreate
                        && (

                            <button
                                type="button"
                                disabled={
                                    saving
                                }
                                onClick={
                                    () => {

                                        setCreateMode(
                                            true
                                        );


                                        setSelectedBranchId(
                                            null
                                        );


                                        setForm(
                                            EMPTY_FORM
                                        );


                                        setError(
                                            null
                                        );


                                        setSuccess(
                                            null
                                        );
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
                                Add Branch
                            </button>

                        )
                    }

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
                        lg:grid-cols-[320px_minmax(0,1fr)]
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

                            <div
                                className="
                                    flex
                                    items-center
                                    justify-between
                                    gap-3
                                "
                            >

                                <div>

                                    <h2
                                        className="
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        Branches
                                    </h2>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        {branches.length} accessible
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
                                        () => {

                                            void refreshBranches();
                                        }
                                    }
                                    className="
                                        rounded-lg
                                        border
                                        border-[#eadfd6]
                                        bg-white
                                        px-3
                                        py-2
                                        text-xs
                                        font-semibold
                                        text-[#7a1625]

                                        disabled:opacity-50
                                    "
                                >
                                    {
                                        loading
                                            ? "Loading..."
                                            : "Refresh"
                                    }
                                </button>

                            </div>

                        </div>


                        {
                            loading
                            &&
                            branches.length === 0
                                ? (

                                    <div
                                        className="
                                            p-6
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        Loading branches...
                                    </div>

                                )
                                : branches.length === 0
                                    ? (

                                        <div
                                            className="
                                                p-6
                                                text-sm
                                                text-[#756763]
                                            "
                                        >
                                            No branches available.
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
                                                branches.map(
                                                    branch => (

                                                        <button
                                                            key={
                                                                branch.id
                                                            }
                                                            type="button"
                                                            disabled={
                                                                saving
                                                            }
                                                            onClick={
                                                                () => {

                                                                    setCreateMode(
                                                                        false
                                                                    );


                                                                    setSelectedBranchId(
                                                                        branch.id
                                                                    );


                                                                    setForm(
                                                                        toFormState(
                                                                            branch
                                                                        )
                                                                    );


                                                                    setError(
                                                                        null
                                                                    );


                                                                    setSuccess(
                                                                        null
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
                                                                    !createMode
                                                                    &&
                                                                    selectedBranchId === branch.id
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

                                                                <div
                                                                    className="
                                                                        min-w-0
                                                                    "
                                                                >

                                                                    <p
                                                                        className="
                                                                            truncate
                                                                            font-bold
                                                                            text-[#241715]
                                                                        "
                                                                    >
                                                                        {branch.name}
                                                                    </p>


                                                                    <p
                                                                        className="
                                                                            mt-1
                                                                            text-xs
                                                                            font-semibold
                                                                            text-[#756763]
                                                                        "
                                                                    >
                                                                        {branch.code}
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
                                                                            branch.active
                                                                                ? "bg-green-50 text-green-800"
                                                                                : "bg-gray-100 text-gray-700"
                                                                        }
                                                                    `}
                                                                >
                                                                    {
                                                                        branch.active
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

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-3
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
                                        {
                                            createMode
                                                ? "New branch"
                                                : "Branch details"
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
                                                ? "Create Branch"
                                                : selectedBranch?.name
                                                ?? "Select a branch"
                                        }
                                    </h2>

                                </div>


                                {
                                    !createMode
                                    &&
                                    selectedBranch
                                    && (

                                        <button
                                            type="button"
                                            disabled={
                                                saving
                                            }
                                            onClick={
                                                () => {

                                                    void handleActiveToggle();
                                                }
                                            }
                                            className={`
                                                min-h-10
                                                rounded-xl
                                                border
                                                px-4
                                                text-sm
                                                font-semibold

                                                ${
                                                    selectedBranch.active
                                                        ? "border-red-200 bg-red-50 text-red-700"
                                                        : "border-green-200 bg-green-50 text-green-800"
                                                }

                                                disabled:opacity-50
                                            `}
                                        >
                                            {
                                                selectedBranch.active
                                                    ? "Deactivate Branch"
                                                    : "Activate Branch"
                                            }
                                        </button>

                                    )
                                }

                            </div>

                        </div>


                        {
                            !createMode
                            &&
                            selectedBranch === null
                                ? (

                                    <div
                                        className="
                                            p-8
                                            text-sm
                                            text-[#756763]
                                        "
                                    >
                                        Select a branch to manage it.
                                    </div>

                                )
                                : (

                                    <div
                                        className="
                                            p-5
                                            sm:p-6
                                        "
                                    >

                                        <div
                                            className="
                                                grid
                                                gap-5
                                                md:grid-cols-2
                                            "
                                        >

                                            <Field
                                                label="Branch Code"
                                            >

                                                <input
                                                    value={
                                                        form.code
                                                    }
                                                    disabled={
                                                        !createMode
                                                        ||
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "code",
                                                                event.target.value
                                                            )
                                                    }
                                                    placeholder="BRANCH-01"
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                        outline-none

                                                        focus:border-[#c88a20]
                                                        focus:ring-4
                                                        focus:ring-[#f6dfad]/40

                                                        disabled:bg-[#f8f4f1]
                                                    "
                                                />


                                                {
                                                    !createMode
                                                    && (

                                                        <p
                                                            className="
                                                                mt-1
                                                                text-xs
                                                                text-[#756763]
                                                            "
                                                        >
                                                            Branch code is permanent.
                                                        </p>

                                                    )
                                                }

                                            </Field>


                                            <Field
                                                label="Branch Name"
                                            >

                                                <input
                                                    value={
                                                        form.name
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "name",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                        outline-none

                                                        focus:border-[#c88a20]
                                                        focus:ring-4
                                                        focus:ring-[#f6dfad]/40
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Phone"
                                            >

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
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Pincode"
                                            >

                                                <input
                                                    value={
                                                        form.pincode
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "pincode",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Address"
                                                wide
                                            >

                                                <textarea
                                                    rows={
                                                        3
                                                    }
                                                    value={
                                                        form.address
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "address",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        py-3
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="City"
                                            >

                                                <input
                                                    value={
                                                        form.city
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "city",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="State"
                                            >

                                                <input
                                                    value={
                                                        form.state
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "state",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Opening Time"
                                            >

                                                <input
                                                    type="time"
                                                    value={
                                                        form.openingTime
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "openingTime",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Closing Time"
                                            >

                                                <input
                                                    type="time"
                                                    value={
                                                        form.closingTime
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "closingTime",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Latitude"
                                            >

                                                <input
                                                    inputMode="decimal"
                                                    value={
                                                        form.latitude
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "latitude",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            <Field
                                                label="Longitude"
                                            >

                                                <input
                                                    inputMode="decimal"
                                                    value={
                                                        form.longitude
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "longitude",
                                                                event.target.value
                                                            )
                                                    }
                                                    className="
                                                        min-h-11
                                                        w-full
                                                        rounded-xl
                                                        border
                                                        border-[#eadfd6]
                                                        px-4
                                                        text-sm
                                                        text-[#241715]
                                                    "
                                                />

                                            </Field>


                                            {
                                                createMode
                                                && (

                                                    <div
                                                        className="
                                                            md:col-span-2
                                                        "
                                                    >

                                                        <label
                                                            className="
                                                                flex
                                                                items-center
                                                                gap-3
                                                                rounded-xl
                                                                border
                                                                border-[#eadfd6]
                                                                bg-[#fffaf3]
                                                                p-4
                                                            "
                                                        >

                                                            <input
                                                                type="checkbox"
                                                                checked={
                                                                    form.active
                                                                }
                                                                disabled={
                                                                    saving
                                                                }
                                                                onChange={
                                                                    event =>
                                                                        updateField(
                                                                            "active",
                                                                            event.target.checked
                                                                        )
                                                                }
                                                            />


                                                            <span
                                                                className="
                                                                    text-sm
                                                                    font-semibold
                                                                    text-[#241715]
                                                                "
                                                            >
                                                                Make this branch active immediately
                                                            </span>

                                                        </label>

                                                    </div>

                                                )
                                            }

                                        </div>


                                        {
                                            !createMode
                                            &&
                                            selectedBranch
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
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Created:{" "}
                                                        <span
                                                            className="
                                                                font-semibold
                                                                text-[#241715]
                                                            "
                                                        >
                                                            {
                                                                formatDateTime(
                                                                    selectedBranch.createdAt
                                                                )
                                                            }
                                                        </span>
                                                    </p>


                                                    <p
                                                        className="
                                                            mt-1
                                                            text-xs
                                                            text-[#756763]
                                                        "
                                                    >
                                                        Last updated:{" "}
                                                        <span
                                                            className="
                                                                font-semibold
                                                                text-[#241715]
                                                            "
                                                        >
                                                            {
                                                                formatDateTime(
                                                                    selectedBranch.updatedAt
                                                                )
                                                            }
                                                        </span>
                                                    </p>

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
                                                    ||
                                                    !form.name.trim()
                                                    ||
                                                    (
                                                        createMode
                                                        &&
                                                        !form.code.trim()
                                                    )
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

                                                    disabled:cursor-not-allowed
                                                    disabled:opacity-50
                                                "
                                            >
                                                {
                                                    saving
                                                        ? "Saving..."
                                                        : createMode
                                                            ? "Create Branch"
                                                            : "Save Changes"
                                                }
                                            </button>


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

                                                                const firstBranch =
                                                                    branches[0]
                                                                    ?? null;


                                                                setCreateMode(
                                                                    false
                                                                );


                                                                setSelectedBranchId(
                                                                    firstBranch?.id
                                                                    ?? null
                                                                );


                                                                setForm(
                                                                    firstBranch
                                                                        ? toFormState(
                                                                            firstBranch
                                                                        )
                                                                        : EMPTY_FORM
                                                                );


                                                                setError(
                                                                    null
                                                                );


                                                                setSuccess(
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
                                                        Cancel
                                                    </button>

                                                )
                                            }

                                        </div>

                                    </div>

                                )
                        }


                        {
                            !createMode
                            &&
                            selectedBranch
                            &&
                            authorization
                            && (

                                <BranchOperationalSettings
                                    key={
                                        selectedBranch.id
                                    }
                                    branchId={
                                        selectedBranch.id
                                    }
                                    branchName={
                                        selectedBranch.name
                                    }
                                    authorization={
                                        authorization
                                    }
                                />

                            )
                        }

                    </section>

                </div>

            </div>

        </div>
    );
}


function Field({
    label,
    wide = false,
    children
}: {
    label: string;
    wide?: boolean;
    children: React.ReactNode;
}) {

    return (
        <div
            className={
                wide
                    ? "md:col-span-2"
                    : undefined
            }
        >

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
