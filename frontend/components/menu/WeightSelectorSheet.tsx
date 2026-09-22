"use client";

import {
    useEffect,
    useId,
    useRef,
    useState
} from "react";

import type {
    MenuProduct
} from "@/types/menu";


type WeightInputUnit =
    | "g"
    | "kg";


interface WeightSelectorSheetProps {
    product: MenuProduct | null;
    currentWeightGrams?: number | null;
    onClose: () => void;
    onConfirm: (product: MenuProduct, weightGrams: number) => void;
}


interface WeightSelectorDialogProps {
    product: MenuProduct;
    initialWeightGrams: number;
    onClose: () => void;
    onConfirm: (product: MenuProduct, weightGrams: number) => void;
}


function formatCurrency(
    amount: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style: "currency",
            currency: "INR",
            minimumFractionDigits: 0,
            maximumFractionDigits: 2
        }
    ).format(
        amount
    );
}


function formatWeight(
    weightGrams: number
): string {

    if (
        weightGrams >= 1000
    ) {

        const kilograms =
            weightGrams
            /
            1000;


        return `${new Intl.NumberFormat(
            "en-IN",
            {
                maximumFractionDigits: 3
            }
        ).format(kilograms)} kg`;
    }


    return `${weightGrams} g`;
}


function inputValueForWeight(
    weightGrams: number,
    unit: WeightInputUnit
): string {

    if (
        unit === "g"
    ) {

        return weightGrams.toString();
    }


    return (
        weightGrams
        /
        1000
    ).toString();
}


function parseWeightInput(
    value: string,
    unit: WeightInputUnit
): number | null {

    if (
        value.trim() === ""
    ) {

        return null;
    }


    const parsed =
        Number(
            value
        );


    if (
        !Number.isFinite(parsed)
        ||
        parsed <= 0
    ) {

        return null;
    }


    const grams =
        unit === "kg"
            ? parsed * 1000
            : parsed;


    const roundedGrams =
        Math.round(
            grams
        );


    if (
        Math.abs(
            grams
            -
            roundedGrams
        ) > 0.000001
    ) {

        return null;
    }


    return roundedGrams;
}


function WeightSelectorDialog({
    product,
    initialWeightGrams,
    onClose,
    onConfirm
}: WeightSelectorDialogProps) {
    const dialogRef = useRef<HTMLElement>(null);
    useEffect(() => {
        const previouslyFocused = document.activeElement;
        const dialog = dialogRef.current;
        const focusable = () => Array.from(dialog?.querySelectorAll<HTMLElement>(
            'button:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex="0"]'
        ) ?? []);
        focusable()[0]?.focus();
        function trapFocus(event: KeyboardEvent) {
            if (event.key !== "Tab") return;
            const elements = focusable();
            const first = elements[0];
            const last = elements[elements.length - 1];
            if (event.shiftKey && document.activeElement === first) {event.preventDefault(); last?.focus();}
            if (!event.shiftKey && document.activeElement === last) {event.preventDefault(); first?.focus();}
        }
        dialog?.addEventListener("keydown", trapFocus);
        return () => {
            dialog?.removeEventListener("keydown", trapFocus);
            if (previouslyFocused instanceof HTMLElement) previouslyFocused.focus();
        };
    }, []);

    const minimum =
        product.minimumWeightGrams
        ?? 250;


    const step =
        product.weightStepGrams
        ?? 50;


    const normalizedInitialWeight =
        initialWeightGrams >= minimum
        &&
        (
            initialWeightGrams
            -
            minimum
        )
        %
        step === 0
            ? initialWeightGrams
            : minimum;


    const inputId =
        useId();


    const [
        weightGrams,
        setWeightGrams
    ] =
        useState(
            normalizedInitialWeight
        );


    const [
        inputUnit,
        setInputUnit
    ] =
        useState<WeightInputUnit>(
            normalizedInitialWeight >= 1000
                ? "kg"
                : "g"
        );


    const [
        inputValue,
        setInputValue
    ] =
        useState(
            inputValueForWeight(
                normalizedInitialWeight,
                normalizedInitialWeight >= 1000
                    ? "kg"
                    : "g"
            )
        );


    useEffect(
        () => {

            function handleKeyDown(
                event: KeyboardEvent
            ): void {

                if (
                    event.key === "Escape"
                ) {

                    onClose();
                }
            }


            window.addEventListener(
                "keydown",
                handleKeyDown
            );


            return () =>
                window.removeEventListener(
                    "keydown",
                    handleKeyDown
                );
        },
        [
            onClose
        ]
    );


    const presets =
        Array.from(
            new Set(
                [
                    minimum,
                    250,
                    500,
                    750,
                    1000,
                    2000,
                    5000,
                    10000
                ]
            )
        )
            .filter(
                value =>
                    value >= minimum
            )
            .filter(
                value =>
                    (
                        value
                        -
                        minimum
                    )
                    %
                    step === 0
            )
            .sort(
                (
                    first,
                    second
                ) =>
                    first
                    -
                    second
            );


    const parsedInputWeight =
        parseWeightInput(
            inputValue,
            inputUnit
        );


    let inputError:
        string | null =
        null;


    if (
        parsedInputWeight === null
    ) {

        inputError =
            "Enter a valid quantity.";

    } else if (
        parsedInputWeight < minimum
    ) {

        inputError =
            `Minimum quantity is ${formatWeight(minimum)}.`;

    } else if (
        (
            parsedInputWeight
            -
            minimum
        )
        %
        step !== 0
    ) {

        inputError =
            `Choose an amount in ${formatWeight(step)} steps.`;
    }


    const validWeight =
        inputError === null
            ? parsedInputWeight
            : null;


    const estimatedPrice =
        validWeight === null
            ? null
            : product.price
                *
                validWeight
                /
                1000;


    function selectWeight(
        nextWeightGrams: number
    ): void {

        setWeightGrams(
            nextWeightGrams
        );


        setInputValue(
            inputValueForWeight(
                nextWeightGrams,
                inputUnit
            )
        );
    }


    function changeUnit(
        nextUnit: WeightInputUnit
    ): void {

        if (
            nextUnit === inputUnit
        ) {

            return;
        }


        setInputUnit(
            nextUnit
        );


        setInputValue(
            inputValueForWeight(
                weightGrams,
                nextUnit
            )
        );
    }


    function changeInput(
        nextValue: string
    ): void {

        const allowed =
            inputUnit === "kg"
                ? /^\d*(?:\.\d{0,3})?$/
                : /^\d*$/;


        if (
            !allowed.test(
                nextValue
            )
        ) {

            return;
        }


        setInputValue(
            nextValue
        );


        const parsed =
            parseWeightInput(
                nextValue,
                inputUnit
            );


        if (
            parsed !== null
            &&
            parsed >= minimum
            &&
            (
                parsed
                -
                minimum
            )
            %
            step === 0
        ) {

            setWeightGrams(
                parsed
            );
        }
    }


    return (
        <div
            role="presentation"
            className="fixed inset-0 z-70 flex items-end justify-center bg-black/45 p-0 sm:items-center sm:p-5"
            onMouseDown={
                event => {

                    if (
                        event.target ===
                        event.currentTarget
                    ) {

                        onClose();
                    }
                }
            }
        >

            <section
                ref={dialogRef}
                role="dialog"
                aria-modal="true"
                aria-labelledby="weight-selector-title"
                className="max-h-[92dvh] w-full max-w-md overflow-y-auto rounded-t-3xl bg-white p-5 shadow-2xl sm:rounded-3xl sm:p-6"
            >

                <div
                    className="mx-auto mb-4 h-1.5 w-12 rounded-full bg-[#dfd2ca] sm:hidden"
                />


                <div
                    className="flex items-start justify-between gap-4"
                >

                    <div>

                        <p
                            className="text-xs font-semibold uppercase tracking-[0.12em] text-[#c88a20]"
                        >
                            Choose quantity
                        </p>


                        <h2
                            id="weight-selector-title"
                            className="mt-1 text-xl font-bold text-[#241715]"
                        >
                            {
                                product.name
                            }
                        </h2>


                        <p
                            className="mt-1 text-sm text-[#756763]"
                        >
                            {
                                formatCurrency(
                                    product.price
                                )
                            }
                            {" per kg"}
                        </p>

                    </div>


                    <button
                        type="button"
                        onClick={
                            onClose
                        }
                        aria-label="Close weight selection"
                        className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full bg-[#fff4e5] text-xl text-[#7a1625] transition active:scale-95"
                    >
                        ×
                    </button>

                </div>


                <div
                    className="mt-5"
                >

                    <p
                        className="text-xs font-bold text-[#241715]"
                    >
                        Quick select
                    </p>


                    <div
                        className="mt-2 grid grid-cols-3 gap-2"
                    >

                        {
                            presets.map(
                                preset => (

                                    <button
                                        key={
                                            preset
                                        }
                                        type="button"
                                        onClick={
                                            () =>
                                                selectWeight(
                                                    preset
                                                )
                                        }
                                        className={`min-h-11 rounded-xl border px-2 text-sm font-bold transition active:scale-[0.97] ${
                                            validWeight === preset
                                                ? "border-[#7a1625] bg-[#7a1625] text-white shadow-sm"
                                                : "border-[#eadfd6] bg-[#fffaf3] text-[#7a1625] hover:border-[#c88a20]"
                                        }`}
                                    >
                                        {
                                            formatWeight(
                                                preset
                                            )
                                        }
                                    </button>

                                )
                            )
                        }

                    </div>

                </div>


                <div
                    className="mt-5 rounded-2xl border border-[#eadfd6] bg-[#fffaf3] p-4"
                >

                    <div
                        className="flex items-center justify-between gap-3"
                    >

                        <div>

                            <label
                                htmlFor={
                                    inputId
                                }
                                className="text-xs font-bold text-[#241715]"
                            >
                                Enter exact quantity
                            </label>


                            <p
                                className="mt-0.5 text-[11px] text-[#756763]"
                            >
                                Minimum {formatWeight(minimum)} · {formatWeight(step)} steps
                            </p>

                        </div>


                        <div
                            className="flex rounded-lg bg-white p-1 shadow-sm"
                            aria-label="Quantity unit"
                        >

                            {
                                ([
                                    "g",
                                    "kg"
                                ] as const)
                                    .map(
                                        unit => (

                                            <button
                                                key={
                                                    unit
                                                }
                                                type="button"
                                                onClick={
                                                    () =>
                                                        changeUnit(
                                                            unit
                                                        )
                                                }
                                                className={`min-h-8 min-w-10 rounded-md px-2 text-xs font-bold uppercase transition ${
                                                    inputUnit === unit
                                                        ? "bg-[#7a1625] text-white"
                                                        : "text-[#756763]"
                                                }`}
                                                aria-pressed={
                                                    inputUnit === unit
                                                }
                                            >
                                                {
                                                    unit
                                                }
                                            </button>

                                        )
                                    )
                            }

                        </div>

                    </div>


                    <div
                        className="mt-3 grid grid-cols-[48px_minmax(0,1fr)_48px] items-center gap-2"
                    >

                        <button
                            type="button"
                            disabled={
                                weightGrams <= minimum
                            }
                            onClick={
                                () =>
                                    selectWeight(
                                        Math.max(
                                            minimum,
                                            weightGrams
                                            -
                                            step
                                        )
                                    )
                            }
                            aria-label={`Decrease weight by ${step} grams`}
                            className="flex h-12 w-12 items-center justify-center rounded-xl border border-[#eadfd6] bg-white text-2xl font-bold text-[#7a1625] shadow-sm transition active:scale-95 disabled:cursor-not-allowed disabled:opacity-35"
                        >
                            −
                        </button>


                        <div
                            className="relative"
                        >

                            <input
                                id={
                                    inputId
                                }
                                type="text"
                                inputMode={
                                    inputUnit === "kg"
                                        ? "decimal"
                                        : "numeric"
                                }
                                value={
                                    inputValue
                                }
                                onChange={
                                    event =>
                                        changeInput(
                                            event.target.value
                                        )
                                }
                                aria-invalid={
                                    inputError !== null
                                }
                                aria-describedby={`${inputId}-help`}
                                className={`h-12 w-full rounded-xl border bg-white px-3 pr-11 text-center text-xl font-bold text-[#241715] outline-none transition focus:ring-2 focus:ring-[#7a1625]/10 ${
                                    inputError
                                        ? "border-red-400 focus:border-red-500"
                                        : "border-[#eadfd6] focus:border-[#7a1625]"
                                }`}
                            />


                            <span
                                className="pointer-events-none absolute inset-y-0 right-3 flex items-center text-xs font-bold text-[#756763]"
                            >
                                {
                                    inputUnit
                                }
                            </span>

                        </div>


                        <button
                            type="button"
                            onClick={
                                () =>
                                    selectWeight(
                                        Math.max(
                                            minimum,
                                            weightGrams
                                        )
                                        +
                                        step
                                    )
                            }
                            aria-label={`Increase weight by ${step} grams`}
                            className="flex h-12 w-12 items-center justify-center rounded-xl border border-[#eadfd6] bg-white text-2xl font-bold text-[#7a1625] shadow-sm transition active:scale-95"
                        >
                            +
                        </button>

                    </div>


                    <p
                        id={`${inputId}-help`}
                        className={`mt-2 min-h-5 text-center text-xs font-medium ${
                            inputError
                                ? "text-red-600"
                                : "text-[#4b7a44]"
                        }`}
                    >
                        {
                            inputError
                            ??
                            `${formatWeight(validWeight ?? weightGrams)} selected`
                        }
                    </p>

                </div>


                <div
                    className="mt-5 flex items-center justify-between gap-4 border-t border-[#eadfd6] pt-4"
                >

                    <div>

                        <p
                            className="text-xs text-[#756763]"
                        >
                            Estimated price
                        </p>


                        <p
                            className="text-xl font-bold text-[#241715]"
                        >
                            {
                                estimatedPrice === null
                                    ? "—"
                                    : formatCurrency(
                                        estimatedPrice
                                    )
                            }
                        </p>

                    </div>


                    <button
                        type="button"
                        disabled={
                            validWeight === null
                        }
                        onClick={
                            () => {

                                if (
                                    validWeight === null
                                ) {

                                    return;
                                }


                                onConfirm(
                                    product,
                                    validWeight
                                );
                            }
                        }
                        className="min-h-12 rounded-xl bg-[#7a1625] px-5 text-sm font-bold text-white shadow-[0_5px_14px_rgba(122,22,37,0.22)] transition active:scale-[0.98] disabled:cursor-not-allowed disabled:bg-[#c9b9b4] disabled:shadow-none disabled:active:scale-100"
                    >
                        {
                            validWeight === null
                                ? "Check quantity"
                                : `Add ${formatWeight(validWeight)}`
                        }
                    </button>

                </div>


                <p
                    className="mt-3 text-center text-[11px] leading-4 text-[#756763]"
                >
                    Final amount is calculated by Gokul Sweets using the selected weight.
                </p>

            </section>

        </div>
    );
}


export default function WeightSelectorSheet({
    product,
    currentWeightGrams,
    onClose,
    onConfirm
}: WeightSelectorSheetProps) {

    if (
        !product
    ) {

        return null;
    }


    const minimum =
        product.minimumWeightGrams
        ?? 250;


    const initialWeightGrams =
        currentWeightGrams
        ?? minimum;


    return (
        <WeightSelectorDialog
            key={`${product.id}-${initialWeightGrams}`}
            product={
                product
            }
            initialWeightGrams={
                initialWeightGrams
            }
            onClose={
                onClose
            }
            onConfirm={
                onConfirm
            }
        />
    );
}
