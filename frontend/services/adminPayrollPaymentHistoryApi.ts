import {
    adminFetch
} from "@/services/adminApi";

import type {
    PayrollPaymentApprovalHistoryItem
} from "@/types/adminPayrollPaymentHistory";


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


export async function getMyPayrollPaymentHistory(
    paymentRequestId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<PayrollPaymentApprovalHistoryItem[]> {

    const response =
        await adminFetch(
            `/api/admin/me/payroll/payment-requests/${paymentRequestId}/history`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    if (!response.ok) {

        throw new Error(
            await readErrorMessage(
                response,
                "Unable to load payment approval history."
            )
        );
    }


    return response.json() as Promise<
        PayrollPaymentApprovalHistoryItem[]
    >;
}
