"use client";

import Link from "next/link";

type CheckoutStateTone =
    | "neutral"
    | "warning"
    | "error";

interface CheckoutStateAction {
    label: string;
    href: string;
    primary?: boolean;
}

interface CheckoutStateCardProps {
    title: string;
    message: string;
    tone?: CheckoutStateTone;
    detail?: string;
    primaryAction?: CheckoutStateAction;
    secondaryAction?: CheckoutStateAction;
}

function iconForTone(
    tone: CheckoutStateTone
) {

    if (tone === "error") {

        return (
            <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
                className="h-6 w-6"
            >
                <circle
                    cx="12"
                    cy="12"
                    r="9"
                    stroke="currentColor"
                    strokeWidth="1.9"
                />
                <path
                    d="M12 7.5v5"
                    stroke="currentColor"
                    strokeWidth="1.9"
                    strokeLinecap="round"
                />
                <circle
                    cx="12"
                    cy="16.5"
                    r="1"
                    fill="currentColor"
                />
            </svg>
        );
    }


    if (tone === "warning") {

        return (
            <svg
                viewBox="0 0 24 24"
                fill="none"
                aria-hidden="true"
                className="h-6 w-6"
            >
                <path
                    d="M12 3.5l9 16H3l9-16z"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinejoin="round"
                />
                <path
                    d="M12 9v4.5"
                    stroke="currentColor"
                    strokeWidth="1.8"
                    strokeLinecap="round"
                />
                <circle
                    cx="12"
                    cy="16.8"
                    r=".9"
                    fill="currentColor"
                />
            </svg>
        );
    }


    return (
        <svg
            viewBox="0 0 24 24"
            fill="none"
            aria-hidden="true"
            className="h-6 w-6"
        >
            <path
                d="M7 7h10l1 13H6L7 7z"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinejoin="round"
            />
            <path
                d="M9 7a3 3 0 016 0"
                stroke="currentColor"
                strokeWidth="1.8"
                strokeLinecap="round"
            />
        </svg>
    );
}


export default function CheckoutStateCard({
    title,
    message,
    tone = "neutral",
    detail,
    primaryAction,
    secondaryAction
}: CheckoutStateCardProps) {

    const toneClasses =
        tone === "error"
            ? "border-red-100 bg-red-50 text-red-700"
            : tone === "warning"
                ? "border-amber-200 bg-amber-50 text-amber-800"
                : "border-[#eadfd6] bg-[#fffaf3] text-[#7a1625]";


    return (

        <div
            className="
                rounded-3xl
                border
                border-[#eadfd6]
                bg-white
                px-6
                py-10
                text-center
                shadow-sm
            "
        >

            <div
                className={`
                    mx-auto
                    flex
                    h-12
                    w-12
                    items-center
                    justify-center
                    rounded-full
                    border
                    ${toneClasses}
                `}
            >
                {
                    iconForTone(
                        tone
                    )
                }
            </div>


            <h1
                className="
                    mt-4
                    text-xl
                    font-bold
                    text-[#241715]
                "
            >
                {title}
            </h1>


            <p
                className="
                    mx-auto
                    mt-2
                    max-w-md
                    text-sm
                    leading-6
                    text-[#756763]
                "
            >
                {message}
            </p>


            {
                detail
                && (

                    <div
                        className={`
                            mt-5
                            rounded-2xl
                            border
                            p-4
                            text-left
                            text-sm
                            leading-6
                            ${toneClasses}
                        `}
                    >
                        {detail}
                    </div>

                )
            }


            {
                primaryAction
                && (

                    <Link
                        href={
                            primaryAction.href
                        }
                        className="
                            mt-6
                            flex
                            min-h-12
                            w-full
                            items-center
                            justify-center
                            rounded-xl
                            bg-[#7a1625]
                            px-6
                            font-bold
                            text-white
                            transition

                            hover:bg-[#5d0f1b]
                        "
                    >
                        {
                            primaryAction.label
                        }
                    </Link>

                )
            }


            {
                secondaryAction
                && (

                    <Link
                        href={
                            secondaryAction.href
                        }
                        className="
                            mt-3
                            flex
                            min-h-11
                            w-full
                            items-center
                            justify-center
                            text-sm
                            font-semibold
                            text-[#7a1625]
                        "
                    >
                        {
                            secondaryAction.label
                        }
                    </Link>

                )
            }

        </div>
    );
}
