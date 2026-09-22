"use client";

import {
    useEffect,
    useState
} from "react";

import {
    useAdminAuth
} from "@/contexts/AdminAuthContext";

import {
    getMyAttendanceHistory
} from "@/services/adminAttendanceApi";

import type {
    AttendanceApprovalHistoryItem
} from "@/types/adminAttendance";


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


export default function AttendanceApprovalHistory({
    attendanceId
}: {
    attendanceId: number;
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
            attendanceId: number;
            history: AttendanceApprovalHistoryItem[];
            error: string | null;
        } | null>(
            null
        );


    const loading =
        result?.attendanceId
        !== attendanceId;


    const history =
        result?.attendanceId
        === attendanceId
            ? result.history
            : [];


    const error =
        result?.attendanceId
        === attendanceId
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


            getMyAttendanceHistory(
                attendanceId,
                authorization,
                controller.signal
            )
                .then(
                    historyResult => {

                        setResult({
                            attendanceId,
                            history:
                                historyResult,
                            error:
                                null
                        });
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


                        setResult({
                            attendanceId,
                            history: [],
                            error:
                                exception instanceof Error
                                    ? exception.message
                                    : "Unable to load approval history."
                        });
                    }
                );


            return () => {

                controller.abort();
            };

        },
        [
            authorization,
            attendanceId
        ]
    );


    if (loading) {

        return (
            <div
                className="
                    mt-6
                    rounded-xl
                    border
                    border-[#eadfd6]
                    bg-[#fffaf3]
                    p-4
                    text-sm
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
                    mt-6
                    rounded-xl
                    border
                    border-red-200
                    bg-red-50
                    p-4
                    text-sm
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
                mt-6
                border-t
                border-[#eadfd6]
                pt-6
            "
        >

            <h3
                className="
                    text-sm
                    font-bold
                    text-[#241715]
                "
            >
                Approval History
            </h3>


            <div
                className="
                    mt-4
                    space-y-3
                "
            >

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
                                    bg-white
                                    p-4
                                "
                            >

                                <div
                                    className="
                                        flex
                                        flex-col
                                        gap-2
                                        sm:flex-row
                                        sm:items-start
                                        sm:justify-between
                                    "
                                >

                                    <div>

                                        <p
                                            className="
                                                text-sm
                                                font-bold
                                                text-[#241715]
                                            "
                                        >
                                            {pretty(item.action)}
                                        </p>


                                        <p
                                            className="
                                                mt-1
                                                text-xs
                                                text-[#756763]
                                            "
                                        >
                                            {item.actorName}
                                        </p>

                                    </div>


                                    <p
                                        className="
                                            text-xs
                                            text-[#756763]
                                        "
                                    >
                                        {formatDateTime(item.createdAt)}
                                    </p>

                                </div>


                                {
                                    item.comment
                                    && (

                                        <div
                                            className="
                                                mt-3
                                                rounded-lg
                                                bg-[#fffaf3]
                                                px-3
                                                py-2.5
                                            "
                                        >

                                            <p
                                                className="
                                                    text-xs
                                                    font-semibold
                                                    text-[#756763]
                                                "
                                            >
                                                Comment
                                            </p>


                                            <p
                                                className="
                                                    mt-1
                                                    whitespace-pre-wrap
                                                    text-sm
                                                    leading-6
                                                    text-[#241715]
                                                "
                                            >
                                                {item.comment}
                                            </p>

                                        </div>

                                    )
                                }

                            </div>

                        )
                    )
                }

            </div>

        </div>
    );
}
