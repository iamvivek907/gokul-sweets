import {
    adminFetch
} from "@/services/adminApi";

import type {
    PayrollOpeningBalance,
    SetPayrollOpeningBalancePayload
} from "@/types/adminPayrollOpeningBalance";


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


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


export async function getPayrollOpeningBalance(
    staffUserId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<PayrollOpeningBalance | null> {

    const response =
        await adminFetch(
            `/api/admin/payroll/staff/${staffUserId}/opening-balance`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    if (
        response.status === 204
    ) {

        return null;
    }


    await ensureOk(
        response,
        "Unable to load payroll opening balance."
    );


    return response.json();
}


export async function createPayrollOpeningBalance(
    staffUserId: number,
    request: SetPayrollOpeningBalancePayload,
    authorization: string
): Promise<PayrollOpeningBalance> {

    const response =
        await adminFetch(
            `/api/admin/payroll/staff/${staffUserId}/opening-balance`,
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
        "Unable to save payroll opening balance."
    );


    return response.json();
}
