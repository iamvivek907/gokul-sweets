import {
    adminFetch
} from "@/services/adminApi";

import type {
    CreateLeaveRequestPayload,
    LeaveOptions,
    LeaveRequestItem,
    SpringPage,
    UpdateLeaveRequestPayload
} from "@/types/adminLeaveRequests";


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
            "You do not have access to perform this leave action."
        );
    }


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


export async function getLeaveOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<LeaveOptions> {

    const response =
        await adminFetch(
            "/api/admin/me/leave-requests/options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load leave options."
    );


    return response.json();
}


export async function getMyLeaveRequests(
    authorization: string,
    page: number,
    size = 20,
    signal?: AbortSignal
): Promise<SpringPage<LeaveRequestItem>> {

    const response =
        await adminFetch(
            `/api/admin/me/leave-requests?page=${page}&size=${size}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load leave requests."
    );


    return response.json();
}


export async function createLeaveRequest(
    request: CreateLeaveRequestPayload,
    authorization: string
): Promise<LeaveRequestItem> {

    const response =
        await adminFetch(
            "/api/admin/me/leave-requests",
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
        "Unable to submit leave request."
    );


    return response.json();
}


export async function updateLeaveRequest(
    leaveRequestId: number,
    request: UpdateLeaveRequestPayload,
    authorization: string
): Promise<LeaveRequestItem> {

    const response =
        await adminFetch(
            `/api/admin/me/leave-requests/${leaveRequestId}`,
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
        "Unable to update leave request."
    );


    return response.json();
}


export async function resubmitLeaveRequest(
    leaveRequestId: number,
    comment: string | null,
    authorization: string
): Promise<LeaveRequestItem> {

    const response =
        await adminFetch(
            `/api/admin/me/leave-requests/${leaveRequestId}/resubmit`,
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
        "Unable to resubmit leave request."
    );


    return response.json();
}


export async function cancelLeaveRequest(
    leaveRequestId: number,
    comment: string | null,
    authorization: string
): Promise<LeaveRequestItem> {

    const response =
        await adminFetch(
            `/api/admin/me/leave-requests/${leaveRequestId}/cancel`,
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
        "Unable to cancel leave request."
    );


    return response.json();
}
