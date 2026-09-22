"use client";

import type {
    PickupSlot
} from "@/types/pickup";


interface PickupSlotCardProps {

    slot:
        PickupSlot;

    selected:
        boolean;

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
            hour: "numeric",
            minute: "2-digit"
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
            style: "currency",
            currency: "INR",
            maximumFractionDigits: 0
        }
    ).format(
        amount
    );
}


function isSlotAvailable(
    slot: PickupSlot
): boolean {

    if (!slot.active) {
        return false;
    }


    const normalAvailable =
        slot.remainingCapacity > 0;


    const priorityAvailable =
        slot.priorityEnabled
        &&
        slot.priorityRemainingCapacity > 0;


    return (
        normalAvailable
        ||
        priorityAvailable
    );
}


export default function PickupSlotCard({
    slot,
    selected,
    onSelect
}: PickupSlotCardProps) {

    const available =
        isSlotAvailable(
            slot
        );


    return (
        <button
            type="button"
            disabled={
                !available
            }
            onClick={() =>
                onSelect(
                    slot
                )
            }
            className={`
                w-full
                rounded-2xl
                border
                p-4
                text-left
                transition
                active:scale-[0.99]

                ${
                    selected
                        ? "border-[#7a1625] bg-[#fff4e5]"
                        : "border-[#eadfd6] bg-white"
                }

                ${
                    !available
                        ? "cursor-not-allowed opacity-50"
                        : "hover:border-[#c88a20]"
                }
            `}
        >

            <div
                className="
                    flex
                    items-start
                    justify-between
                    gap-4
                "
            >

                <div>

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
                                text-base
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
                            && (

                                <span
                                    className="
                                        rounded-full
                                        bg-[#7a1625]
                                        px-2
                                        py-1
                                        text-[10px]
                                        font-bold
                                        uppercase
                                        tracking-wide
                                        text-white
                                    "
                                >
                                    Priority available
                                </span>

                            )
                        }

                    </div>


                    <p
                        className="
                            mt-2
                            text-xs
                            text-[#756763]
                        "
                    >
                        {
                            available
                                ? "Available for pickup"
                                : "Currently full"
                        }
                    </p>


                    {
                        available
                        && (

                            <div
                                className="
                                    mt-2
                                    space-y-1
                                    text-xs
                                "
                            >

                                <p
                                    className="
                                        font-medium
                                        text-[#4b7a44]
                                    "
                                >
                                    {
                                        slot.remainingCapacity
                                    }
                                    {" "}
                                    normal{" "}
                                    {
                                        slot.remainingCapacity === 1
                                            ? "slot"
                                            : "slots"
                                    }
                                    {" "}
                                    remaining
                                </p>


                                {
                                    slot.priorityEnabled
                                    && (

                                        <p
                                            className="
                                                font-medium
                                                text-[#7a1625]
                                            "
                                        >
                                            {
                                                slot.priorityRemainingCapacity
                                            }
                                            {" "}
                                            priority{" "}
                                            {
                                                slot.priorityRemainingCapacity === 1
                                                    ? "slot"
                                                    : "slots"
                                            }
                                            {" "}
                                            remaining
                                        </p>

                                    )
                                }

                            </div>

                        )
                    }

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
                        ? (

                            <p
                                className="
                                    text-sm
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
                        : (

                            <p
                                className="
                                    text-xs
                                    font-semibold
                                    text-[#4b7a44]
                                "
                            >
                                Normal pickup
                            </p>

                        )
                    }


                    {
                        selected
                        && (

                            <div
                                className="
                                    mt-2
                                    text-lg
                                    font-bold
                                    text-[#7a1625]
                                "
                            >
                                ✓
                            </div>

                        )
                    }

                </div>

            </div>

        </button>
    );
}