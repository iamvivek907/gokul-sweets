import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminApprovalCounts,
    AdminApprovalDetail,
    AdminApprovalOptions,
    AdminApprovalRequest,
    ApprovalRequestStatus,
    ApprovalRequestType,
    SpringPage
} from "@/types/adminApprovals";


async function readError(
    response: Response,
    fallback: string
) {

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
         * Response may not be JSON.
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
            "Your admin session is no longer authorized."
        );
    }


    if (response.status === 403) {

        throw new Error(
            "You do not have permission to view or manage this approval."
        );
    }


    throw new Error(
        await readError(
            response,
            fallback
        )
    );
}


function buildQuery(
    values: Record<
        string,
        string | number | null | undefined
    >
) {

    const params =
        new URLSearchParams();


    Object.entries(
        values
    ).forEach(
        ([
            key,
            value
        ]) => {

            if (
                value !== null
                &&
                value !== undefined
                &&
                value !== ""
            ) {

                params.set(
                    key,
                    String(
                        value
                    )
                );
            }
        }
    );


    const query =
        params.toString();


    return query
        ? `?${query}`
        : "";
}


export async function getAdminApprovals(
    authorization: string,
    filters: {
        branchId?: number | null;
        type?: ApprovalRequestType | null;
        status?: ApprovalRequestStatus | null;
        page?: number;
        size?: number;
    },
    signal?: AbortSignal
): Promise<SpringPage<AdminApprovalRequest>> {

    const query =
        buildQuery({
            branchId:
                filters.branchId,
            type:
                filters.type,
            status:
                filters.status,
            page:
                filters.page
                ?? 0,
            size:
                filters.size
                ?? 25
        });


    const response =
        await adminFetch(
            `/api/admin/approvals${query}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load approvals."
    );


    return response.json();
}


export async function getAdminApprovalCounts(
    authorization: string,
    filters: {
        branchId?: number | null;
        type?: ApprovalRequestType | null;
    },
    signal?: AbortSignal
): Promise<AdminApprovalCounts> {

    const query =
        buildQuery({
            branchId:
                filters.branchId,
            type:
                filters.type
        });


    const response =
        await adminFetch(
            `/api/admin/approvals/counts${query}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load approval counts."
    );


    return response.json();
}


export async function getAdminApprovalOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<AdminApprovalOptions> {

    const response =
        await adminFetch(
            "/api/admin/approvals/options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load approval options."
    );


    return response.json();
}


export async function getAdminApprovalDetail(
    approvalRequestId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminApprovalDetail> {

    const response =
        await adminFetch(
            `/api/admin/approvals/${approvalRequestId}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load approval details."
    );


    return response.json();
}


async function actOnApproval(
    approvalRequestId: number,
    action:
        | "approve"
        | "reject"
        | "send-back",
    comment: string | null,
    authorization: string
): Promise<AdminApprovalRequest> {

    const response =
        await adminFetch(
            `/api/admin/approvals/${approvalRequestId}/${action}`,
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
        "Unable to action the approval request."
    );


    return response.json();
}


export function approveAdminApproval(
    approvalRequestId: number,
    comment: string | null,
    authorization: string
) {

    return actOnApproval(
        approvalRequestId,
        "approve",
        comment,
        authorization
    );
}


export function rejectAdminApproval(
    approvalRequestId: number,
    comment: string,
    authorization: string
) {

    return actOnApproval(
        approvalRequestId,
        "reject",
        comment,
        authorization
    );
}


export function sendBackAdminApproval(
    approvalRequestId: number,
    comment: string,
    authorization: string
) {

    return actOnApproval(
        approvalRequestId,
        "send-back",
        comment,
        authorization
    );
}
