"use client";

import {
    useEffect,
    useRef,
    useState
} from "react";

import type {
    PickupSlot
} from "@/types/pickup";


interface PickupSlotDropdownProps {

    slots:
        PickupSlot[];

    selectedSlot:
        PickupSlot | null;

    reservedSlotId?:
        number
        | null;

    onSelect:
        (slot: PickupSlot) => void;
}


function formatTime(
    value: string
): string {

    const [
        hour,
        minute
    ] =
        value.split(":");


    const date =
        new Date();


    date.setHours(
        Number(hour),
        Number(minute),
        0,
        0
    );


    return new Intl.DateTimeFormat(
        "en-IN",
        {
            hour:
                "numeric",
            minute:
                "2-digit"
        }
    ).format(
        date
    );
}


function formatCurrency(
    amount: number
): string {

    return new Intl.NumberFormat(
        "en-IN",
        {
            style:
                "currency",
            currency:
                "INR",
            maximumFractionDigits:
                0
        }
    ).format(
        amount
    );
}


function isSlotAvailable(
    slot: PickupSlot
): boolean {

    if (
        !slot.active
    ) {

        return false;
    }


    return (
        slot.remainingCapacity > 0
        ||
        (
            slot.priorityEnabled
            &&
            slot.priorityRemainingCapacity > 0
        )
    );
}


export default function PickupSlotDropdown({
    slots,
    selectedSlot,
    reservedSlotId = null,
    onSelect
}: PickupSlotDropdownProps) {

    const [
        open,
        setOpen
    ] =
        useState(false);


    const containerRef =
        useRef<HTMLDivElement | null>(
            null
        );


    useEffect(
        () => {

            function handlePointerDown(
                event: MouseEvent
            ) {

                if (
                    containerRef.current
                    &&
                    !containerRef.current.contains(
                        event.target as Node
                    )
                ) {

                    setOpen(
                        false
                    );
                }
            }


            function handleKeyDown(
                event: KeyboardEvent
            ) {

                if (
                    event.key === "Escape"
                ) {

                    setOpen(
                        false
                    );
                }
            }


            document.addEventListener(
                "mousedown",
                handlePointerDown
            );


            document.addEventListener(
                "keydown",
                handleKeyDown
            );


            return () => {

                document.removeEventListener(
                    "mousedown",
                    handlePointerDown
                );


                document.removeEventListener(
                    "keydown",
                    handleKeyDown
                );
            };

        },
        []
    );


    function handleSelect(
        slot: PickupSlot
    ) {

        const reservedForCustomer =
            slot.id ===
                reservedSlotId;


        if (
            !reservedForCustomer
            &&
            !isSlotAvailable(
                slot
            )
        ) {

            return;
        }


        onSelect(
            slot
        );


        setOpen(
            false
        );
    }


    return (
        <div
            ref={
                containerRef
            }
            className="
                relative
                mt-3
                w-full
                min-w-0
            "
        >

            <button
                type="button"
                aria-haspopup="listbox"
                aria-expanded={
                    open
                }
                onClick={
                    () =>
                        setOpen(
                            current =>
                                !current
                        )
                }
                className={`
                    flex
                    min-h-14
                    w-full
                    min-w-0
                    items-center
                    justify-between
                    gap-3
                    rounded-2xl
                    border
                    bg-[#fffaf3]
                    px-4
                    py-3
                    text-left
                    outline-none
                    transition

                    focus:border-[#7a1625]
                    focus:ring-2
                    focus:ring-[#7a1625]/10

                    ${
                        open
                            ? "border-[#7a1625]"
                            : "border-[#eadfd6]"
                    }
                `}
            >

                <div
                    className="
                        min-w-0
                        flex-1
                    "
                >

                    {
                        selectedSlot
                            ? (
                                <>

                                    <p
                                        className="
                                            truncate
                                            text-sm
                                            font-bold
                                            text-[#241715]
                                        "
                                    >
                                        {
                                            formatTime(
                                                selectedSlot.startTime
                                            )
                                        }

                                        {" – "}

                                        {
                                            formatTime(
                                                selectedSlot.endTime
                                            )
                                        }
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            truncate
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        {
                                            selectedSlot.id ===
                                                reservedSlotId
                                                ? "Reserved for you"
                                                : selectedSlot.remainingCapacity > 0
                                                ? `${selectedSlot.remainingCapacity} normal slots left`
                                                : "Normal slots full"
                                        }

                                        {
                                            selectedSlot.id !==
                                                reservedSlotId
                                            &&
                                            selectedSlot.priorityEnabled
                                            &&
                                            selectedSlot.priorityRemainingCapacity > 0
                                                ? ` · ${selectedSlot.priorityRemainingCapacity} priority left`
                                                : ""
                                        }
                                    </p>

                                </>
                            )
                            : (
                                <>

                                    <p
                                        className="
                                            text-sm
                                            font-semibold
                                            text-[#241715]
                                        "
                                    >
                                        Select pickup time
                                    </p>


                                    <p
                                        className="
                                            mt-1
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        Tap to view available slots
                                    </p>

                                </>
                            )
                    }

                </div>


                <span
                    aria-hidden="true"
                    className={`
                        shrink-0
                        text-lg
                        text-[#7a1625]
                        transition-transform

                        ${
                            open
                                ? "rotate-180"
                                : ""
                        }
                    `}
                >
                   ⌄
                </span>

            </button>


            {
                open
                && (

                    <div
                        role="listbox"
                        aria-label="Pickup time"
                        className="
                            absolute
                            left-0
                            right-0
                            top-[calc(100%+8px)]
                            z-30
                            max-h-72
                            overflow-y-auto
                            overscroll-contain
                            rounded-2xl
                            border
                            border-[#eadfd6]
                            bg-white
                            p-2
                            shadow-[0_16px_40px_rgba(60,30,20,0.16)]
                        "
                    >

                        {
                            slots.map(
                                slot => {

                                    const available =
                                        isSlotAvailable(
                                            slot
                                        );


                                    const reservedForCustomer =
                                        slot.id ===
                                            reservedSlotId;


                                    const selectable =
                                        available
                                        ||
                                        reservedForCustomer;


                                    const selected =
                                        selectedSlot?.id
                                        === slot.id;


                                    return (
                                        <button
                                            key={
                                                slot.id
                                            }
                                            type="button"
                                            role="option"
                                            aria-selected={
                                                selected
                                            }
                                            disabled={
                                                !selectable
                                            }
                                            onClick={
                                                () =>
                                                    handleSelect(
                                                        slot
                                                    )
                                            }
                                            className={`
                                                flex
                                                w-full
                                                min-w-0
                                                items-start
                                                justify-between
                                                gap-3
                                                rounded-xl
                                                px-3
                                                py-3
                                                text-left
                                                transition

                                                ${
                                                    selected
                                                        ? "bg-[#fff0dc]"
                                                        : "hover:bg-[#fffaf3]"
                                                }

                                                ${
                                                    selectable
                                                        ? ""
                                                        : "cursor-not-allowed opacity-45"
                                                }
                                            `}
                                        >

                                            <div
                                                className="
                                                    min-w-0
                                                    flex-1
                                                "
                                            >

                                                <div
                                                    className="
                                                        flex
                                                        flex-wrap
                                                        items-center
                                                        gap-2
                                                    "
                                                >

                                                    <p
                                                        className="
                                                            text-sm
                                                            font-bold
                                                            text-[#241715]
                                                        "
                                                    >
                                                        {
                                                            formatTime(
                                                                slot.startTime
                                                            )
                                                        }

                                                        {" – "}

                                                        {
                                                            formatTime(
                                                                slot.endTime
                                                            )
                                                        }
                                                    </p>


                                                    {
                                                        slot.priorityEnabled
                                                        &&
                                                        slot.priorityRemainingCapacity > 0
                                                        && (

                                                            <span
                                                                className="
                                                                    rounded-full
                                                                    bg-[#fff0dc]
                                                                    px-2
                                                                    py-0.5
                                                                    text-[9px]
                                                                    font-bold
                                                                    uppercase
                                                                    tracking-wide
                                                                    text-[#7a1625]
                                                                "
                                                            >
                                                                Priority
                                                            </span>

                                                        )
                                                    }

                                                </div>


                                                <p
                                                    className={`
                                                        mt-1
                                                        text-xs

                                                        ${
                                                            available
                                                                ? "text-[#756763]"
                                                                : "text-red-600"
                                                        }
                                                    `}
                                                >
                                                    {
                                                        reservedForCustomer
                                                            ? "Reserved for you"
                                                            : available
                                                            ? (
                                                                <>
                                                                    {
                                                                        slot.remainingCapacity > 0
                                                                            ? `${slot.remainingCapacity} normal left`
                                                                            : "Normal full"
                                                                    }

                                                                    {
                                                                        slot.priorityEnabled
                                                                        &&
                                                                        slot.priorityRemainingCapacity > 0
                                                                            ? ` · ${slot.priorityRemainingCapacity} priority left`
                                                                            : ""
                                                                    }
                                                                </>
                                                            )
                                                            : "All slots occupied"
                                                    }
                                                </p>

                                            </div>


                                            <div
                                                className="
                                                    shrink-0
                                                    text-right
                                                "
                                            >

                                                {
                                                    slot.priorityEnabled
                                                    &&
                                                    slot.priorityRemainingCapacity > 0
                                                    &&
                                                    slot.priorityCharge > 0
                                                    && (

                                                        <p
                                                            className="
                                                                text-xs
                                                                font-bold
                                                                text-[#7a1625]
                                                            "
                                                        >
                                                            +
                                                            {
                                                                formatCurrency(
                                                                    slot.priorityCharge
                                                                )
                                                            }
                                                        </p>

                                                    )
                                                }


                                                {
                                                    selected
                                                    && (

                                                        <p
                                                            className="
                                                                mt-1
                                                                text-sm
                                                                font-bold
                                                                text-[#7a1625]
                                                            "
                                                        >
                                                            ✓
                                                        </p>

                                                    )
                                                }

                                            </div>

                                        </button>
                                    );
                                }
                            )
                        }

                    </div>

                )
            }

        </div>
    );
}
