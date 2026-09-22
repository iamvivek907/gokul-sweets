import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminStaff,
    AdminStaffManagementOptions,
    CreateAdminStaffRequest,
    UpdateAdminStaffRequest
} from "@/types/adminStaff";


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
            "You do not have permission to manage this staff member."
        );
    }


    throw new Error(
        await readErrorMessage(
            response,
            fallback
        )
    );
}


export async function getAdminStaff(
    authorization: string,
    signal?: AbortSignal
): Promise<AdminStaff[]> {

    const response =
        await adminFetch(
            "/api/admin/staff",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load staff."
    );


    return response.json();
}


export async function getAdminStaffOptions(
    authorization: string,
    signal?: AbortSignal
): Promise<AdminStaffManagementOptions> {

    const response =
        await adminFetch(
            "/api/admin/staff/options",
            authorization,
            {
                method: "GET",
                signal
            }
        );


    await ensureOk(
        response,
        "Unable to load staff management options."
    );


    return response.json();
}


export async function createAdminStaff(
    request: CreateAdminStaffRequest,
    authorization: string
): Promise<AdminStaff> {

    const response =
        await adminFetch(
            "/api/admin/staff",
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
        "Unable to create staff member."
    );


    return response.json();
}


export async function updateAdminStaff(
    staffId: number,
    request: UpdateAdminStaffRequest,
    authorization: string
): Promise<AdminStaff> {

    const response =
        await adminFetch(
            `/api/admin/staff/${staffId}`,
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
        "Unable to update staff member."
    );


    return response.json();
}


export async function resetAdminStaffPassword(
    staffId: number,
    newPassword: string,
    authorization: string
): Promise<void> {

    const response =
        await adminFetch(
            `/api/admin/staff/${staffId}/password`,
            authorization,
            {
                method: "PATCH",

                headers: {
                    "Content-Type": "application/json"
                },

                body: JSON.stringify({
                    newPassword
                })
            }
        );


    await ensureOk(
        response,
        "Unable to reset staff password."
    );
}
