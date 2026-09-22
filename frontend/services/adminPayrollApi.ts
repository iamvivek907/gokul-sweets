import {
    adminFetch
} from "@/services/adminApi";

import type {
    CreatePayrollPaymentRequest,
    PayrollEarning,
    PayrollOptions,
    PayrollPaymentRequest,
    PayrollSummary,
    SetCompensationPayload,
    SpringPage,
    StaffCompensation,
    UpdatePayrollPaymentRequest
} from "@/types/adminPayroll";


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
            "You do not have permission to perform this payroll action."
        );
    }


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


export async function getMyPayrollOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<PayrollOptions> {

    const response =
        await adminFetch(
            "/api/admin/me/payroll/options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load payroll options."
    );


    return response.json();
}


export async function getMyPayrollSummary(
    authorization: string,
    signal?: AbortSignal
): Promise<PayrollSummary> {

    const response =
        await adminFetch(
            "/api/admin/me/payroll/summary",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load payroll summary."
    );


    return response.json();
}


export async function getMyPayrollEarnings(
    authorization: string,
    page: number,
    size = 20,
    signal?: AbortSignal
): Promise<SpringPage<PayrollEarning>> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/earnings?page=${page}&size=${size}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load payroll earnings."
    );


    return response.json();
}


export async function getMyPayrollPaymentRequests(
    authorization: string,
    page: number,
    size = 20,
    signal?: AbortSignal
): Promise<SpringPage<PayrollPaymentRequest>> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/payment-requests?page=${page}&size=${size}`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load payment requests."
    );


    return response.json();
}


export async function createPayrollPaymentRequest(
    request: CreatePayrollPaymentRequest,
    authorization: string
): Promise<PayrollPaymentRequest> {

    const response =
        await adminFetch(
            "/api/admin/me/payroll/payment-requests",
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
        "Unable to submit money-taken request."
    );


    return response.json();
}


export async function updatePayrollPaymentRequest(
    paymentRequestId: number,
    request: UpdatePayrollPaymentRequest,
    authorization: string
): Promise<PayrollPaymentRequest> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/payment-requests/${paymentRequestId}`,
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
        "Unable to update payment request."
    );


    return response.json();
}


export async function resubmitPayrollPaymentRequest(
    paymentRequestId: number,
    comment: string | null,
    authorization: string
): Promise<PayrollPaymentRequest> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/payment-requests/${paymentRequestId}/resubmit`,
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
        "Unable to resubmit payment request."
    );


    return response.json();
}


export async function cancelPayrollPaymentRequest(
    paymentRequestId: number,
    comment: string | null,
    authorization: string
): Promise<PayrollPaymentRequest> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/payment-requests/${paymentRequestId}/cancel`,
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
        "Unable to cancel payment request."
    );


    return response.json();
}


export async function getAdminPayrollSummary(
    staffUserId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<PayrollSummary> {

    const response =
        await adminFetch(
            `/api/admin/payroll/staff/${staffUserId}/summary`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load staff payroll summary."
    );


    return response.json();
}


export async function getStaffCompensation(
    staffUserId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<StaffCompensation[]> {

    const response =
        await adminFetch(
            `/api/admin/payroll/staff/${staffUserId}/compensation`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load compensation history."
    );


    return response.json();
}


export async function setStaffCompensation(
    staffUserId: number,
    request: SetCompensationPayload,
    authorization: string
): Promise<StaffCompensation> {

    const response =
        await adminFetch(
            `/api/admin/payroll/staff/${staffUserId}/compensation`,
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
        "Unable to save staff compensation."
    );


    return response.json();
}
