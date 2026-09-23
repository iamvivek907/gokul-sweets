"use client";

import {
    useEffect,
    useState
} from "react";

import {
    AdminBranchPickupSettingsApiError,
    getAdminBranchPickupSettings,
    updateAdminBranchPickupSettings
} from "@/services/adminBranchPickupSettingsApi";

import type {
    UpdateAdminBranchPickupSettingsRequest
} from "@/types/adminBranchPickupSettings";


interface PickupSettingsFormState {

    slotDurationMinutes: string;

    defaultCapacity: string;

    advanceBookingDays: string;

    openingTime: string;

    closingTime: string;

    enabled: boolean;

    priorityEnabled: boolean;

    defaultPriorityCapacity: string;

    defaultPriorityCharge: string;
}


const DEFAULT_FORM: PickupSettingsFormState = {

    slotDurationMinutes: "30",

    defaultCapacity: "10",

    advanceBookingDays: "7",

    openingTime: "09:00",

    closingTime: "21:00",

    enabled: true,

    priorityEnabled: false,

    defaultPriorityCapacity: "0",

    defaultPriorityCharge: "0.00"
};


function normalizeTimeForInput(
    value: string
) {

    return value
        ? value.slice(
            0,
            5
        )
        : "";
}


function normalizeTimeForApi(
    value: string
) {

    return `${value}:00`;
}


function positiveInteger(
    value: string,
    fieldName: string
) {

    const parsed =
        Number(
            value
        );


    if (
        !Number.isInteger(
            parsed
        )
        ||
        parsed <= 0
    ) {

        throw new Error(
            `${fieldName} must be a whole number greater than zero.`
        );
    }


    return parsed;
}


function nonNegativeInteger(
    value: string,
    fieldName: string
) {

    const parsed =
        Number(
            value
        );


    if (
        !Number.isInteger(
            parsed
        )
        ||
        parsed < 0
    ) {

        throw new Error(
            `${fieldName} must be a whole number of zero or more.`
        );
    }


    return parsed;
}


function nonNegativeMoney(
    value: string
) {

    const parsed =
        Number(
            value
        );


    if (
        Number.isNaN(
            parsed
        )
        ||
        parsed < 0
    ) {

        throw new Error(
            "Priority charge must be zero or greater."
        );
    }


    return Number(
        parsed.toFixed(
            2
        )
    );
}


export function BranchOperationalSettings({
    branchId,
    branchName,
    authorization
}: {
    branchId: number;
    branchName: string;
    authorization: string;
}) {

    const [
        form,
        setForm
    ] =
        useState<PickupSettingsFormState>(
            DEFAULT_FORM
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
        initialized,
        setInitialized
    ] =
        useState(
            true
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

            const controller =
                new AbortController();


            getAdminBranchPickupSettings(
                branchId,
                authorization,
                controller.signal
            )
                .then(
                    settings => {

                        setForm({
                            slotDurationMinutes:
                                String(
                                    settings.slotDurationMinutes
                                ),

                            defaultCapacity:
                                String(
                                    settings.defaultCapacity
                                ),

                            advanceBookingDays:
                                String(
                                    settings.advanceBookingDays
                                ),

                            openingTime:
                                normalizeTimeForInput(
                                    settings.openingTime
                                ),

                            closingTime:
                                normalizeTimeForInput(
                                    settings.closingTime
                                ),

                            enabled:
                                settings.enabled,

                            priorityEnabled:
                                settings.priorityEnabled,

                            defaultPriorityCapacity:
                                String(
                                    settings.defaultPriorityCapacity
                                ),

                            defaultPriorityCharge:
                                Number(
                                    settings.defaultPriorityCharge
                                ).toFixed(
                                    2
                                )
                        });


                        setInitialized(
                            true
                        );


                        setError(
                            null
                        );


                        setSuccess(
                            null
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


                        if (
                            exception instanceof AdminBranchPickupSettingsApiError
                            &&
                            (
                                exception.status === 400
                                ||
                                exception.status === 404
                            )
                        ) {

                            /*
                             * A newly created branch may not have its
                             * pickup-settings row yet. The PUT endpoint
                             * creates it, so present safe defaults.
                             */
                            setForm(
                                DEFAULT_FORM
                            );


                            setInitialized(
                                false
                            );


                            setError(
                                null
                            );


                            setSuccess(
                                null
                            );


                            setLoading(
                                false
                            );


                            return;
                        }


                        setError(
                            exception instanceof Error
                                ? exception.message
                                : "Unable to load pickup settings."
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
            branchId,
            authorization
        ]
    );


    function updateField<
        K extends keyof PickupSettingsFormState
    >(
        field: K,
        value: PickupSettingsFormState[K]
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


    async function handleSave() {

        if (
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
                !form.openingTime
                ||
                !form.closingTime
            ) {

                throw new Error(
                    "Pickup opening and closing times are required."
                );
            }


            const priorityEnabled =
                form.priorityEnabled;


            const request:
                UpdateAdminBranchPickupSettingsRequest = {

                slotDurationMinutes:
                    positiveInteger(
                        form.slotDurationMinutes,
                        "Slot duration"
                    ),

                defaultCapacity:
                    positiveInteger(
                        form.defaultCapacity,
                        "Normal capacity"
                    ),

                advanceBookingDays:
                    nonNegativeInteger(
                        form.advanceBookingDays,
                        "Advance booking days"
                    ),

                openingTime:
                    normalizeTimeForApi(
                        form.openingTime
                    ),

                closingTime:
                    normalizeTimeForApi(
                        form.closingTime
                    ),

                enabled:
                    form.enabled,

                priorityEnabled,

                defaultPriorityCapacity:
                    priorityEnabled
                        ? positiveInteger(
                            form.defaultPriorityCapacity,
                            "Priority capacity"
                        )
                        : 0,

                defaultPriorityCharge:
                    priorityEnabled
                        ? nonNegativeMoney(
                            form.defaultPriorityCharge
                        )
                        : 0
            };


            const updated =
                await updateAdminBranchPickupSettings(
                    branchId,
                    request,
                    authorization
                );


            setForm({
                slotDurationMinutes:
                    String(
                        updated.slotDurationMinutes
                    ),

                defaultCapacity:
                    String(
                        updated.defaultCapacity
                    ),

                advanceBookingDays:
                    String(
                        updated.advanceBookingDays
                    ),

                openingTime:
                    normalizeTimeForInput(
                        updated.openingTime
                    ),

                closingTime:
                    normalizeTimeForInput(
                        updated.closingTime
                    ),

                enabled:
                    updated.enabled,

                priorityEnabled:
                    updated.priorityEnabled,

                defaultPriorityCapacity:
                    String(
                        updated.defaultPriorityCapacity
                    ),

                defaultPriorityCharge:
                    Number(
                        updated.defaultPriorityCharge
                    ).toFixed(
                        2
                    )
            });


            setInitialized(
                true
            );


            setSuccess(
                "Operational settings saved successfully."
            );

        } catch (exception) {

            setError(
                exception instanceof Error
                    ? exception.message
                    : "Unable to save pickup settings."
            );

        } finally {

            setSaving(
                false
            );
        }
    }


    return (
        <div
            className="
                border-t
                border-[#eadfd6]
                bg-[#fffdf9]
                p-5
                sm:p-6
            "
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
                            text-xs
                            font-semibold
                            uppercase
                            tracking-[0.14em]
                            text-[#c88a20]
                        "
                    >
                        11A2
                    </p>


                    <h3
                        className="
                            mt-1
                            text-xl
                            font-bold
                            text-[#241715]
                        "
                    >
                        Pickup & Operational Settings
                    </h3>


                    <p
                        className="
                            mt-2
                            max-w-3xl
                            text-sm
                            leading-6
                            text-[#756763]
                        "
                    >
                        Configure the defaults used when pickup slots are generated for {branchName}.
                    </p>

                </div>


                <span
                    className={`
                        inline-flex
                        w-fit
                        rounded-full
                        px-3
                        py-1.5
                        text-xs
                        font-semibold

                        ${
                            form.enabled
                                ? "bg-green-50 text-green-800"
                                : "bg-gray-100 text-gray-700"
                        }
                    `}
                >
                    {
                        form.enabled
                            ? "Pickup Enabled"
                            : "Pickup Disabled"
                    }
                </span>

            </div>


            {
                loading
                    ? (

                        <div
                            className="
                                mt-6
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-white
                                p-5
                                text-sm
                                text-[#756763]
                            "
                        >
                            Loading operational settings...
                        </div>

                    )
                    : (

                        <>

                            {
                                !initialized
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
                                            text-amber-900
                                        "
                                    >
                                        This branch does not have pickup settings yet. Saving the defaults below will create them.
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
                                    gap-5
                                    md:grid-cols-2
                                "
                            >

                                <SettingToggle
                                    label="Customer pickup"
                                    description="Allow customers to select pickup slots for this branch."
                                    checked={
                                        form.enabled
                                    }
                                    disabled={
                                        saving
                                    }
                                    onChange={
                                        value =>
                                            updateField(
                                                "enabled",
                                                value
                                            )
                                    }
                                />


                                <SettingToggle
                                    label="Priority pickup"
                                    description="Enable a separate paid priority capacity on newly generated slots."
                                    checked={
                                        form.priorityEnabled
                                    }
                                    disabled={
                                        saving
                                    }
                                    onChange={
                                        value =>
                                            updateField(
                                                "priorityEnabled",
                                                value
                                            )
                                    }
                                />


                                <SettingField
                                    label="Pickup Opening Time"
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
                                        className={INPUT_CLASS}
                                    />

                                </SettingField>


                                <SettingField
                                    label="Pickup Closing Time"
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
                                        className={INPUT_CLASS}
                                    />

                                </SettingField>


                                <SettingField
                                    label="Slot Duration (minutes)"
                                >

                                    <input
                                        type="number"
                                        min="1"
                                        step="1"
                                        value={
                                            form.slotDurationMinutes
                                        }
                                        disabled={
                                            saving
                                        }
                                        onChange={
                                            event =>
                                                updateField(
                                                    "slotDurationMinutes",
                                                    event.target.value
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    />

                                </SettingField>


                                <SettingField
                                    label="Normal Capacity / Slot"
                                >

                                    <input
                                        type="number"
                                        min="1"
                                        step="1"
                                        value={
                                            form.defaultCapacity
                                        }
                                        disabled={
                                            saving
                                        }
                                        onChange={
                                            event =>
                                                updateField(
                                                    "defaultCapacity",
                                                    event.target.value
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    />

                                </SettingField>


                                <SettingField
                                    label="Advance Booking Days"
                                    help="0 means customers can book only for today."
                                >

                                    <input
                                        type="number"
                                        min="0"
                                        step="1"
                                        value={
                                            form.advanceBookingDays
                                        }
                                        disabled={
                                            saving
                                        }
                                        onChange={
                                            event =>
                                                updateField(
                                                    "advanceBookingDays",
                                                    event.target.value
                                                )
                                        }
                                        className={INPUT_CLASS}
                                    />

                                </SettingField>


                                <div
                                    className="
                                        hidden
                                        md:block
                                    "
                                />


                                {
                                    form.priorityEnabled
                                    && (

                                        <>

                                            <SettingField
                                                label="Priority Capacity / Slot"
                                            >

                                                <input
                                                    type="number"
                                                    min="1"
                                                    step="1"
                                                    value={
                                                        form.defaultPriorityCapacity
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "defaultPriorityCapacity",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </SettingField>


                                            <SettingField
                                                label="Priority Charge (₹)"
                                            >

                                                <input
                                                    type="number"
                                                    min="0"
                                                    step="0.01"
                                                    value={
                                                        form.defaultPriorityCharge
                                                    }
                                                    disabled={
                                                        saving
                                                    }
                                                    onChange={
                                                        event =>
                                                            updateField(
                                                                "defaultPriorityCharge",
                                                                event.target.value
                                                            )
                                                    }
                                                    className={INPUT_CLASS}
                                                />

                                            </SettingField>

                                        </>

                                    )
                                }

                            </div>


                            <div
                                className="
                                    mt-6
                                    rounded-xl
                                    border
                                    border-[#eadfd6]
                                    bg-white
                                    p-4
                                    text-sm
                                    leading-6
                                    text-[#756763]
                                "
                            >
                                These values are defaults for <strong className="text-[#241715]">newly generated pickup slots</strong>. Existing slots keep their current capacity, priority settings and charge so existing bookings are not silently changed.
                            </div>


                            <div
                                className="
                                    mt-6
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

                                        disabled:cursor-not-allowed
                                        disabled:opacity-50
                                    "
                                >
                                    {
                                        saving
                                            ? "Saving Settings..."
                                            : initialized
                                                ? "Save Operational Settings"
                                                : "Create Operational Settings"
                                    }
                                </button>

                            </div>

                        </>

                    )
            }

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


export function SettingField({
    label,
    help,
    htmlFor,
    children
}: {
    label: string;
    help?: string;
    htmlFor?: string;
    children: React.ReactNode;
}) {

    return (
        <div>

            <label
                htmlFor={htmlFor}
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


            {
                help
                ? (

                    <p
                        className="
                            mt-1
                            text-xs
                            text-[#756763]
                        "
                    >
                        {help}
                    </p>

                )
                : null
            }

        </div>
    );
}


export function SettingToggle({
    label,
    description,
    checked,
    disabled,
    onChange
}: {
    label: string;
    description: string;
    checked: boolean;
    disabled: boolean;
    onChange: (value: boolean) => void;
}) {

    return (
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
                    checked
                }
                disabled={
                    disabled
                }
                onChange={
                    event =>
                        onChange(
                            event.target.checked
                        )
                }
                className="
                    mt-1
                    h-4
                    w-4
                "
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
                    {label}
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
                    {description}
                </span>

            </span>

        </label>
    );
}
