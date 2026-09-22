import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminPayrollStaffOption
} from "@/types/adminPayrollAdmin";

import type {
    PayrollSummary,
    SetCompensationPayload,
    StaffCompensation
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
            "Your admin session is no longer authorized."
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


export async function getAdminPayrollStaffOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<AdminPayrollStaffOption[]> {

    const response =
        await adminFetch(
            "/api/admin/payroll/staff-options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load payroll staff."
    );


    return response.json();
}


export async function getAdminPayrollStaffSummary(
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
        "Unable to load payroll summary."
    );


    return response.json();
}


export async function getAdminPayrollCompensation(
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


export async function createAdminPayrollCompensation(
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
        "Unable to save compensation."
    );


    return response.json();
}
