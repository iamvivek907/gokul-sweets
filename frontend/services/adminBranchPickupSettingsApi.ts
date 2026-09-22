import {
    adminFetch
} from "@/services/adminApi";

import type {
    AdminBranchPickupSettings,
    UpdateAdminBranchPickupSettingsRequest
} from "@/types/adminBranchPickupSettings";


export class AdminBranchPickupSettingsApiError
    extends Error {

    readonly status: number;


    constructor(
        status: number,
        message: string
    ) {

        super(
            message
        );

        this.name =
            "AdminBranchPickupSettingsApiError";

        this.status =
            status;
    }
}


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


async function throwApiError(
    response: Response,
    fallback: string
): Promise<never> {

    const message =
        await readErrorMessage(
            response,
            fallback
        );


    throw new AdminBranchPickupSettingsApiError(
        response.status,
        message
    );
}


export async function getAdminBranchPickupSettings(
    branchId: number,
    authorization: string,
    signal?: AbortSignal
): Promise<AdminBranchPickupSettings> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/pickup-settings`,
            authorization,
            {
                method: "GET",
                signal
            }
        );


    if (!response.ok) {

        return throwApiError(
            response,
            "Unable to load pickup settings."
        );
    }


    return response.json();
}


export async function updateAdminBranchPickupSettings(
    branchId: number,
    request: UpdateAdminBranchPickupSettingsRequest,
    authorization: string
): Promise<AdminBranchPickupSettings> {

    const response =
        await adminFetch(
            `/api/admin/branches/${branchId}/pickup-settings`,
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


    if (!response.ok) {

        return throwApiError(
            response,
            "Unable to update pickup settings."
        );
    }


    return response.json();
}
