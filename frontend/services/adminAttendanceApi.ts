import {
    adminFetch
} from "@/services/adminApi";

import type {
    AttendanceApprovalHistoryItem,
    AttendanceItem,
    AttendanceOptions,
    CreateAttendancePayload,
    SpringPage,
    UpdateAttendancePayload
} from "@/types/adminAttendance";


async function readErrorMessage(
    response: Response,
    fallback: string
): Promise<string> {

    try {

        const body =
            await response.json() as {
                message?: string;
                error?: string;
            };


        if (
            typeof body.message === "string"
            &&
            body.message.trim()
        ) {

            return body.message;
        }


        if (
            typeof body.error === "string"
            &&
            body.error.trim()
        ) {

            return body.error;
        }

    } catch {

        /*
         * Response may not contain JSON.
         */
    }


    return fallback;
}


async function ensureOk(
    response: Response,
    fallback: string
) {

    if (response.ok) {

        return;
    }


    if (response.status === 401) {

        throw new Error(
            "Your staff session is no longer authorized."
        );
    }


    if (response.status === 403) {

        throw new Error(
            "You do not have access to perform this attendance action."
        );
    }


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


export async function getAttendanceOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<AttendanceOptions> {

    const response =
        await adminFetch(
            "/api/admin/me/attendance/options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load attendance options."
    );


    return response.json();
}


export async function getMyAttendance(
    authorization: string,
    page: number,
    size = 20,
    signal?: AbortSignal
): Promise<SpringPage<AttendanceItem>> {

    const response =
        await adminFetch(
            `/api/admin/me/attendance?page=${page}&size=${size}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load attendance."
    );


    return response.json();
}


export async function getMyAttendanceHistory(
    attendanceId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AttendanceApprovalHistoryItem[]> {

    const response =
        await adminFetch(
            `/api/admin/me/attendance/${attendanceId}/history`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load attendance approval history."
    );


    return response.json();
}


export async function createAttendance(
    request: CreateAttendancePayload,
    authorization: string
): Promise<AttendanceItem> {

    const response =
        await adminFetch(
            "/api/admin/me/attendance",
            authorization,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(
                    request
                )
            }
        );


    await ensureOk(
        response,
        "Unable to submit attendance."
    );


    return response.json();
}


export async function updateAttendance(
    attendanceId: number,
    request: UpdateAttendancePayload,
    authorization: string
): Promise<AttendanceItem> {

    const response =
        await adminFetch(
            `/api/admin/me/attendance/${attendanceId}`,
            authorization,
            {
                method: "PUT",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify(
                    request
                )
            }
        );


    await ensureOk(
        response,
        "Unable to update attendance."
    );


    return response.json();
}


export async function resubmitAttendance(
    attendanceId: number,
    comment: string | null,
    authorization: string
): Promise<AttendanceItem> {

    const response =
        await adminFetch(
            `/api/admin/me/attendance/${attendanceId}/resubmit`,
            authorization,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    comment
                })
            }
        );


    await ensureOk(
        response,
        "Unable to resubmit attendance."
    );


    return response.json();
}


export async function cancelAttendance(
    attendanceId: number,
    comment: string | null,
    authorization: string
): Promise<AttendanceItem> {

    const response =
        await adminFetch(
            `/api/admin/me/attendance/${attendanceId}/cancel`,
            authorization,
            {
                method: "POST",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    comment
                })
            }
        );


    await ensureOk(
        response,
        "Unable to cancel attendance."
    );


    return response.json();
}
