"use client";

import {
    useEffect,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getMyPayrollPaymentHistory
} from "@/services/adminPayrollPaymentHistoryApi";

import type {
    PayrollPaymentApprovalHistoryItem
} from "@/types/adminPayrollPaymentHistory";


function pretty(
    value: string
) {

    return value
        .toLowerCase()
        .split("_")
        .map(
            part =>
                part.charAt(0)
                    .toUpperCase()
                +
                part.slice(1)
        )
        .join(" ");
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


export default function PayrollPaymentApprovalHistory({
    paymentRequestId
}: {
    paymentRequestId: number;
}) {

    const {
        authorization
    } =
        useAdminAuth();


    const [
        result,
        setResult
    ] =
        useState<{
            paymentRequestId: number;
            history: PayrollPaymentApprovalHistoryItem[];
            error: string | null;
        } | null>(
            null
        );


    const loading =
        result?.paymentRequestId
        !== paymentRequestId;


    const history =
        result?.paymentRequestId
        === paymentRequestId
            ? result.history
            : [];


    const error =
        result?.paymentRequestId
        === paymentRequestId
            ? result.error
            : null;


    useEffect(
        () => {

            if (
                authorization === null
            ) {

                return;
            }


            const controller =
                new AbortController();


            getMyPayrollPaymentHistory(
                paymentRequestId,
                authorization,
                controller.signal
            )
                .then(
                    (
                        historyResult:
                            PayrollPaymentApprovalHistoryItem[]
                    ) => {

                        setResult({
                            paymentRequestId,
                            history:
                                historyResult,
                            error:
                                null
                        });
                    }
                )
                .catch(
                    (
                        exception:
                            unknown
                    ) => {

                        if (
                            exception instanceof DOMException
                            &&
                            exception.name === "AbortError"
                        ) {

                            return;
                        }


                        setResult({
                            paymentRequestId,
                            history: [],
                            error:
                                exception instanceof Error
                                    ? exception.message
                                    : "Unable to load payment approval history."
                        });
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            paymentRequestId
        ]
    );


    if (loading) {

        return (
            <div
                className="
                    mt-4
                    rounded-xl
                    border
                    border-[#eadfd6]
                    bg-[#fffaf3]
                    p-4
                    text-xs
                    text-[#756763]
                "
            >
                Loading approval history...
            </div>
        );
    }


    if (error) {

        return (
            <div
                className="
                    mt-4
                    rounded-xl
                    border
                    border-red-200
                    bg-red-50
                    p-4
                    text-xs
                    text-red-700
                "
            >
                {error}
            </div>
        );
    }


    if (
        history.length === 0
    ) {

        return null;
    }


    return (
        <div
            className="
                mt-4
                space-y-3
                border-t
                border-[#eadfd6]
                pt-4
            "
        >

            <p
                className="
                    text-xs
                    font-bold
                    uppercase
                    tracking-0.1em
                    text-[#756763]
                "
            >
                Approval History
            </p>


            {
                history.map(
                    item => (

                        <div
                            key={
                                item.id
                            }
                            className="
                                rounded-xl
                                border
                                border-[#eadfd6]
                                bg-[#fffaf3]
                                p-3
                            "
                        >

                            <div
                                className="
                                    flex
                                    flex-col
                                    gap-1
                                    sm:flex-row
                                    sm:items-center
                                    sm:justify-between
                                "
                            >

                                <p
                                    className="
                                        text-xs
                                        font-bold
                                        text-[#241715]
                                    "
                                >
                                    {pretty(item.action)}
                                </p>


                                <p
                                    className="
                                        text-[11px]
                                        text-[#756763]
                                    "
                                >
                                    {formatDateTime(item.createdAt)}
                                </p>

                            </div>


                            <p
                                className="
                                    mt-1
                                    text-[11px]
                                    text-[#756763]
                                "
                            >
                                {item.actorName}
                            </p>


                            {
                                item.comment
                                && (

                                    <p
                                        className="
                                            mt-2
                                            whitespace-pre-wrap
                                            text-xs
                                            leading-5
                                            text-[#241715]
                                        "
                                    >
                                        {item.comment}
                                    </p>

                                )
                            }

                        </div>

                    )
                )
            }

        </div>
    );
}
