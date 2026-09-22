"use client";

import {
    useState
} from "react";

import type {
    CustomerDetails
} from "@/types/customer";


interface CustomerDetailsFormProps {

    initialValue?:
        CustomerDetails | null;

    onSubmit:
        (customer: CustomerDetails) => void;
}


interface FormErrors {
    name?: string;
    phone?: string;
}


function normalizePhone(
    value: string
): string {

    return value.replace(
        /\D/g,
        ""
    );
}


function validateIndianPhone(
    phone: string
): boolean {

    return /^[6-9]\d{9}$/.test(
        phone
    );
}


export default function CustomerDetailsForm({
    initialValue,
    onSubmit
}: CustomerDetailsFormProps) {

    const [
        name,
        setName
    ] =
        useState(
            initialValue?.name
            ?? ""
        );


    const [
        phone,
        setPhone
    ] =
        useState(
            initialValue?.phone
            ?? ""
        );


    const [
        errors,
        setErrors
    ] =
        useState<FormErrors>(
            {}
        );


    function handleSubmit(
        event: React.FormEvent<HTMLFormElement>
    ) {

        event.preventDefault();


        const normalizedName =
            name.trim();


        const normalizedPhone =
            normalizePhone(
                phone
            );


        const nextErrors:
            FormErrors = {};


        if (
            normalizedName.length < 2
        ) {

            nextErrors.name =
                "Please enter your name.";
        }


        if (
            !validateIndianPhone(
                normalizedPhone
            )
        ) {

            nextErrors.phone =
                "Enter a valid 10-digit mobile number.";
        }


        setErrors(
            nextErrors
        );


        if (
            Object.keys(
                nextErrors
            ).length > 0
        ) {

            return;
        }


        onSubmit(
            {
                name:
                    normalizedName,

                phone:
                    normalizedPhone
            }
        );
    }


    return (
        <form
            onSubmit={
                handleSubmit
            }
            className="
                space-y-5
            "
        >

            <div>

                <label
                    htmlFor="customer-name"
                    className="
                        block
                        text-sm
                        font-semibold
                        text-[#241715]
                    "
                >
                    Your name
                </label>


                <input
                    id="customer-name"
                    type="text"
                    value={
                        name
                    }
                    onChange={
                        event =>
                            setName(
                                event.target.value
                            )
                    }
                    autoComplete="name"
                    placeholder="Enter your name"
                    className="
                        mt-2
                        min-h-12
                        w-full
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white
                        px-4
                        text-sm
                        text-[#241715]
                        outline-none
                        transition

                        focus:border-[#7a1625]
                        focus:ring-2
                        focus:ring-[#7a1625]/10
                    "
                />


                {
                    errors.name
                    && (

                        <p
                            className="
                                mt-2
                                text-xs
                                font-medium
                                text-red-600
                            "
                        >
                            {
                                errors.name
                            }
                        </p>

                    )
                }

            </div>


            <div>

                <label
                    htmlFor="customer-phone"
                    className="
                        block
                        text-sm
                        font-semibold
                        text-[#241715]
                    "
                >
                    Mobile number
                </label>


                <div
                    className="
                        mt-2
                        flex
                        overflow-hidden
                        rounded-xl
                        border
                        border-[#eadfd6]
                        bg-white

                        focus-within:border-[#7a1625]
                        focus-within:ring-2
                        focus-within:ring-[#7a1625]/10
                    "
                >

                    <div
                        className="
                            flex
                            items-center
                            border-r
                            border-[#eadfd6]
                            bg-[#fffaf3]
                            px-4
                            text-sm
                            font-semibold
                            text-[#756763]
                        "
                    >
                        +91
                    </div>


                    <input
                        id="customer-phone"
                        type="tel"
                        inputMode="numeric"
                        value={
                            phone
                        }
                        onChange={
                            event => {

                                const value =
                                    normalizePhone(
                                        event.target.value
                                    );


                                setPhone(
                                    value.slice(
                                        0,
                                        10
                                    )
                                );
                            }
                        }
                        autoComplete="tel"
                        placeholder="9876543210"
                        className="
                            min-h-12
                            min-w-0
                            flex-1
                            px-4
                            text-sm
                            text-[#241715]
                            outline-none
                        "
                    />

                </div>


                {
                    errors.phone
                    && (

                        <p
                            className="
                                mt-2
                                text-xs
                                font-medium
                                text-red-600
                            "
                        >
                            {
                                errors.phone
                            }
                        </p>

                    )
                }


                <p
                    className="
                        mt-2
                        text-xs
                        leading-5
                        text-[#756763]
                    "
                >
                    We&apos;ll use this number for
                    order updates and pickup
                    identification.
                </p>

            </div>


            <button
                type="submit"
                className="
                    flex
                    min-h-12
                    w-full
                    items-center
                    justify-center
                    rounded-xl
                    bg-[#7a1625]
                    px-5
                    font-bold
                    text-white
                    transition
                    active:scale-[0.98]
                "
            >
                Continue to Review
            </button>

        </form>
    );
}